package com.fongmi.android.NewTV.ui.presenter;

import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.leanback.widget.Presenter;

import com.fongmi.android.NewTV.ui.custom.HomeBandView;

/**
 * 首页顶部留白带的占位 Presenter。
 *
 * <p>留白带本身没有内容，高度由 {@code HomeActivity} 在布局完成后实测写入，
 * 这里只负责把它作为「列表第一项」交给网格排版，从而获得随内容一起滚动的行为。</p>
 */
public class HomeBandPresenter extends Presenter {

    /** 网格可能预建多于一个视图（回收池），保持引用以便高度变化时统一刷新。 */
    private HomeBandView view;
    private int bandHeight;

    public int getBandHeight() {
        return bandHeight;
    }

    public void setBandHeight(int height) {
        height = Math.max(0, height);
        if (bandHeight == height) return;
        bandHeight = height;
        if (view != null) view.setBandHeight(height);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent) {
        HomeBandView item = new HomeBandView(parent.getContext());
        item.setBandHeight(bandHeight);
        view = item;
        return new ViewHolder(item);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder viewHolder, Object item) {
        if (viewHolder.view instanceof HomeBandView band) band.setBandHeight(bandHeight);
    }

    @Override
    public void onUnbindViewHolder(@NonNull ViewHolder viewHolder) {
    }
}
