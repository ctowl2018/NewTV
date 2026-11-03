package com.fongmi.android.NewTV.ui.custom;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.animation.DecelerateInterpolator;

import androidx.media3.ui.DefaultTimeBar;
import androidx.media3.ui.TimeBar;

import com.fongmi.android.NewTV.Constant;

/**
 * 播放进度条：按键快进/快退 + 聚焦态的「条身加粗发光」与「圆形滑块放大」。
 *
 * <p><b>为什么不再用背景描边</b>：原先用 {@code selector_seek_focus} 给整条加一圈线框，
 * 与播放页浮层/控制条的无边框风格冲突。现在改为条身自身的变化：
 * <ul>
 *   <li>条身由 4dp 加粗到 6.5dp，并在四周叠三层递减的半透明橙色光晕（模拟发光）；</li>
 *   <li>圆形滑块半径由 6dp 放大到 7.6dp（直径 12dp → 15.2dp），外加一圈柔光；</li>
 *   <li>用 160ms 补间过渡，键盘移入/移出不会突兀。</li>
 * </ul>
 *
 * <p><b>绘制方式</b>：{@link DefaultTimeBar} 的 {@code barHeight}/{@code scrubberEnabledSize}
 * 都是 final 字段、没有 setter，因此保留父类的条身绘制，聚焦时在其上覆盖一层自绘的加粗版本。
 * 父类默认 {@code bar_gravity=center}，条与滑块都居中于视图高度，覆盖层按 {@code getHeight()/2}
 * 精确对齐。
 *
 * <p><b>边界（防溢出）</b>：父类会把进度条内缩 {@code scrubberPadding}（默认 8dp）给滑块留空间，
 * 自绘层若按整宽计算，0% / 100% 时滑块中心正好落在视图边缘，半个圆（以及外圈柔光）会被裁掉，
 * 看起来就是滑块溢出进度条容器。因此这里统一改用
 * {@code [paddingLeft + 8dp, width - paddingRight - 8dp]} 作为条身范围，并把滑块中心再钳制一个
 * 「最大滑块半径 7.6dp」：0% 时滑块左沿贴住条首、100% 时右沿贴住条尾，聚焦放大也不越界；
 * 3.5dp 的柔光正好落在父类预留的 8dp 余量内，整颗滑块（含光晕）始终完整显示。
 *
 * <p><b>滑块必须独占绘制</b>：父类的 {@code drawPlayhead} 在 {@code isFocused()} 或拖动时会把滑块
 * 从 {@code scrubberEnabledSize}（12dp）切成更大的 {@code scrubberDraggedSize}（16dp）再画一个，
 * 半径 8dp 比自绘的 7.6dp 还大，露在外面就是「聚焦后出现两个滑块」。所以构造时先把父类滑块染成
 * 全透明，滑块（含常态尺寸）一律由本类绘制，任何状态下都只有一个，聚焦只是把它平滑放大。
 */
public final class LeanbackTimeBar extends DefaultTimeBar implements TimeBar.OnScrubListener {

    /** 父类条高：本布局未覆盖 style，取 DefaultTimeBar 构造默认值 4dp。 */
    private static final float BAR_HEIGHT_DP = 4f;
    /**
     * 父类给滑块预留的左右内缩量（{@code DefaultTimeBar.scrubberPadding}）：
     * {@code (max(scrubberDisabledSize, scrubberEnabledSize, scrubberDraggedSize) + 1) / 2}，
     * 默认 0 / 12dp / 16dp → 8dp。父类的进度条只画在 {@code [paddingLeft + 8dp, width - paddingRight - 8dp]}
     * 内；自绘层必须用同一组边界，否则 0% / 100% 时滑块会画到视图外面。
     */
    private static final float BAR_INSET_DP = 8f;
    /** 聚焦时条身的加粗量：4dp → 6.5dp（细微加粗，不做夸张放大）。 */
    private static final float BAR_EXTRA_DP = 2.5f;
    /** 常态滑块半径（父类默认 scrubber_enabled_size = 12dp，即半径 6dp）。 */
    private static final float SCRUBBER_RADIUS_DP = 6f;
    /** 聚焦时滑块半径的放大量：6dp → 7.6dp（直径 12dp → 15.2dp，轻微放大）。 */
    private static final float SCRUBBER_EXTRA_DP = 1.6f;
    /** 滑块外圈柔光相对滑块半径的额外扩散量（{@link #drawScrubber} 使用）。 */
    private static final float SCRUBBER_HALO_DP = 3.5f;
    private static final long EMPHASIS_DURATION = 160L;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float density;

    private int playedColor = Color.WHITE;
    private int bufferedColor = 0xCCFFFFFF;
    private int unplayedColor = 0x33FFFFFF;
    private int scrubberColor = 0xFFE97022;

    private long duration;
    private long position;
    private long bufferedPosition;

    /** 遥控器/触摸拖动中：父类内部 scrubPosition 是否生效（与 TimeBar.OnScrubListener 同步）。 */
    private boolean scrubbing;
    /** 拖动过程中的临时位置（父类私有 scrubPosition 的镜像，用于自绘滑块实时跟随）。 */
    private long scrubPosition;
    /**
     * 松手（onScrubStop）到播放器真正 seek 回新位置之前，继续以最终 scrub 位置绘制，
     * 避免「先弹回旧位置、再跳到新位置」的闪动。-1 表示无覆盖，正常按 position 绘制。
     */
    private long scrubDrawOverride = -1;

    /** 0 = 常态，1 = 完全聚焦，中间是过渡帧。 */
    private float emphasis;
    private ValueAnimator animator;

    public LeanbackTimeBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        // 父类滑块全透明：滑块统一由本类绘制，避免聚焦时父类再画一个更大的 dragged 滑块
        super.setScrubberColor(Color.TRANSPARENT);
        // 监听自身的拖动事件，拿到父类私有的 scrubPosition，使自绘滑块在拖动中实时跟随
        addListener(this);
    }

    @Override
    public void setPlayedColor(int color) {
        playedColor = color;
        super.setPlayedColor(color);
        invalidate();
    }

    @Override
    public void setBufferedColor(int color) {
        bufferedColor = color;
        super.setBufferedColor(color);
        invalidate();
    }

    @Override
    public void setUnplayedColor(int color) {
        unplayedColor = color;
        super.setUnplayedColor(color);
        invalidate();
    }

    /** 只记录颜色，父类滑块保持全透明（否则聚焦时会出现第二个滑块）。 */
    @Override
    public void setScrubberColor(int color) {
        scrubberColor = color;
        super.setScrubberColor(Color.TRANSPARENT);
        invalidate();
    }

    @Override
    public void setDuration(long duration) {
        this.duration = duration;
        super.setDuration(duration);
        invalidate();
    }

    @Override
    public void setPosition(long position) {
        this.position = position;
        // 播放器真正回写的位置一旦与「松手时的最终 scrub 位置」对齐，就撤销覆盖，回归正常绘制。
        // 用 200ms 容差避免拖动期间播放器回写的旧播放位置误清覆盖，导致滑块提前弹回。
        if (scrubDrawOverride != -1 && Math.abs(position - scrubDrawOverride) <= 200) {
            scrubDrawOverride = -1;
        }
        super.setPosition(position);
        invalidate();
    }

    @Override
    public void setBufferedPosition(long bufferedPosition) {
        this.bufferedPosition = bufferedPosition;
        super.setBufferedPosition(bufferedPosition);
        invalidate();
    }

    @Override
    public void setSelected(boolean selected) {
        super.setSelected(selected);
        syncEmphasis();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (!enabled) resetKeyIncrement();
        syncEmphasis();
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        if (!gainFocus) resetKeyIncrement();
        syncEmphasis();
    }

    @Override
    protected void onDetachedFromWindow() {
        removeListener(this);
        resetKeyIncrement();
        if (animator != null) animator.cancel();
        emphasis = 0f;
        scrubbing = false;
        scrubPosition = 0;
        scrubDrawOverride = -1;
        super.onDetachedFromWindow();
    }

    // ---- TimeBar.OnScrubListener：把父类私有 scrubPosition 镜像出来，驱动自绘滑块实时跟随 ----
    @Override
    public void onScrubStart(TimeBar timeBar, long position) {
        scrubbing = true;
        scrubPosition = position;
        scrubDrawOverride = position;
        invalidate();
    }

    @Override
    public void onScrubMove(TimeBar timeBar, long position) {
        scrubPosition = position;
        scrubDrawOverride = position;
        invalidate();
    }

    @Override
    public void onScrubStop(TimeBar timeBar, long position, boolean canceled) {
        scrubbing = false;
        // 取消（canceled）：不 seek，回到真实播放位置；否则保持最终 scrub 位置直到播放器回写
        scrubDrawOverride = canceled ? -1 : position;
        invalidate();
    }

    /** 聚焦 / 选中态切换：用补间把 emphasis 推到 0 或 1，避免瞬间跳变。 */
    private void syncEmphasis() {
        float target = isEnabled() && (isFocused() || isSelected()) ? 1f : 0f;
        if (animator != null) animator.cancel();
        if (Math.abs(target - emphasis) < 0.001f) return;
        animator = ValueAnimator.ofFloat(emphasis, target);
        animator.setDuration(EMPHASIS_DURATION);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            emphasis = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (duration <= 0) return;
        // 与父类 DefaultTimeBar 的 progressBar 对齐：左右各留出 8dp 的滑块余量
        float left = getPaddingLeft() + dp(BAR_INSET_DP);
        float right = getWidth() - getPaddingRight() - dp(BAR_INSET_DP);
        float width = right - left;
        if (width <= 0) return;
        float centerY = getHeight() / 2f;
        // 拖动中（含松手到播放器回写之间的过渡）一律用实时 scrub 位置，使滑块与进度数值一致跟随；
        // 否则用真实播放位置。条身的 0% / 100% 用同一位置，保证进度长度不被滑块内缩影响。
        long drawPos = scrubDrawOverride != -1 ? scrubDrawOverride : position;
        float playheadX = left + fraction(drawPos) * width;
        float bufferedX = left + fraction(bufferedPosition) * width;
        // 条身：仅在聚焦过渡期叠一层加粗版本（父类画的细条被完全覆盖）
        if (emphasis > 0.01f) {
            float half = dp(BAR_HEIGHT_DP + BAR_EXTRA_DP * emphasis) / 2f;
            drawGlow(canvas, left, right, centerY, half);
            drawBar(canvas, left, right, centerY, half, unplayedColor);
            drawBar(canvas, left, bufferedX, centerY, half, bufferedColor);
            drawBar(canvas, left, playheadX, centerY, half, playedColor);
        }
        // 滑块中心再内缩一个「最大滑块半径」：0% / 100% 时圆点（含聚焦放大）不会越出进度条容器。
        // 最大半径取聚焦后的 7.6dp 而不是当前半径，避免聚焦动画过程中滑块中心左右漂移。
        float maxRadius = dp(SCRUBBER_RADIUS_DP + SCRUBBER_EXTRA_DP);
        float minX = left + maxRadius;
        float maxX = right - maxRadius;
        float scrubberX = minX >= maxX ? (left + right) / 2f : Math.max(minX, Math.min(playheadX, maxX));
        float scrubberRadius = dp(SCRUBBER_RADIUS_DP + (isEnabled() ? SCRUBBER_EXTRA_DP * emphasis : 0f));
        drawScrubber(canvas, scrubberX, centerY, scrubberRadius);
    }

    /**
     * 条身外圈的光晕：三层逐级收敛的半透明橙色胶囊。
     * 用多层绘制而不是 {@code BlurMaskFilter}，硬件加速下同样生效、开销更低。
     */
    private void drawGlow(Canvas canvas, float left, float right, float centerY, float half) {
        float[] spreads = {dp(6), dp(4), dp(2)};
        int[] alphas = {14, 24, 38};
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(scrubberColor);
        for (int i = 0; i < spreads.length; i++) {
            float spread = spreads[i];
            paint.setAlpha((int) (alphas[i] * emphasis));
            rect.set(left, centerY - half - spread, right, centerY + half + spread);
            canvas.drawRoundRect(rect, half + spread, half + spread, paint);
        }
        paint.setAlpha(255);
    }

    private void drawBar(Canvas canvas, float left, float right, float centerY, float half, int color) {
        if (right - left <= 0.5f) return;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setAlpha(255);
        rect.set(left, centerY - half, right, centerY + half);
        canvas.drawRoundRect(rect, half, half, paint);
    }

    /** 圆形滑块：外圈一层柔光 + 实心圆点，颜色与控制条播放键同为品牌橙。 */
    private void drawScrubber(Canvas canvas, float centerX, float centerY, float radius) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(scrubberColor);
        paint.setAlpha((int) (52 * emphasis));
        canvas.drawCircle(centerX, centerY, radius + dp(SCRUBBER_HALO_DP), paint);
        paint.setAlpha(255);
        canvas.drawCircle(centerX, centerY, radius, paint);
    }

    private float fraction(long timeMs) {
        if (duration <= 0) return 0f;
        return Math.max(0f, Math.min(1f, timeMs / (float) duration));
    }

    private float dp(float value) {
        return value * density;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (isEnabled() && isSeekKey(keyCode)) updateKeyIncrement(event);
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (isSeekKey(keyCode)) resetKeyIncrement();
        return super.onKeyUp(keyCode, event);
    }

    private boolean isSeekKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;
    }

    private void updateKeyIncrement(KeyEvent event) {
        long heldMs = Math.max(0, event.getEventTime() - event.getDownTime());
        setKeyTimeIncrement(Constant.INTERVAL_SEEK * (1 + heldMs / 1000));
    }

    private void resetKeyIncrement() {
        setKeyTimeIncrement(Constant.INTERVAL_SEEK);
    }
}
