package com.fongmi.android.NewTV.setting;

import com.fongmi.android.NewTV.R;
import com.fongmi.android.NewTV.utils.ResUtil;
import com.github.catvod.utils.Prefers;

public class Setting {

    /** 热词榜最小刷新间隔：15 分钟。榜单是低频数据，没必要每次进搜索页都打三次请求。 */
    private static final long HOT_TTL_MS = 15 * 60 * 1000L;

    private static final int MIN_SITE_MODE = 0;
    private static final int MAX_SITE_MODE = 1;
    private static final int MIN_SYNC_MODE = 0;
    private static final int MAX_SYNC_MODE = 2;

    public static String getSwitch(boolean value) {
        return ResUtil.getString(value ? R.string.setting_on : R.string.setting_off);
    }

    /**
     * 一次性把新版默认值写入已有安装：老版本持久化过 danmaku_load/size/danmaku_api_url，
     * 只改代码里的默认值对升级用户不生效，这里统一覆盖一次。
     */
    public static void migrateDefaults() {
        try {
            if (!Prefers.getBoolean("defaults_v2")) {
                Prefers.put("danmaku_load", true);
                Prefers.put("danmaku_api_url", DanmakuSetting.DEFAULT_API_URL);
                Prefers.put("size", 1);
                Prefers.put("defaults_v2", true);
            }
            if (!Prefers.getBoolean("defaults_v3")) {
                // v3 默认值：弹幕加载 / 进度条热力图 / TMDB 刮削改为默认关闭；
                // 「联网搜索建议」入口已从设置页移除，固定开启。
                Prefers.put("search_suggest", true);
                DanmakuSetting.putLoad(false);
                DanmakuSetting.putHeatmap(false);
                TmdbSetting.putEnabled(false);
                Prefers.put("defaults_v3", true);
            }
            if (!Prefers.getBoolean("defaults_v4")) {
                // v4 默认值：TMDB「刮削失败提示」改为默认关闭。
                // 单独开一档而不是并进 v3：已经跑过 v3 的安装也要覆盖到，且只覆盖一次。
                TmdbSetting.putErrorToast(false);
                Prefers.put("defaults_v4", true);
            }
        } catch (Exception ignored) {
            // 启动阶段 Prefers 尚未就绪时忽略，下次启动再迁移。
        }
    }

    /** 热词数据源改为「腾讯优先 + 360kan」后，清空一次节流时间，保证升级后首次进入就拉取新数据。 */
    public static void migrateHotSource() {
        try {
            if (Prefers.getBoolean("hot_source_v2")) return;
            Prefers.put("hot_time", 0L);
            Prefers.put("hot_source_v2", true);
        } catch (Exception ignored) {
            // 启动阶段 Prefers 尚未就绪时忽略，下次启动再迁移。
        }
    }

    public static String getDoh() {
        return Prefers.getString("doh");
    }

    public static void putDoh(String doh) {
        Prefers.put("doh", doh);
    }

    public static String getUa() {
        return Prefers.getString("ua");
    }

    public static void putUa(String ua) {
        Prefers.put("ua", ua);
    }

    public static String getKeyword() {
        return Prefers.getString("keyword");
    }

    public static void putKeyword(String keyword) {
        Prefers.put("keyword", keyword);
    }

    public static String getHotMovie() {
        return Prefers.getString("hot_movie");
    }

    public static void putHotMovie(String hot) {
        Prefers.put("hot_movie", hot);
    }

    public static String getHotTv() {
        return Prefers.getString("hot_tv");
    }

    public static void putHotTv(String hot) {
        Prefers.put("hot_tv", hot);
    }

    public static String getHotVariety() {
        return Prefers.getString("hot_variety");
    }

    public static void putHotVariety(String hot) {
        Prefers.put("hot_variety", hot);
    }

    /** 上次成功拉取热词的时间，用于节流。 */
    public static boolean isHotExpired() {
        return System.currentTimeMillis() - Prefers.getLong("hot_time") > HOT_TTL_MS;
    }

    public static void putHotTime() {
        Prefers.put("hot_time", System.currentTimeMillis());
    }

    /**
     * 联网搜索建议。关闭后输入关键词不再向第三方索取联想词，
     * 避免把用户逐字输入的内容明文发给爱奇艺/腾讯。
     */
    public static boolean isSearchSuggest() {
        return Prefers.getBoolean("search_suggest", true);
    }

    public static void putSearchSuggest(boolean suggest) {
        Prefers.put("search_suggest", suggest);
    }

    public static int getSiteMode() {
        return Math.clamp(Prefers.getInt("site_mode"), MIN_SITE_MODE, MAX_SITE_MODE);
    }

    public static void putSiteMode(int mode) {
        Prefers.put("site_mode", Math.clamp(mode, MIN_SITE_MODE, MAX_SITE_MODE));
    }

    public static int getSyncMode() {
        return Math.clamp(Prefers.getInt("sync_mode"), MIN_SYNC_MODE, MAX_SYNC_MODE);
    }

    public static void putSyncMode(int mode) {
        Prefers.put("sync_mode", Math.clamp(mode, MIN_SYNC_MODE, MAX_SYNC_MODE));
    }

    public static boolean isIncognito() {
        return Prefers.getBoolean("incognito");
    }

    public static void putIncognito(boolean incognito) {
        Prefers.put("incognito", incognito);
    }

    public static boolean getUpdate() {
        return Prefers.getBoolean("update", true);
    }

    public static void putUpdate(boolean update) {
        Prefers.put("update", update);
    }

    public static boolean isAdblock() {
        return Prefers.getBoolean("adblock", true);
    }

    public static void putAdblock(boolean adblock) {
        Prefers.put("adblock", adblock);
    }

    public static boolean isZhuyin() {
        return Prefers.getBoolean("zhuyin");
    }

    public static void putZhuyin(boolean zhuyin) {
        Prefers.put("zhuyin", zhuyin);
    }
}
