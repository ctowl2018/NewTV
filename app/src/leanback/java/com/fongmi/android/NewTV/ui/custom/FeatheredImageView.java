package com.fongmi.android.NewTV.ui.custom;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.FrameLayout;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

/**
 * 右上角背景图（首页、分类页共用）。
 *
 * <p>图片锚定在父容器右上角（顶部、右侧与屏幕边缘贴合），左侧与底部在运行时按比例做
 * alpha 羽化（渐变透明），使边缘自然融入 App 背景色，不产生硬接缝。羽化只作用在内存位图上，
 * 不修改原始图片文件；宽高与羽化宽度都按比例计算，因此任意屏幕尺寸下视觉比例一致。</p>
 *
 * <p>羽化曲线用五点 S 形（smoothstep）而不是两点线性：线性渐变在起点和终点处存在明显的
 * 一阶不连续，肉眼能看出「开始变淡」和「完全消失」两道折线；S 曲线两端斜率为 0，
 * 过渡与背景融合得更连续。</p>
 *
 * <p>缩放：控件宽高永远按位图自身的宽高比换算（见 {@link #load(int, float)}），
 * 配合 {@link ScaleType#FIT_XY}，等比缩放到目标尺寸，不会出现拉伸变形。</p>
 */
public class FeatheredImageView extends AppCompatImageView {

    /** 左侧羽化宽度占图片宽度的比例。 */
    private static final float LEFT_FADE_RATIO = 0.22f;
    /** 底部羽化高度占图片高度的比例。 */
    private static final float BOTTOM_FADE_RATIO = 0.28f;
    /** 解码宽度上限，避免高分辨率屏幕下原图解码占用过多内存。 */
    private static final int MAX_DECODE_WIDTH = 1920;

    /** S 形羽化的采样点位置（0 = 全透明边缘端，1 = 完全不透明的图片内部）。 */
    private static final float[] FADE_POSITIONS = {0f, 0.125f, 0.25f, 0.375f, 0.5f, 0.625f, 0.75f, 0.875f, 1f};
    /** 与上面的位置一一对应的 alpha：smoothstep(3t²-2t³) 的取值，分段越多越接近连续曲线。 */
    private static final int[] FADE_ALPHAS = {0x00, 0x0B, 0x28, 0x51, 0x80, 0xAE, 0xD7, 0xF4, 0xFF};

    public FeatheredImageView(@NonNull Context context) {
        this(context, null);
    }

    public FeatheredImageView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FeatheredImageView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setScaleType(ScaleType.FIT_XY);
        setFocusable(false);
        setFocusableInTouchMode(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /**
     * 载入图片并按屏幕宽度比例定位到右上角。
     *
     * @param resId      图片资源
     * @param widthRatio 图片宽度占屏幕宽度的比例
     */
    public void load(@DrawableRes int resId, float widthRatio) {
        Bitmap src = decode(resId);
        if (src == null) return;
        int targetW = Math.max(1, Math.round(getResources().getDisplayMetrics().widthPixels * widthRatio));
        // 高度严格按原图宽高比换算，源图是 16:9（1920x1080）时目标尺寸也是 16:9，不会变形。
        int targetH = Math.max(1, Math.round(targetW * (float) src.getHeight() / src.getWidth()));
        Bitmap scaled = Bitmap.createScaledBitmap(src, targetW, targetH, true);
        if (scaled != src) src.recycle();
        Bitmap feathered = feather(scaled);
        if (feathered != scaled) scaled.recycle();
        setImageBitmap(feathered);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(targetW, targetH);
        params.gravity = Gravity.TOP | Gravity.RIGHT;
        setLayoutParams(params);
    }

    private Bitmap decode(@DrawableRes int resId) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(getResources(), resId, bounds);
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= MAX_DECODE_WIDTH) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeResource(getResources(), resId, options);
    }

    /** 左侧与底部各画一层 S 形 alpha 渐变，用 DST_IN 把对应区域擦成透明。 */
    private static Bitmap feather(Bitmap src) {
        Bitmap out = src.copy(Bitmap.Config.ARGB_8888, true);
        if (out == null) return src;
        int width = out.getWidth();
        int height = out.getHeight();
        if (width <= 0 || height <= 0) return out;
        Canvas canvas = new Canvas(out);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        int fadeW = Math.max(1, Math.round(width * LEFT_FADE_RATIO));
        int fadeH = Math.max(1, Math.round(height * BOTTOM_FADE_RATIO));
        // 左侧：从左边缘的全透明，S 形过渡到图片内部的完全不透明。
        paint.setShader(new LinearGradient(0, 0, fadeW, 0, alphaColors(false), FADE_POSITIONS, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, fadeW, height, paint);
        // 底部：从图片内部的不透明，S 形衰减到底边缘的全透明。
        paint.setShader(new LinearGradient(0, height - fadeH, 0, height, alphaColors(true), FADE_POSITIONS, Shader.TileMode.CLAMP));
        canvas.drawRect(0, height - fadeH, width, height, paint);
        paint.setShader(null);
        paint.setXfermode(null);
        return out;
    }

    /**
     * 生成渐变用的颜色数组。
     *
     * @param reverse true 表示由不透明渐变到透明（底部羽化），false 表示由透明渐变到不透明（左侧羽化）
     */
    private static int[] alphaColors(boolean reverse) {
        int[] colors = new int[FADE_ALPHAS.length];
        for (int i = 0; i < FADE_ALPHAS.length; i++) {
            int alpha = FADE_ALPHAS[reverse ? FADE_ALPHAS.length - 1 - i : i];
            // DST_IN 只看 alpha 通道，RGB 保持黑色即可。
            colors[i] = alpha << 24;
        }
        return colors;
    }
}
