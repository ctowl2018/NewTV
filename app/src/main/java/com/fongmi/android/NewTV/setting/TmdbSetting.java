package com.fongmi.android.NewTV.setting;

import android.text.TextUtils;

import com.fongmi.android.NewTV.api.TmdbApi;
import com.github.catvod.utils.Prefers;

/** TMDB 接口的自定义配置。默认值可直接用，用户可在「设置 → TMDB 配置」里覆盖。 */
public final class TmdbSetting {

    public static final String DEFAULT_API_URL = "https://***REMOVED***";
    public static final String DEFAULT_API_KEY = "***REMOVED***";

    private TmdbSetting() {
    }

    public static String getApiUrl() {
        String url = Prefers.getString("tmdb_api_url", DEFAULT_API_URL);
        return TextUtils.isEmpty(url) ? DEFAULT_API_URL : url.trim();
    }

    public static void putApiUrl(String url) {
        String value = url == null ? "" : url.trim();
        if (TextUtils.equals(value, Prefers.getString("tmdb_api_url", DEFAULT_API_URL))) return;
        Prefers.put("tmdb_api_url", value);
        TmdbApi.onConfigChanged();
    }

    public static String getApiKey() {
        String key = Prefers.getString("tmdb_api_key", DEFAULT_API_KEY);
        return TextUtils.isEmpty(key) ? DEFAULT_API_KEY : key.trim();
    }

    public static void putApiKey(String key) {
        String value = key == null ? "" : key.trim();
        if (TextUtils.equals(value, Prefers.getString("tmdb_api_key", DEFAULT_API_KEY))) return;
        Prefers.put("tmdb_api_key", value);
        TmdbApi.onConfigChanged();
    }

    /** 总开关：关闭后播放页与首页海报聚焦都不再抓取，已缓存的图片仍可显示。 */
    public static boolean isEnabled() {
        return Prefers.getBoolean("tmdb_enable", true);
    }

    public static void putEnabled(boolean value) {
        Prefers.put("tmdb_enable", value);
    }

    /** 子开关：最近观看海报获得焦点时是否用海报名补抓一次。 */
    public static boolean isFocusFetch() {
        return Prefers.getBoolean("tmdb_focus_fetch", true);
    }

    public static void putFocusFetch(boolean value) {
        Prefers.put("tmdb_focus_fetch", value);
    }

    /** 去掉末尾斜杠，便于拼接 /3/... 与 /t/p/...。 */
    public static String base() {
        String url = getApiUrl();
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        return url;
    }
}
