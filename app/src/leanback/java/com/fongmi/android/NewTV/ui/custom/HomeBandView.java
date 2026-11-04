package com.fongmi.android.NewTV.ui.custom;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * 首页顶部「留白带」：一段透明的占位高度，把「最近观看」的**海报顶部**顶到背景图下沿
 * 之上 {@code HERO_POSTER_OVERLAP_PX} 处（板块标题仍按原有行距排在海报上方）。
 *
 * <p>它不是一个固定在屏幕上的空白区域，而是一个正常的列表项 —— 位置与高度都由网格排版，
 * 所以内容上翻时会跟着列表一起滚出屏幕，下面的海报行紧接着补上来，顶部不会残留一条静止的空白。
 * 这与「给网格加 paddingTop」「给容器加 topMargin」有本质区别：那两种做法只是把内容起点往下推，
 * 视口上边界仍停在原处，滚动时海报会被这条边界裁掉，上面永远空着。</p>
 *
 * <p>高度在布局完成后由 {@code HomeActivity} 按背景图实测写入
 * （背景图底边 − 海报重合量 − 标题↔海报高度差 − 网格起点），
 * 因此换屏、换图、换字体缩放都自动对齐。自身不绘制、不参与焦点。</p>
 */
public class HomeBandView extends View {

    private int bandHeight;

    public HomeBandView(Context context) {
        this(context, null);
    }

    public HomeBandView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setFocusable(false);
        setFocusableInTouchMode(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public int getBandHeight() {
        return bandHeight;
    }

    /** 设置占位高度（px）；0 表示不占位（尺寸尚未测出时）。 */
    public void setBandHeight(int height) {
        height = Math.max(0, height);
        if (bandHeight == height) return;
        bandHeight = height;
        requestLayout();
    }

    /**
     * 高度只由 {@link #setBandHeight(int)} 决定，忽略 LayoutParams 与父容器的测量建议：
     * 网格会给子项下发 AT_MOST/EXACTLY 的规格，普通 View 在这些规格下会被拉满整屏，
     * 那就不是「留白带」而是「整屏空白」了。
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), bandHeight);
    }
}
