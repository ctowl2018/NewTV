package com.fongmi.android.NewTV.utils;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.fongmi.android.NewTV.App;
import com.fongmi.android.NewTV.bean.Word;
import com.github.catvod.net.OkHttp;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Response;

/**
 * 搜索页热词数据源：自建榜单接口 {@code /rank/***REMOVED***?format=json}。
 *
 * <p>接口一次返回优酷 / 爱奇艺 / 腾讯视频 / 芒果TV 四个平台的电影、电视剧、综艺榜单，
 * 单次请求即可拿到全部类目，按 {@code category} 抽出当前类目后合并。
 *
 * <p>合并规则沿用旧版的「交替排名」，只是来源从 2 个扩到 4 个：
 * 按来源优先级轮转取词 —— 电影 / 电视剧 依次 爱奇艺 → 腾讯视频 → 芒果TV → 优酷，
 * 综艺 依次 芒果TV → 爱奇艺 → 腾讯视频 → 优酷；每轮各来源取 1 条，循环到凑满 {@link #LIMIT} 条。
 * 同一作品在多个来源出现时只保留排位靠前的那条（先被取到者），并按作品名去重。
 *
 * <p>回退：某来源 {@code error} 非空、{@code items} 为空或已取尽时，直接跳过并由下一优先级补位；
 * 全部来源都无数据时返回 {@code null}，调用方 {@code requestRank} 会忽略，不会覆盖已有缓存。
 *
 * <p>输出格式沿用 {@code {"data":[{"title":"..."}]}}，与搜索页原有结构一致。
 */
public class HotRank {

    public static final int MOVIE = 2;
    public static final int TV = 3;
    public static final int VARIETY = 4;

    private static final String RANK_URL = "http://***REMOVED***/rank/***REMOVED***?format=json";
    /** 每个类目最多输出多少条，与搜索页的 HOT_LIMIT 对齐。 */
    private static final int LIMIT = 10;

    /** 电影 / 电视剧的来源优先级；优酷排在最后兜底。 */
    private static final String[] PRIORITY_VIDEO = {"爱奇艺", "腾讯视频", "芒果TV", "优酷"};
    /** 综艺的来源优先级；优酷排在最后兜底。 */
    private static final String[] PRIORITY_VARIETY = {"芒果TV", "爱奇艺", "腾讯视频", "优酷"};

    /** 去重时忽略的修饰符：空白、常见标点、间隔号。 */
    private static final Pattern NOISE = Pattern.compile("[\\s·・•\\-—–~～!！?？:：;；,，、。.()（）\\[\\]【】《》<>\\u2018\\u2019\\u201C\\u201D]");
    /** 去重时忽略的版本后缀，如「空枪 粤语版」「知否 TV版」。 */
    private static final Pattern VERSION = Pattern.compile("(?:粤语|国语|潮汕话|普通话|英语|日语|韩语|原声|配音|TV|完整|加长|精编|番外)版$", Pattern.CASE_INSENSITIVE);

    public interface Callback {

        void onResult(String json);
    }

    private static String[] priority(int cat) {
        return cat == VARIETY ? PRIORITY_VARIETY : PRIORITY_VIDEO;
    }

    private static String category(int cat) {
        if (cat == MOVIE) return "电影";
        if (cat == TV) return "电视剧";
        return "综艺";
    }

    public static void fetch(int cat, Callback callback) {
        OkHttp.newCall(RANK_URL).enqueue(new okhttp3.Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                String text = null;
                try {
                    text = response.body() != null ? response.body().string() : null;
                } catch (IOException ignored) {
                }
                callback.onResult(merge(text, cat));
            }

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                callback.onResult(null);
            }
        });
    }

    private static String merge(String json, int cat) {
        RankResponse resp = parse(json);
        if (resp == null || resp.lists == null || resp.lists.isEmpty()) return null;
        String category = category(cat);
        Map<String, List<String>> bySite = new LinkedHashMap<>();
        for (RankList list : resp.lists) {
            if (list == null || !category.equals(list.category)) continue;
            // 该平台抓取失败（error 非空）或没取到条目时直接跳过，由下一优先级补位。
            if (!TextUtils.isEmpty(list.error) || list.items == null || list.items.isEmpty()) continue;
            List<String> titles = bySite.get(list.site);
            if (titles == null) bySite.put(list.site, titles = new ArrayList<>());
            for (RankItem item : list.items) {
                if (item != null && !TextUtils.isEmpty(item.title)) titles.add(item.title.trim());
            }
        }
        if (bySite.isEmpty()) return null;
        // 按优先级排好取词队列；接口里出现但优先级表未列出的来源追加到末尾，避免丢数据。
        List<List<String>> queues = new ArrayList<>();
        for (String site : priority(cat)) {
            List<String> titles = bySite.remove(site);
            if (titles != null) queues.add(titles);
        }
        queues.addAll(bySite.values());
        // 轮转取词：A1、T1、M1、A2、T2、M2 …，按作品名去重后最多取 LIMIT 条。
        Map<String, Integer> index = new HashMap<>();
        List<String> merged = new ArrayList<>();
        int[] cursor = new int[queues.size()];
        while (merged.size() < LIMIT) {
            boolean moved = false;
            for (int i = 0; i < queues.size() && merged.size() < LIMIT; i++) {
                List<String> queue = queues.get(i);
                while (cursor[i] < queue.size()) {
                    moved = true;
                    if (add(merged, index, queue.get(cursor[i]++))) break;
                }
            }
            if (!moved) break;
        }
        // 所有来源都没取到词时返回 null，避免把已有缓存覆盖成空。
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

    private static boolean add(List<String> out, Map<String, Integer> index, String raw) {
        // 与展示层一致地做一次简繁转换后再去重，避免两种写法被当成两个作品。
        String title = Word.Data.create(raw).getTitle();
        if (TextUtils.isEmpty(title)) return false;
        String key = normalize(title);
        if (TextUtils.isEmpty(key)) return false;
        Integer at = index.get(key);
        if (at != null) {
            // 同一作品重复出现：排位保持不变，但若这次的写法更干净（少了「粤语版」「第 3 季」等修饰）就换掉文案。
            if (title.length() < out.get(at).length()) out.set(at, title);
            return false;
        }
        index.put(key, out.size());
        out.add(title);
        return true;
    }

    private static String normalize(String title) {
        String key = NOISE.matcher(title).replaceAll("");
        key = VERSION.matcher(key).replaceAll("");
        return key.toLowerCase(Locale.ROOT);
    }

    private static RankResponse parse(String json) {
        if (TextUtils.isEmpty(json)) return null;
        try {
            return App.gson().fromJson(json, RankResponse.class);
        } catch (Exception e) {
            return null;
        }
    }

    private static class RankResponse {

        @SerializedName("lists")
        private List<RankList> lists;
    }

    private static class RankList {

        @SerializedName("site")
        private String site;
        @SerializedName("category")
        private String category;
        @SerializedName("error")
        private String error;
        @SerializedName("items")
        private List<RankItem> items;
    }

    private static class RankItem {

        @SerializedName("title")
        private String title;
    }
}
