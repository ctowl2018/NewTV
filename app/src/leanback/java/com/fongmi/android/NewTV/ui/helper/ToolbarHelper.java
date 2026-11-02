package com.fongmi.android.NewTV.ui.helper;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 顶部品牌行通用交互辅助。
 *
 * <p>站点选择区域（logo 图标 + 「影视 Videos」字标）原本只聚焦在 logo 图标上，
 * 现改为一个可聚焦容器整体选中；选中时不显示选中背景，改用透明度 30%~100% 的慢闪动画作为提示。
 */
public class ToolbarHelper {

    /** 慢闪一圈（100% → 30% → 100%）的时长，偏慢以契合电视端提示节奏。 */
    private static final long BLINK_DURATION = 1600L;

    /** 每个 logo 容器正在运行的慢闪动画，失焦时据此 cancel（clearAnimation 只对旧 Animation 框架有效，对 ObjectAnimator 无效）。 */
    private static final Map<View, ObjectAnimator> sAnimators = new WeakHashMap<>();

    /** 为站点选择容器绑定慢闪：聚焦（选中）时 alpha 在 30%~100% 之间循环，失焦立即取消并恢复全显。 */
    public static void setupLogoBlink(View logo) {
        logo.setOnFocusChangeListener((view, focused) -> {
            // 先停掉可能仍在跑的旧动画，避免失焦后继续闪烁或叠加多个动画。
            ObjectAnimator old = sAnimators.remove(view);
            if (old != null) old.cancel();
            if (focused) {
                view.setAlpha(1f);
                ObjectAnimator animator = ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0.3f, 1f);
                animator.setDuration(BLINK_DURATION);
                animator.setRepeatCount(ValueAnimator.INFINITE);
                animator.setRepeatMode(ValueAnimator.RESTART);
                animator.setInterpolator(new LinearInterpolator());
                sAnimators.put(view, animator);
                animator.start();
            } else {
                view.setAlpha(1f);
            }
        });
    }
}
