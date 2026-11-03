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
 * 首页右上角背景图。
 *
 * <p>图片锚定在父容器右上角（顶部、右侧与屏幕边缘贴合），左侧与底部在运行时按比例做
 * alpha 羽化（渐变透明），使边缘自然融入 App 背景色，不产生硬接缝。羽化只作用在内存位图上，
 * 不修改原始图片文件；宽高与羽化宽度都按比例计算，因此任意屏幕尺寸下视觉比例一致。</p>
 */
public class FeatheredImageView extends AppCompatImageView {

    /** 左侧羽化宽度占图片宽度的比例。 */
    private static final float LEFT_FADE_RATIO = 0.16f;
    /** 底部羽化高度占图片高度的比例。 */
    private static final float BOTTOM_FADE_RATIO = 0.20f;
    /** 解码宽度上限，避免高分辨率屏幕下原图解码占用过多内存。 */
    private static final int MAX_DECODE_WIDTH = 1920;

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

    /** 左侧与底部各画一层 alpha 线性渐变，用 DST_IN 把对应区域擦成透明。 */
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
        paint.setShader(new LinearGradient(0, 0, fadeW, 0, 0x00000000, 0xFF000000, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, fadeW, height, paint);
        paint.setShader(new LinearGradient(0, height - fadeH, 0, height, 0xFF000000, 0x00000000, Shader.TileMode.CLAMP));
        canvas.drawRect(0, height - fadeH, width, height, paint);
        paint.setShader(null);
        paint.setXfermode(null);
        return out;
    }
}
