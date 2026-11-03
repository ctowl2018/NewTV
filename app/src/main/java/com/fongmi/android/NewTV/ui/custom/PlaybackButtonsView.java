package com.fongmi.android.NewTV.ui.custom;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import com.fongmi.android.NewTV.R;
import com.fongmi.android.NewTV.player.PlayerManager;
import com.fongmi.android.NewTV.player.exo.ExoCommand;
import com.fongmi.android.NewTV.player.mpv.MpvScripts;
import com.fongmi.android.NewTV.setting.PlayerSetting;
import com.fongmi.android.NewTV.ui.activity.PlaybackActivity;
import com.fongmi.android.NewTV.ui.dialog.PlaybackButtonsDialog;
import com.fongmi.android.NewTV.utils.Notify;
import com.github.catvod.utils.Prefers;

import org.json.JSONException;

public final class PlaybackButtonsView extends LinearLayout implements SharedPreferences.OnSharedPreferenceChangeListener {

    private final Observer<PlayerManager> playerObserver = this::updateVisibility;
    private LiveData<PlayerManager> players;
    private int activeEngine = -1;
    private boolean trapHorizontal;

    public PlaybackButtonsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setVisibility(GONE);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        Prefers.getPrefers().registerOnSharedPreferenceChangeListener(this);
        refresh();
        if (activity() instanceof PlaybackActivity activity) {
            players = activity.getPlaybackPlayerState();
            players.observe(activity, playerObserver);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (players != null) players.removeObserver(playerObserver);
        players = null;
        setVisibility(GONE);
        updateFocusNeighbors();
        Prefers.getPrefers().unregisterOnSharedPreferenceChangeListener(this);
        super.onDetachedFromWindow();
    }

    private FragmentActivity activity() {
        Context context = getContext();
        while (context instanceof ContextWrapper wrapper && !(context instanceof FragmentActivity)) context = wrapper.getBaseContext();
        return context instanceof FragmentActivity activity ? activity : null;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
        if (MpvScripts.PREFERENCE_KEY.equals(key)) refresh();
    }

    private void refresh() {
        boolean exo = activeEngine == PlayerSetting.ENGINE_EXO;
        View focused = getFocusedChild();
        Object focusedId = focused == null ? null : focused.getTag();
        removeAllViews();
        TextView manage = addButton(getContext().getString(exo ? R.string.exo_command_buttons : R.string.mpv_script_buttons));
        manage.setOnClickListener(view -> manage(null));
        addScriptButtons(manage, exo);
        updateFocusNeighbors();
        if (focused != null) {
            View target = focusedId == null ? manage : findViewWithTag(focusedId);
            (target == null ? manage : target).requestFocus();
        }
    }

    private void addScriptButtons(TextView manage, boolean exo) {
        try {
            for (MpvScripts.Item button : MpvScripts.read()) {
                if (button.automatic || button.hidden || (exo && (!button.isCommand() || !ExoCommand.isSupported(button.command)))) continue;
                addScriptButton(button);
            }
        } catch (JSONException e) {
            manage.setContentDescription(Notify.getError(exo ? R.string.exo_command_error : R.string.mpv_script_error, e));
        }
    }

    private void addScriptButton(MpvScripts.Item button) {
        TextView view = addButton(button.title);
        view.setAlpha(button.enabled ? 1f : 0.5f);
        if (!button.enabled) view.setContentDescription(button.title + " · " + getContext().getString(R.string.mpv_script_disabled));
        view.setTag(button.id);
        view.setOnClickListener(v -> run(button));
        view.setOnLongClickListener(v -> {
            manage(button.id);
            return true;
        });
    }

    private TextView addButton(String title) {
        TextView view = (TextView) LayoutInflater.from(getContext()).inflate(R.layout.view_mpv_script_button, this, false);
        view.setText(title);
        view.setId(View.generateViewId());
        view.setNextFocusDownId(view.getId());
        addView(view);
        return view;
    }

    private void updateFocusNeighbors() {
        if (trapHorizontal) {
            chainHorizontal();
            return;
        }
        boolean visible = getVisibility() == VISIBLE;
        View first = getChildAt(0);
        View last = getChildAt(getChildCount() - 1);
        View left = getRootView().findViewById(getNextFocusLeftId());
        View right = getRootView().findViewById(getNextFocusRightId());
        if (left != null) {
            first.setNextFocusLeftId(left.getId());
            left.setNextFocusRightId(visible ? first.getId() : getNextFocusRightId());
        }
        if (right != null) {
            last.setNextFocusRightId(right.getId());
            right.setNextFocusLeftId(visible ? last.getId() : getNextFocusLeftId());
        }
    }

    /**
     * 浮层内使用：左右键只在本排按钮内走位，首尾各自封口，绝不把焦点带出弹层。
     *
     * <p>控制条那侧的 {@link #updateFocusNeighbors()} 会把首/末按钮的左/右指向控制条上的相邻按钮，
     * 那套规则只适用于控制条；放进「播放设置」浮层后会把焦点带回控制条，所以浮层内改用排内闭环。
     */
    public void trapHorizontalFocus() {
        trapHorizontal = true;
        updateFocusNeighbors();
    }

    /** 排内左右闭环：每个按钮的左/右都指向同排相邻按钮，首尾指向自己。 */
    private void chainHorizontal() {
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View view = getChildAt(i);
            view.setNextFocusLeftId(i == 0 ? view.getId() : getChildAt(i - 1).getId());
            view.setNextFocusRightId(i == count - 1 ? view.getId() : getChildAt(i + 1).getId());
        }
    }

    private void updateVisibility(PlayerManager manager) {
        int engine = manager == null ? -1 : manager.getEngine();
        // 「指令」（EXO 命令）已从播放设置移除，只保留 MPV 脚本按钮
        boolean visible = engine == PlayerSetting.ENGINE_MPV;
        boolean restoreFocus = !visible && hasFocus();
        if (activeEngine != engine) {
            activeEngine = engine;
            refresh();
        }
        setVisibility(visible ? VISIBLE : GONE);
        updateFocusNeighbors();
        if (restoreFocus) {
            View target = getRootView().findViewById(getNextFocusLeftId());
            if (target != null && target.isShown()) target.requestFocus();
        }
    }

    private void manage(String id) {
        FragmentActivity activity = activity();
        if (activity != null) PlaybackButtonsDialog.show(activity, id);
    }

    private void run(MpvScripts.Item item) {
        FragmentActivity activity = activity();
        PlayerManager manager = activity instanceof PlaybackActivity playback ? playback.getPlaybackPlayer() : null;
        if (manager == null) {
            Notify.show(activeEngine == PlayerSetting.ENGINE_MPV ? R.string.mpv_script_unavailable : R.string.exo_command_unavailable);
            return;
        }
        if (manager.getEngine() == PlayerSetting.ENGINE_MPV) {
            if (!manager.runMpvScript(item)) Notify.show(R.string.mpv_script_unavailable);
        } else if (manager.getEngine() == PlayerSetting.ENGINE_EXO) {
            ExoCommand.Result result = manager.runExoCommand(item, ((PlaybackActivity) activity).getPlaybackPlayerView());
            if (result == ExoCommand.Result.UNAVAILABLE) Notify.show(R.string.exo_command_unavailable);
            else if (result == ExoCommand.Result.UNSUPPORTED) Notify.show(R.string.exo_command_unsupported);
        }
    }
}
