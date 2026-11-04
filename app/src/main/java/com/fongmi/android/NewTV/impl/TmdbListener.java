package com.fongmi.android.NewTV.impl;

/** 「设置 → TMDB 配置」里两项编辑的回调。 */
public interface TmdbListener {

    void setTmdbApi(String url);

    void setTmdbKey(String key);
}
