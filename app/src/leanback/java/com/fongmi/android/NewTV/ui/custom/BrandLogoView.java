package com.fongmi.android.NewTV.ui.custom;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.appcompat.widget.AppCompatImageView;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.core.content.ContextCompat;

import com.fongmi.android.NewTV.R;

/**
 * 顶部品牌组件：站点 logo 与「影视 Videos」字标合并为一个不可拆分的整体。
 *
 * <p>首页、分类页、搜索页、搜索详情页、播放详情页、收藏页统一复用本组件，
 * 保证六个页面顶部的图标尺寸、字标比例、间距与垂直对齐完全一致。
 *
 * <p><b>锁定保护</b>：
 * <ul>
 *   <li>两个子视图是私有字段、不对外暴露 id，外部拿不到可改写的句柄；</li>
 *   <li>构造完成后 {@link #addView(View, int, ViewGroup.LayoutParams)} 拒绝任何外部新增子视图，
 *       结构不可再变；</li>
 *   <li>每次布局都会调用 {@link #enforceLocked()}，把图标 / 字标的 drawable、尺寸、缩放方式、
 *       可见性、透明度强制校正回固定值。</li>
 * </ul>
 * 因此站点数据源（例如旧实现里 {@code ImgUtil.logo()} 按配置加载远程 logo）无法在运行时
 * 修改或覆盖本组件，DOM 结构与样式始终保持不变。
 */
public final class BrandLogoView extends LinearLayoutCompat {

    /** 图标边长（与原顶栏一致）。 */
    private static final int ICON_SIZE_DP = 36;
    /** 字标高度（与原顶栏一致，宽度按图片比例自适应）。 */
    private static final int WORD_HEIGHT_DP = 17;
    /** 图标与字标之间的间距。 */
    private static final int WORD_GAP_DP = 8;

    private final AppCompatImageView icon;
    private final AppCompatImageView word;
    private final Drawable iconDrawable;
    private final Drawable wordDrawable;
    private final int iconSizePx;
    private final int wordHeightPx;
    private final int wordGapPx;

    /** 构造完成后置 true：此后任何外部 addView 都被忽略，保证结构不可变。 */
    private boolean sealed;

    public BrandLogoView(Context context) {
        this(context, null);
    }

    public BrandLogoView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BrandLogoView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);

        iconSizePx = dp(ICON_SIZE_DP);
        wordHeightPx = dp(WORD_HEIGHT_DP);
        wordGapPx = dp(WORD_GAP_DP);
        iconDrawable = ContextCompat.getDrawable(context, R.drawable.ic_logo);
        wordDrawable = ContextCompat.getDrawable(context, R.drawable.logo_word);

        icon = new AppCompatImageView(context);
        icon.setContentDescription(context.getString(R.string.app_name));
        icon.setFocusable(false);
        icon.setFocusableInTouchMode(false);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setImageDrawable(iconDrawable);
        icon.setLayoutParams(new LayoutParams(iconSizePx, iconSizePx));
        addView(icon);

        word = new AppCompatImageView(context);
        word.setAdjustViewBounds(true);
        word.setFocusable(false);
        word.setFocusableInTouchMode(false);
        word.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        word.setScaleType(ImageView.ScaleType.FIT_CENTER);
        word.setImageDrawable(wordDrawable);
        LayoutParams wordParams = new LayoutParams(LayoutParams.WRAP_CONTENT, wordHeightPx);
        wordParams.setMarginStart(wordGapPx);
        word.setLayoutParams(wordParams);
        addView(word);

        sealed = true;
    }

    /**
     * 把组件重新校正回固定外观。
     *
     * <p>供「站点切换 / 配置刷新」等事件调用：即使外部曾尝试写入其它 logo，
     * 这里也会立即恢复为内置图标与字标。
     */
    public void refreshLocked() {
        enforceLocked();
        invalidate();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        // 每次布局都校验一次，防止运行期被数据源改写后不再恢复
        enforceLocked();
    }

    /** 结构锁定：构造完成后拒绝任何外部新增子视图。 */
    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        if (!sealed) super.addView(child, index, params);
    }

    private void enforceLocked() {
        lockIcon();
        lockWord();
    }

    private void lockIcon() {
        if (icon.getDrawable() != iconDrawable) icon.setImageDrawable(iconDrawable);
        if (icon.getScaleType() != ImageView.ScaleType.FIT_CENTER) icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (icon.getVisibility() != VISIBLE) icon.setVisibility(VISIBLE);
        if (icon.getAlpha() != 1f) icon.setAlpha(1f);
        ViewGroup.LayoutParams params = icon.getLayoutParams();
        if (params != null && (params.width != iconSizePx || params.height != iconSizePx)) {
            params.width = iconSizePx;
            params.height = iconSizePx;
            icon.setLayoutParams(params);
        }
    }

    private void lockWord() {
        if (word.getDrawable() != wordDrawable) word.setImageDrawable(wordDrawable);
        if (word.getScaleType() != ImageView.ScaleType.FIT_CENTER) word.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (!word.getAdjustViewBounds()) word.setAdjustViewBounds(true);
        if (word.getVisibility() != VISIBLE) word.setVisibility(VISIBLE);
        if (word.getAlpha() != 1f) word.setAlpha(1f);
        if (word.getImportantForAccessibility() != IMPORTANT_FOR_ACCESSIBILITY_NO) {
            word.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        ViewGroup.LayoutParams params = word.getLayoutParams();
        if (params instanceof ViewGroup.MarginLayoutParams margins) {
            if (params.height != wordHeightPx || margins.getMarginStart() != wordGapPx) {
                params.height = wordHeightPx;
                margins.setMarginStart(wordGapPx);
                word.setLayoutParams(params);
            }
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
