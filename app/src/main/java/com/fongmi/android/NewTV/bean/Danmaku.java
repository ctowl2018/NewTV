package com.fongmi.android.NewTV.bean;

import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fongmi.android.NewTV.App;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import com.github.catvod.utils.UriUtil;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;

public class Danmaku {

    @SerializedName("name")
    private String name;
    @SerializedName("url")
    private String url;

    private transient boolean selected;
    private transient DanmakuTitle meta;
    /** 是否来自弹幕接口（{@link #arrayFrom} 解析出的结果），用于区分「片源自带弹幕」。 */
    private transient boolean remote;

    /**
     * 结构化标题（懒解析并缓存）：搜索列表要按分类 / 平台筛选并分行展示，
     * 一次搜索可能上千条，逐次正则代价不小，这里只在首次访问时解析一次。
     */
    public DanmakuTitle getMeta() {
        if (meta == null) meta = DanmakuTitle.parse(getName());
        return meta;
    }

    public static List<Danmaku> arrayFrom(String str) {
        Type listType = TypeToken.getParameterized(List.class, Danmaku.class).getType();
        List<Danmaku> items = App.gson().fromJson(str, listType);
        if (items == null) return Collections.emptyList();
        // 这条路径只被 DanmakuApi 用于解析接口响应，逐条打上「来自弹幕接口」的标记；
        // 片源自带的弹幕由 Danmaku.from(name, url) 构造，不会带这个标记。
        for (Danmaku item : items) item.remote = true;
        return items;
    }

    /** 是否来自弹幕接口（片源自带弹幕为 false）。 */
    public boolean isRemote() {
        return remote;
    }

    public static Danmaku from(String url) {
        return from(url, url);
    }

    public static Danmaku from(String name, String url) {
        Danmaku danmaku = new Danmaku();
        danmaku.name = name;
        danmaku.url = url;
        return danmaku;
    }

    public String getName() {
        return TextUtils.isEmpty(name) ? getUrl() : name;
    }

    public String getUrl() {
        return TextUtils.isEmpty(url) ? "" : url;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public boolean isEmpty() {
        return getUrl().isEmpty();
    }

    public Uri getUri() {
        return isEmpty() ? null : UriUtil.uri(getUrl());
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Danmaku it)) return false;
        return getUrl().equals(it.getUrl());
    }

    @Override
    public int hashCode() {
        return getUrl().hashCode();
    }

    @NonNull
    @Override
    public String toString() {
        return App.gson().toJson(this);
    }
}
