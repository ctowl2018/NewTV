package com.fongmi.android.NewTV.ui.custom;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.widget.ImageView;

import androidx.annotation.Nullable;

/**
 * 首页右上角背景图与 TMDB logo 共用的交叉淡入实现。
 *
 * <p>两处素材必须严格同步：同一时长、同一插值行为（TransitionDrawable 的线性 alpha 交叉）、
 * 同一个入口。任何一方单独写 startTransition 都会出现「背景已经换完 logo 才慢慢浮出来」的
 * 割裂感，因此这里统一收口。</p>
 *
 * <p>连续切换时以「当前过渡动画的最后一层」为起点，避免把过渡中的旧图又淡入一次；
 * 首次出现（没有当前图）从全透明层淡入，消失时淡出到全透明层并在动画结束后清空，
 * 所以 logo 的加载、切换、隐藏三种情况都是同一种渐变。</p>
 */
public final class HeroFade {

    /** 交叉淡入时长（ms）：背景图与 logo 共用，切换时视觉上是一组转场。 */
    public static final int DURATION = 400;

    private HeroFade() {
    }

    /**
     * @param animate false 时立即替换（首帧载入默认背景图用）
     * @param next    null 表示淡出到全透明
     */
    public static void crossFade(ImageView view, @Nullable Drawable next, boolean animate) {
        if (!animate) {
            view.setImageDrawable(next);
            return;
        }
        Drawable first = view.getDrawable();
        if (first instanceof TransitionDrawable transition && transition.getNumberOfLayers() > 0) {
            first = transition.getDrawable(transition.getNumberOfLayers() - 1);
        }
        if (first == null) first = new ColorDrawable(Color.TRANSPARENT);
        Drawable second = next == null ? new ColorDrawable(Color.TRANSPARENT) : next;
        TransitionDrawable fade = new TransitionDrawable(new Drawable[]{first, second});
        fade.setCrossFadeEnabled(true);
        view.setImageDrawable(fade);
        fade.startTransition(DURATION);
        if (next == null) {
            // 只清掉自己这一轮动画；期间若又切了新素材，getDrawable() 已不是 fade。
            view.postDelayed(() -> {
                if (view.getDrawable() == fade) view.setImageDrawable(null);
            }, DURATION);
        }
    }

    /**
     * 生成 logo 专用的 Drawable：固有尺寸恒为画布大小，绘制时按位图自身宽高比等比居中。
     *
     * <p>不能直接把 {@code BitmapDrawable} 交给 {@link TransitionDrawable}：后者的
     * {@code getIntrinsicWidth/Height()} 取所有层的<b>最大值</b>，又把同一个 bounds 传给每层，
     * 而 {@code BitmapDrawable} 默认 {@code Gravity.FILL} 会把位图<b>拉伸铺满</b> bounds。
     * 两张宽高比不同的 logo 叠加时，当前 logo 就被拉到「相邻那张」的尺寸上 —— 快速横移时的
     * 偶发变形正是这么来的（相邻两片宽高比差异越大，越明显）。</p>
     *
     * <p>这里让每层返回同一个固有尺寸，各自在画布内按自身比例居中，因此每个 logo 的
     * 显示尺寸只由它自己和画布决定，与相邻素材完全无关。</p>
     *
     * @param canvasWidth  画布宽（px），应与 logo 控件的基准宽度一致
     * @param canvasHeight 画布高（px）
     */
    public static Drawable logo(@Nullable Bitmap bitmap, int canvasWidth, int canvasHeight) {
        if (bitmap == null || bitmap.isRecycled() || canvasWidth <= 0 || canvasHeight <= 0) return null;
        return new LogoDrawable(bitmap, canvasWidth, canvasHeight);
    }

    /** 固定画布尺寸的 logo 层：只影响自身绘制，不参与「取最大固有尺寸」的层间协商。 */
    private static final class LogoDrawable extends Drawable {

        private final Bitmap bitmap;
        private final int canvasWidth;
        private final int canvasHeight;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final RectF dst = new RectF();

        LogoDrawable(Bitmap bitmap, int canvasWidth, int canvasHeight) {
            this.bitmap = bitmap;
            this.canvasWidth = canvasWidth;
            this.canvasHeight = canvasHeight;
        }

        @Override
        public int getIntrinsicWidth() {
            return canvasWidth;
        }

        @Override
        public int getIntrinsicHeight() {
            return canvasHeight;
        }

        @Override
        public void draw(Canvas canvas) {
            if (bitmap.isRecycled()) return;
            Rect bounds = getBounds();
            if (bounds.isEmpty()) return;
            // FIT_CENTER：取较小缩放比，保证整张位图落在画布内且宽高比不变，四边留透明。
            float scale = Math.min(bounds.width() / (float) bitmap.getWidth(),
                    bounds.height() / (float) bitmap.getHeight());
            float width = bitmap.getWidth() * scale;
            float height = bitmap.getHeight() * scale;
            float left = bounds.left + (bounds.width() - width) / 2f;
            float top = bounds.top + (bounds.height() - height) / 2f;
            dst.set(left, top, left + width, top + height);
            canvas.drawBitmap(bitmap, null, dst, paint);
        }

        /** TransitionDrawable 的交叉淡入靠逐层 setAlpha 实现，这里的 alpha 必须落到画笔上。 */
        @Override
        public void setAlpha(int alpha) {
            if (paint.getAlpha() == alpha) return;
            paint.setAlpha(alpha);
            invalidateSelf();
        }

        @Override
        public int getAlpha() {
            return paint.getAlpha();
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
            invalidateSelf();
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
