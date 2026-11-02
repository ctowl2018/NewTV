package com.fongmi.android.NewTV.ui.custom;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.animation.Animation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fongmi.android.NewTV.App;
import com.fongmi.android.NewTV.R;
import com.fongmi.android.NewTV.api.config.VodConfig;
import com.fongmi.android.NewTV.impl.SiteListener;
import com.fongmi.android.NewTV.utils.KeyUtil;
import com.fongmi.android.NewTV.utils.ResUtil;
import com.google.android.material.textview.MaterialTextView;

/**
 * 顶部站点名称。
 *
 * <p>已取消「左右键切换站点」：左右键恢复为正常的焦点走位，
 * 站点切换改用搜索/收藏旁的「连接」入口与设置页，避免抢走导航栏的横向移动。
 * 仅保留上键刷新当前源首页内容。
 */
public class CustomTitleView extends MaterialTextView {

    private Listener listener;
    private Animation flicker;
    private boolean coolDown;

    public CustomTitleView(@NonNull Context context) {
        super(context);
    }

    public CustomTitleView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        flicker = ResUtil.getAnim(R.anim.flicker);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
        setOnClickListener(v -> listener.showDialog());
    }

    private boolean hasEvent(KeyEvent event) {
        return !VodConfig.get().getHome().isEmpty() && KeyUtil.isUpKey(event) && !coolDown;
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
        if (focused) startAnimation(flicker);
        else clearAnimation();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (!hasEvent(event)) return super.dispatchKeyEvent(event);
        onKeyUp();
        return true;
    }

    private void onKeyUp() {
        App.post(() -> coolDown = false, 3000);
        listener.onRefresh();
        coolDown = true;
    }

    public interface Listener extends SiteListener {

        void showDialog();

        void onRefresh();
    }
}
