package com.fongmi.android.NewTV.utils;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.fongmi.android.NewTV.App;
import com.fongmi.android.NewTV.bean.Word;
import com.github.catvod.net.OkHttp;
import com.google.common.net.HttpHeaders;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.Call;
import okhttp3.Response;

/**
 * 搜索页热词数据源：腾讯热搜榜（每类 5 条）+ 360kan 榜单（补充）。
 *
 * <p>腾讯接口为公开 GET（无需签名），按频道返回热搜词与热度；360kan 为原有数据源。
 * 合并规则：同类目内腾讯取前 5 条，与 360kan 按作品名称去重后交替排名
 * （腾讯、360kan 依次各取一条：T1、K1、T2、K2 …），重复时保留首次出现者。
 * 输出格式沿用 {@code {"data":[{"title":"..."}]}}，与搜索页原有结构一致。
 */
public class HotRank {

    public static final int MOVIE = 2;
    public static final int TV = 3;
    public static final int VARIETY = 4;

    private static final String TENCENT_URL = "https://pbaccess.video.qq.com/trpc.tencentvideohotlistdata.hotlistdata.DoSearchHotListHttp/getSearchHotListHttp";
    private static final String TENCENT_REFERER = "https://v.qq.com/ranks/pages/index.html";
    private static final String KAN_URL = "https://api.web.360kan.com/v1/rank?cat=";
    private static final String KAN_REFERER = "https://www.360kan.com/rank/general";
    /** 腾讯每个分类最多取多少条。 */
    private static final int TENCENT_LIMIT = 5;

    public interface Callback {

        void onResult(String json);
    }

    private interface Body {

        void onBody(String body);
    }

    /** 腾讯频道号：电影 1 / 电视剧 2 / 综艺 10。 */
    private static int channel(int cat) {
        if (cat == MOVIE) return 1;
        if (cat == TV) return 2;
        return 10;
    }

    private static String tencentUrl(int cat) {
        return TENCENT_URL + "?ChannelID=" + channel(cat) + "&PageSize=" + TENCENT_LIMIT + "&PageNum=0&SceneID=2";
    }

    public static void fetch(int cat, Callback callback) {
        final String[] tencent = {null};
        final String[] kan = {null};
        final AtomicInteger left = new AtomicInteger(2);
        Runnable done = () -> {
            if (left.decrementAndGet() != 0) return;
            callback.onResult(merge(tencent[0], kan[0]));
        };
        get(tencentUrl(cat), TENCENT_REFERER, body -> tencent[0] = body, done);
        get(KAN_URL + cat, KAN_REFERER, body -> kan[0] = body, done);
    }

    private static void get(String url, String referer, Body body, Runnable done) {
        Map<String, String> headers = Collections.singletonMap(HttpHeaders.REFERER, referer);
        OkHttp.newCall(url, headers).enqueue(new okhttp3.Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                String text = null;
                try {
                    text = response.body() != null ? response.body().string() : null;
                } catch (IOException ignored) {
                }
                if (!TextUtils.isEmpty(text)) body.onBody(text);
                done.run();
            }

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                done.run();
            }
        });
    }

    private static String merge(String tencentJson, String kanJson) {
        List<String> tencent = parseTencent(tencentJson);
        List<String> kan = parseKan(kanJson);
        // 交替排名：腾讯、360kan 依次各取一条（T1、K1、T2、K2 …），以作品名称为依据去重，
        // 重复时保留首次出现者（即排名更靠前者）。
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<String> merged = new ArrayList<>();
        int ti = 0, ki = 0;
        while (ti < tencent.size() || ki < kan.size()) {
            if (ti < tencent.size()) add(merged, seen, tencent.get(ti++));
            if (ki < kan.size()) add(merged, seen, kan.get(ki++));
        }
        // 两路都失败时不返回空数组，避免把已有缓存覆盖成空。
        if (merged.isEmpty()) return null;
        JsonArray array = new JsonArray();
        for (String title : merged) {
            JsonObject item = new JsonObject();
            item.addProperty("title", title);
            array.add(item);
        }
        JsonObject root = new JsonObject();
        root.add("data", array);
        return root.toString();
    }

    private static void add(List<String> out, LinkedHashSet<String> seen, String title) {
        if (TextUtils.isEmpty(title)) return;
        // 与展示层一致地做一次简繁转换后再去重，避免两种写法被当成两个作品。
        String key = Word.Data.create(title.trim()).getTitle();
        if (TextUtils.isEmpty(key) || !seen.add(key)) return;
        out.add(key);
    }

    private static List<String> parseTencent(String json) {
        List<String> titles = new ArrayList<>();
        if (TextUtils.isEmpty(json)) return titles;
        try {
            TencentResp resp = App.gson().fromJson(json, TencentResp.class);
            if (resp == null || resp.data == null || resp.data.results == null) return titles;
            for (Result result : resp.data.results) {
                if (result == null || result.result == null || result.result.list == null) continue;
                for (Item item : result.result.list) {
                    if (item == null || TextUtils.isEmpty(item.query)) continue;
                    String key = Word.Data.create(item.query.trim()).getTitle();
                    if (!TextUtils.isEmpty(key) && !titles.contains(key)) titles.add(key);
                    if (titles.size() >= TENCENT_LIMIT) return titles;
                }
            }
        } catch (Exception ignored) {
        }
        return titles;
    }

    private static List<String> parseKan(String json) {
        List<String> titles = new ArrayList<>();
        if (TextUtils.isEmpty(json)) return titles;
        try {
            for (Word.Data data : Word.objectFrom(json).getData()) {
                String key = Word.Data.create(data.getTitle().trim()).getTitle();
                if (!TextUtils.isEmpty(key) && !titles.contains(key)) titles.add(key);
            }
        } catch (Exception ignored) {
        }
        return titles;
    }

    private static class TencentResp {

        @SerializedName("data")
        private TencentData data;
    }

    private static class TencentData {

        @SerializedName("Results")
        private List<Result> results;
    }

    private static class Result {

        @SerializedName("Result")
        private ListInfo result;
    }

    private static class ListInfo {

        @SerializedName("ListInfo")
        private List<Item> list;
    }

    private static class Item {

        @SerializedName("Query")
        private String query;
    }
}
