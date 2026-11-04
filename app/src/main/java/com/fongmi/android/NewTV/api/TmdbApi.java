package com.fongmi.android.NewTV.api;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import com.fongmi.android.NewTV.App;
import com.fongmi.android.NewTV.bean.TmdbArt;
import com.fongmi.android.NewTV.setting.TmdbSetting;
import com.github.catvod.net.OkHttp;
import com.google.gson.annotations.SerializedName;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Response;

/**
 * TMDB 素材抓取：按剧名搜索 → 取背景图（w1280）与 logo（w500）→ 落到本地缓存并和影片别名关联。
 *
 * <p>接口形态（已在默认地址实测）：
 * <pre>
 * GET {base}/3/search/multi?api_key={key}&amp;language=zh-CN&amp;query={name}
 * GET {base}/3/{movie|tv}/{id}/images?api_key={key}&amp;include_image_language=zh,en,null
 * GET {base}/t/p/w1280/{file_path}   /   GET {base}/t/p/w500/{file_path}
 * </pre>
 * 所有网络与解码都在单线程后台执行，主线程只负责发起和读取缓存。
 * </p>
 *
 * <p>分季处理：TMDB 没有季级 backdrop，季条目往往 backdrops/logos 全为 0。
 * 因此先由 {@link TmdbKey} 展开「原名 → 母剧名 → 紧凑名」候选依次重试，
 * 再对每次搜索结果逐条尝试取图，遇到无图季条目自动回退到结果里的母剧条目。</p>
 */
public final class TmdbApi {

    public static final String BACKDROP_SIZE = "w1280";
    public static final String LOGO_SIZE = "w500";

    private static final String TAG = "TmdbApi";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "TmdbFetch");
        thread.setDaemon(true);
        return thread;
    });
    private static final Set<String> IN_FLIGHT = Collections.synchronizedSet(new HashSet<>());
    /** 网络异常后的短暂冷却（ms）：避免换集时反复重试，又不像「搜不到」那样压 24 小时。 */
    private static final Map<String, Long> RECENT_FAILURES = Collections.synchronizedMap(new HashMap<>());
    private static final long FAILURE_COOLDOWN = 60_000L;
    private static final int MAX_CANDIDATE_ITEMS = 4;

    /** 抓取完成后通知首页刷新背景；弱引用避免持有 Activity。 */
    private static volatile WeakReference<Runnable> ON_FETCHED;

    private TmdbApi() {
    }

    /** 注册 / 注销抓取完成回调。调用方需自行用字段强引用这个 Runnable。 */
    public static void setOnFetched(Runnable callback) {
        ON_FETCHED = callback == null ? null : new WeakReference<>(callback);
    }

    private static void notifyFetched() {
        App.post(() -> {
            WeakReference<Runnable> ref = ON_FETCHED;
            Runnable callback = ref == null ? null : ref.get();
            if (callback != null) callback.run();
        });
    }

    /** 清空缓存（设置页调用）：同时清掉冷却与进行中标记，后台任务靠 generation 丢弃旧结果。 */
    public static void clearCache() {
        RECENT_FAILURES.clear();
        IN_FLIGHT.clear();
        TmdbStore.clear();
    }

    /**
     * 触发一次抓取（异步、去重、失败自动回退）。
     *
     * @param keyword  优先关键字，通常是弹幕片名；首页焦点触发时就是海报名
     * @param fallback 备用关键字，通常是海报名称；可为空
     * @param aliases  需要关联到这次结果的其它名字（海报名、弹幕名等）；可为空
     */
    public static void fetch(String keyword, String fallback, List<String> aliases) {
        if (!TmdbSetting.isEnabled()) return;
        String primary = keyword == null ? "" : keyword.trim();
        String spare = fallback == null ? "" : fallback.trim();
        List<String> names = new ArrayList<>();
        if (aliases != null) for (String alias : aliases) if (alias != null && !alias.trim().isEmpty()) names.add(alias.trim());
        if (!primary.isEmpty()) names.add(primary);
        if (!spare.isEmpty()) names.add(spare);
        if (primary.isEmpty()) primary = spare;
        if (primary.isEmpty() && names.isEmpty()) return;

        // 分季片名展开成候选队列：原名搜不到就用母剧名、紧凑名继续试。
        List<String> queries = TmdbKey.candidates(names);
        if (queries.isEmpty()) return;

        // 任一候选已有可用背景图 → 只补别名，不打接口。
        // 不同季共用母剧 backdrop 是 TMDB 的既有行为，也是这里刻意复用的结果。
        for (String query : queries) {
            if (TmdbStore.cached(query)) {
                bindAliases(query, names);
                return;
            }
        }
        // 冷却期内的 miss 只压它自己那个关键字，其余候选照样尝试。
        List<String> pending = new ArrayList<>();
        for (String query : queries) if (!TmdbStore.missed(query)) pending.add(query);
        if (pending.isEmpty()) return;

        String token = TmdbStore.normalize(pending.get(0));
        if (token.isEmpty() || !IN_FLIGHT.add(token)) return;
        Long lastFailure = RECENT_FAILURES.get(token);
        if (lastFailure != null && System.currentTimeMillis() - lastFailure < FAILURE_COOLDOWN) {
            IN_FLIGHT.remove(token);
            return;
        }
        final long generation = TmdbStore.generation();
        final List<String> tasks = pending;
        EXECUTOR.execute(() -> {
            try {
                for (String query : tasks) {
                    TmdbArt art = search(query, generation);
                    if (TmdbStore.generation() != generation) return; // 期间被清理，丢弃结果
                    if (art == null) {
                        // miss 只绑当前关键字，绝不扩散别名：以前会把整部剧所有季压 24 小时。
                        TmdbArt miss = new TmdbArt();
                        miss.setKeyword(query);
                        miss.setTime(System.currentTimeMillis());
                        miss.addAliases(Collections.singletonList(query));
                        TmdbStore.save(miss);
                        continue;
                    }
                    art.setKeyword(query);
                    art.setTime(System.currentTimeMillis());
                    art.addAliases(names);
                    TmdbStore.save(art);
                    // 背景图与 logo 在同一个后台任务里预解码，两者淡入时不会一先一后。
                    TmdbStore.precache(art.getBackdrop());
                    TmdbStore.precacheLogo(art.getLogo());
                    RECENT_FAILURES.remove(token);
                    notifyFetched();
                    return;
                }
            } catch (Exception e) {
                Log.w(TAG, "fetch failed: " + e.getMessage());
                RECENT_FAILURES.put(token, System.currentTimeMillis());
            } finally {
                IN_FLIGHT.remove(token);
            }
        });
    }

    /** 配置变更后清掉失败冷却与「未命中」记录，让新地址 / 新密钥立刻生效。 */
    public static void onConfigChanged() {
        RECENT_FAILURES.clear();
        TmdbStore.clearMisses();
    }

    /** 已有缓存时补齐新别名（例如后来才拿到弹幕片名，或换了一季的海报）。 */
    private static void bindAliases(String hit, List<String> names) {
        TmdbArt art = TmdbStore.find(hit);
        if (art == null || !art.hasBackdrop()) return;
        int before = art.getAliases().size();
        art.addAliases(names);
        if (art.getAliases().size() != before) TmdbStore.save(art);
    }

    /** 搜索一个关键字；结果里逐条尝试取图，无图的季条目会自动让位给母剧条目。 */
    private static TmdbArt search(String keyword, long generation) throws Exception {
        String base = TmdbSetting.base();
        String key = TmdbSetting.getApiKey();
        String url = base + "/3/search/multi?api_key=" + Uri.encode(key)
                + "&language=zh-CN&page=1&query=" + Uri.encode(keyword);
        SearchResponse response = App.gson().fromJson(get(url), SearchResponse.class);
        if (response == null || response.results == null) return null;
        List<Result> items = new ArrayList<>();
        for (Result item : response.results) {
            if (item == null || item.id <= 0) continue;
            if (!"movie".equals(item.mediaType) && !"tv".equals(item.mediaType)) continue;
            items.add(item);
        }
        if (items.isEmpty()) return null;
        // 相关标题 + 有 backdrop 的排前面；仍然保留不相关条目兜底，避免因相似度判断漏掉母剧。
        Collections.sort(items, (a, b) -> Integer.compare(score(b, keyword), score(a, keyword)));
        int limit = Math.min(items.size(), MAX_CANDIDATE_ITEMS);
        for (int i = 0; i < limit; i++) {
            Result item = items.get(i);
            ImagesResponse images = loadImages(base, key, item);
            String backdrop = TextUtils.isEmpty(item.backdropPath) ? firstBackdrop(images) : item.backdropPath;
            if (TextUtils.isEmpty(backdrop)) continue; // 季条目无图 → 试下一条（母剧）
            try {
                byte[] bytes = download(base + "/t/p/" + BACKDROP_SIZE + backdrop);
                if (TmdbStore.generation() != generation) return null; // 下载期间缓存被清空，不再落盘
                File file = TmdbStore.imageFile(BACKDROP_SIZE, backdrop);
                TmdbStore.write(file, bytes);
                TmdbArt art = new TmdbArt();
                art.setKeyword(keyword);
                art.setTitle(TextUtils.isEmpty(item.title) ? nullToEmpty(item.name) : item.title);
                art.setType(item.mediaType);
                art.setId(item.id);
                art.setRootId(item.id);
                art.setSeason(TmdbKey.season(keyword));
                art.setBackdrop(file.getName());
                String logo = pickLogo(images);
                if (!TextUtils.isEmpty(logo)) {
                    try {
                        byte[] logoBytes = download(base + "/t/p/" + LOGO_SIZE + logo);
                        File logoFile = TmdbStore.imageFile(LOGO_SIZE, logo);
                        TmdbStore.write(logoFile, logoBytes);
                        art.setLogo(logoFile.getName());
                    } catch (Exception ignored) {
                        // logo 缺失时背景图仍然可用。
                    }
                }
                return art;
            } catch (Exception e) {
                Log.w(TAG, "backdrop failed #" + item.id + ": " + e.getMessage());
                // 图片下载失败不该终止整次抓取，继续试下一条结果。
            }
        }
        return null;
    }

    /** 相关性评分（只按中文/本地化标题匹配）：含母剧名 > 含原关键字；有 backdrop、精确同名再加分。 */
    private static int score(Result item, String keyword) {
        String root = TmdbStore.normalize(TmdbKey.root(keyword));
        String query = TmdbStore.normalize(keyword);
        String title = TmdbStore.normalize(TextUtils.isEmpty(item.title) ? nullToEmpty(item.name) : item.title);
        int value = 0;
        if (!root.isEmpty() && !title.isEmpty() && (title.contains(root) || root.contains(title))) value += 4;
        else if (!query.isEmpty() && !title.isEmpty() && (title.contains(query) || query.contains(title))) value += 2;
        if (!TextUtils.isEmpty(item.backdropPath)) value += 2;
        if (!title.isEmpty() && title.equals(query)) value += 1;
        return value;
    }

    private static ImagesResponse loadImages(String base, String key, Result item) {
        String url = base + "/3/" + item.mediaType + "/" + item.id + "/images?api_key="
                + Uri.encode(key) + "&include_image_language=zh,en,null";
        try {
            return App.gson().fromJson(get(url), ImagesResponse.class);
        } catch (Exception e) {
            return null;
        }
    }

    private static String pickLogo(ImagesResponse images) {
        if (images == null || images.logos == null) return "";
        for (Image item : images.logos) {
            if (item != null && !TextUtils.isEmpty(item.filePath) && "zh".equals(item.iso6391)) return item.filePath;
        }
        for (Image item : images.logos) {
            if (item != null && !TextUtils.isEmpty(item.filePath) && TextUtils.isEmpty(item.iso6391)) return item.filePath;
        }
        for (Image item : images.logos) {
            if (item != null && !TextUtils.isEmpty(item.filePath)) return item.filePath;
        }
        return "";
    }

    private static String firstBackdrop(ImagesResponse images) {
        if (images == null || images.backdrops == null) return "";
        for (Image item : images.backdrops) if (item != null && !TextUtils.isEmpty(item.filePath)) return item.filePath;
        return "";
    }

    private static String get(String url) throws IOException {
        try (Response response = OkHttp.newCall(url, TAG).execute()) {
            if (!response.isSuccessful() || response.body() == null) throw new IOException("HTTP " + response.code());
            return response.body().string();
        }
    }

    private static byte[] download(String url) throws IOException {
        try (Response response = OkHttp.newCall(url, TAG).execute()) {
            if (!response.isSuccessful() || response.body() == null) throw new IOException("HTTP " + response.code());
            byte[] bytes = response.body().bytes();
            if (bytes.length == 0) throw new IOException("empty image");
            return bytes;
        }
    }

    private static String nullToEmpty(String text) {
        return text == null ? "" : text;
    }

    private static class SearchResponse {
        @SerializedName("results")
        List<Result> results;
    }

    private static class Result {
        @SerializedName("id")
        int id;
        @SerializedName("media_type")
        String mediaType;
        @SerializedName("title")
        String title;
        @SerializedName("name")
        String name;
        @SerializedName("backdrop_path")
        String backdropPath;
    }

    private static class ImagesResponse {
        @SerializedName("backdrops")
        List<Image> backdrops;
        @SerializedName("logos")
        List<Image> logos;
    }

    private static class Image {
        @SerializedName("file_path")
        String filePath;
        @SerializedName("iso_639_1")
        String iso6391;
    }
}
