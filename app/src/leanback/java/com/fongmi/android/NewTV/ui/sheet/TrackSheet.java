package com.fongmi.android.NewTV.ui.sheet;

import android.view.View;

import androidx.annotation.StringRes;
import androidx.fragment.app.FragmentActivity;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.Tracks;
import androidx.media3.ui.DefaultTrackNameProvider;
import androidx.media3.ui.TrackNameProvider;

import com.fongmi.android.NewTV.App;
import com.fongmi.android.NewTV.R;
import com.fongmi.android.NewTV.bean.Danmaku;
import com.fongmi.android.NewTV.bean.Track;
import com.fongmi.android.NewTV.databinding.ViewSheetTrackBinding;
import com.fongmi.android.NewTV.player.PlayerManager;
import com.fongmi.android.NewTV.player.compat.SubtitleSelectionState;
import com.fongmi.android.NewTV.player.track.TrackUtil;
import com.fongmi.android.NewTV.setting.DanmakuSetting;
import com.fongmi.android.NewTV.ui.activity.PlaybackActivity;
import com.fongmi.android.NewTV.ui.adapter.DanmakuAdapter;
import com.fongmi.android.NewTV.ui.adapter.TrackAdapter;
import com.fongmi.android.NewTV.ui.dialog.AudioSettingDialog;
import com.fongmi.android.NewTV.ui.dialog.DanmakuSearchDialog;
import com.fongmi.android.NewTV.ui.dialog.DanmakuSettingDialog;
import com.fongmi.android.NewTV.ui.dialog.PlaybackContentDialog;
import com.fongmi.android.NewTV.ui.dialog.SubtitleSearchDialog;
import com.fongmi.android.NewTV.ui.dialog.SubtitleSettingDialog;
import com.fongmi.android.NewTV.ui.dialog.VideoSettingDialog;
import com.fongmi.android.NewTV.utils.ResUtil;
import com.fongmi.android.NewTV.utils.SliderUtil;
import com.google.android.material.slider.Slider;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;

/**
 * 字幕 / 音轨 / 视轨 / 弹幕浮层的内容。
 *
 * <p>三条轨道类型的条目、选中态与切换逻辑沿用原来的轨道对话框；弹幕列表沿用原来的弹幕对话框。
 * 区别只在容器：从底部弹窗改成贴着控制条按钮上方的浮层（60% 半透明 + 与选集一致的展开动效）。
 *
 * <p>动作图标按类型显隐：
 * <ul>
 *   <li>字幕：搜索 / 本地文件 / 字幕内容 / 字幕设置</li>
 *   <li>音轨、视轨：设置</li>
 *   <li>弹幕：搜索 / 设置（本地文件与弹幕内容按钮已按要求移除）</li>
 * </ul>
 */
public final class TrackSheet {

    /** 浮层宽度：字幕轨道名通常较长，固定一档宽度比自适应更稳。 */
    public static final int WIDTH_DP = 360;

    private final FragmentActivity activity;
    private final ViewSheetTrackBinding binding;
    private final TrackNameProvider provider;
    private final Runnable hide;
    private final IntConsumer chooseFile;
    private PlayerManager player;
    private int type = C.TRACK_TYPE_TEXT;

    public TrackSheet(FragmentActivity activity, Runnable hide, IntConsumer chooseFile) {
        this.activity = activity;
        this.hide = hide;
        this.chooseFile = chooseFile;
        this.binding = ViewSheetTrackBinding.inflate(activity.getLayoutInflater());
        this.provider = new DefaultTrackNameProvider(App.get().getResources());
        this.binding.sheetTrackRecycler.setItemAnimator(null);
        this.binding.sheetTrackRecycler.setHasFixedSize(true);
    }

    public View getView() {
        return binding.getRoot();
    }

    /** 轨道（字幕 / 音轨 / 视轨）。 */
    public void bindTrack(PlayerManager player, int type) {
        this.player = player;
        this.type = type;
        binding.sheetTrackTitle.setText(ResUtil.getStringArray(R.array.select_track)[type - 1]);
        boolean subtitle = type == C.TRACK_TYPE_TEXT;
        boolean actions = subtitle && player != null && player.isVod();
        binding.sheetTrackSearch.setVisibility(actions ? View.VISIBLE : View.GONE);
        binding.sheetTrackChoose.setVisibility(actions ? View.VISIBLE : View.GONE);
        binding.sheetTrackContent.setVisibility(subtitle ? View.VISIBLE : View.GONE);
        binding.sheetTrackSetting.setVisibility(View.VISIBLE);
        binding.sheetTrackSetting.setContentDescription(activity.getString(getSettingText()));
        binding.sheetTrackDanmakuQuick.setVisibility(View.GONE);
        bindActions(false);
        TrackAdapter adapter = new TrackAdapter(item -> onTrackClick(item));
        binding.sheetTrackRecycler.setAdapter(adapter.addAll(getTrack()));
        binding.sheetTrackRecycler.setVisibility(adapter.getItemCount() == 0 ? View.GONE : View.VISIBLE);
        if (adapter.getItemCount() > 0) binding.sheetTrackRecycler.scrollToPosition(adapter.getSelected());
    }

    /** 弹幕列表。 */
    public void bindDanmaku(PlayerManager player) {
        this.player = player;
        binding.sheetTrackTitle.setText(R.string.danmaku_select);
        binding.sheetTrackSearch.setVisibility(player == null || player.getMetadata() == null ? View.GONE : View.VISIBLE);
        binding.sheetTrackChoose.setVisibility(View.GONE);
        binding.sheetTrackContent.setVisibility(View.GONE);
        binding.sheetTrackSetting.setVisibility(View.VISIBLE);
        binding.sheetTrackSetting.setContentDescription(activity.getString(R.string.danmaku_setting));
        binding.sheetTrackDanmakuQuick.setVisibility(View.VISIBLE);
        bindDanmakuQuick();
        bindActions(true);
        DanmakuAdapter adapter = new DanmakuAdapter(this::onDanmakuClick);
        binding.sheetTrackRecycler.setAdapter(adapter.addAll(player == null ? new ArrayList<>() : player.getDanmakus()));
        binding.sheetTrackRecycler.setVisibility(adapter.getItemCount() == 0 ? View.GONE : View.VISIBLE);
        binding.sheetTrackRecycler.scrollToPosition(adapter.getSelected());
    }

    /**
     * 弹幕浮层内的两项常用设置。与弹幕设置页共用 {@link DanmakuSetting} 的同一份持久化配置，
     * 范围 / 步长 / 默认值 / 文案完全一致；调整后立刻把新配置推给播放器，画面中的弹幕实时重排。
     */
    private void bindDanmakuQuick() {
        Slider textScale = binding.textScaleSlider;
        textScale.clearOnChangeListeners();
        SliderUtil.setValue(textScale, DanmakuSetting.getTextScale());
        binding.textScaleValue.setText(formatTextScale(textScale.getValue()));
        textScale.addOnChangeListener((source, value, fromUser) -> {
            if (!fromUser) return;
            float snapped = SliderUtil.snap(source, value);
            DanmakuSetting.putTextScale(snapped);
            binding.textScaleValue.setText(formatTextScale(snapped));
            applyDanmakuConfig();
        });

        Slider maxScrollLines = binding.maxScrollLinesSlider;
        maxScrollLines.clearOnChangeListeners();
        SliderUtil.setValue(maxScrollLines, DanmakuSetting.getMaxScrollLines());
        binding.maxScrollLinesValue.setText(formatLines(Math.round(maxScrollLines.getValue())));
        maxScrollLines.addOnChangeListener((source, value, fromUser) -> {
            if (!fromUser) return;
            int snapped = Math.round(SliderUtil.snap(source, value));
            DanmakuSetting.putMaxScrollLines(snapped);
            binding.maxScrollLinesValue.setText(formatLines(snapped));
            applyDanmakuConfig();
        });
    }

    /** 与弹幕设置页同一格式：保留 1 位小数。 */
    private String formatTextScale(float value) {
        return String.format(Locale.getDefault(), "%.1f", value);
    }

    /** 与弹幕设置页同一规则：0 显示「自动」，其余显示行数。 */
    private String formatLines(int value) {
        return value == 0 ? activity.getString(R.string.danmaku_auto) : String.valueOf(value);
    }

    /** 复用 PlayerManager#getDanmakuConfig()：它从 DanmakuSetting 重建并带上持久化的时间偏移。 */
    private void applyDanmakuConfig() {
        if (player != null && !player.isReleased()) player.setDanmakuConfig(player.getDanmakuConfig());
    }

    /** 浮层打开后把焦点交给列表首行（列表为空时给动作图标），避免焦点留在控制条上。 */
    public void focus() {
        if (binding.sheetTrackRecycler.getVisibility() == View.VISIBLE && binding.sheetTrackRecycler.getAdapter() != null && binding.sheetTrackRecycler.getAdapter().getItemCount() > 0) {
            binding.sheetTrackRecycler.requestFocus();
            return;
        }
        if (binding.sheetTrackDanmakuQuick.getVisibility() == View.VISIBLE) {
            binding.textScaleSlider.requestFocus();
            return;
        }
        if (binding.sheetTrackSetting.getVisibility() == View.VISIBLE) binding.sheetTrackSetting.requestFocus();
        else if (binding.sheetTrackSearch.getVisibility() == View.VISIBLE) binding.sheetTrackSearch.requestFocus();
    }

    private void bindActions(boolean danmaku) {
        binding.sheetTrackSearch.setOnClickListener(view -> {
            hide.run();
            if (danmaku) DanmakuSearchDialog.create().autoSearch().show(activity);
            else SubtitleSearchDialog.create().show(activity);
        });
        binding.sheetTrackChoose.setOnClickListener(view -> {
            hide.run();
            if (chooseFile != null) chooseFile.accept(C.TRACK_TYPE_TEXT);
        });
        binding.sheetTrackContent.setOnClickListener(view -> {
            hide.run();
            PlaybackContentDialog.create().type(PlaybackContentDialog.SUBTITLE).show(activity);
        });
        binding.sheetTrackSetting.setOnClickListener(view -> {
            hide.run();
            if (danmaku) DanmakuSettingDialog.create().show(activity);
            else showSetting();
        });
    }

    private void showSetting() {
        switch (type) {
            case C.TRACK_TYPE_AUDIO -> AudioSettingDialog.create().show(activity);
            case C.TRACK_TYPE_VIDEO -> VideoSettingDialog.create().show(activity);
            default -> SubtitleSettingDialog.create().show(activity);
        }
    }

    private int getSettingText() {
        return switch (type) {
            case C.TRACK_TYPE_AUDIO -> R.string.audio_setting;
            case C.TRACK_TYPE_VIDEO -> R.string.video_setting;
            default -> R.string.subtitle_setting;
        };
    }

    private void onTrackClick(Track item) {
        if (!isCurrent()) return;
        player.setTrack(item.key(player.getKey()));
        hide.run();
    }

    private void onDanmakuClick(Danmaku item) {
        if (!isCurrent()) return;
        player.toggleDanmaku(item);
        hide.run();
    }

    private boolean isCurrent() {
        return player != null && activity instanceof PlaybackActivity playback && playback.getPlaybackPlayerState().getValue() == player;
    }

    private List<TrackAdapter.TrackItem> getTrack() {
        List<TrackAdapter.TrackItem> items = new ArrayList<>();
        if (player == null) return items;
        SubtitleSelectionState subtitleState = type == C.TRACK_TYPE_TEXT ? player.getSubtitleSelectionState() : SubtitleSelectionState.EMPTY;
        int ordinal = 0;
        for (Tracks.Group trackGroup : player.getCurrentTracks().getGroups()) {
            if (trackGroup.getType() != type) continue;
            for (int j = 0; j < trackGroup.length; j++) items.add(createTrackItem(trackGroup, j, ordinal++, subtitleState));
        }
        return items;
    }

    private TrackAdapter.TrackItem createTrackItem(Tracks.Group trackGroup, int index, int ordinal, SubtitleSelectionState subtitleState) {
        Format format = trackGroup.getTrackFormat(index);
        boolean selected = trackGroup.isTrackSelected(index);
        String name = provider.getTrackName(format);
        Track track = TrackUtil.createTrack(type, name, format, ordinal);
        track.setSelected(selected);
        int role = selected ? getSubtitleRole(subtitleState, trackGroup, index) : 0;
        track.setRole(role == R.string.subtitle_secondary_role ? Track.ROLE_SECONDARY : Track.ROLE_PRIMARY);
        return new TrackAdapter.TrackItem(track, role);
    }

    @StringRes
    private static int getSubtitleRole(SubtitleSelectionState state, Tracks.Group group, int trackIndex) {
        if (state.activeSecondarySelection == null) return 0;
        TrackSelectionOverride selection = new TrackSelectionOverride(group.getMediaTrackGroup(), trackIndex);
        if (state.isPrimary(selection)) return R.string.subtitle_primary_role;
        return state.isActiveSecondary(selection) ? R.string.subtitle_secondary_role : 0;
    }
}
