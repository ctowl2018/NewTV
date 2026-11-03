package com.fongmi.android.NewTV.ui.dialog;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.activity.ComponentDialog;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.NewTV.utils.Util;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public abstract class BaseBottomSheetDialog extends BottomSheetDialogFragment {

    private final OnBackPressedCallback backCallback = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            onBackInvoked();
        }
    };

    protected abstract ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container);

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        Window window = dialog.getWindow();
        if (window == null) return dialog;
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if (Util.isFullscreen(getActivity())) window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return getBinding(inflater, container).getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupBackDispatcher();
        initView();
        initEvent();
    }

    @Override
    public void onStart() {
        super.onStart();
        setSheet((BottomSheetDialog) requireDialog());
    }

    protected void initView() {
    }

    protected void initEvent() {
    }

    protected void onBackInvoked() {
    }

    protected final void setBackCallbackEnabled(boolean enabled) {
        backCallback.setEnabled(enabled);
    }

    protected int getMaxHeight() {
        return 0;
    }

    private void setSheet(BottomSheetDialog dialog) {
        FrameLayout sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet == null) return;
        BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(sheet);
        setHeight(sheet, behavior);
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        behavior.setSkipCollapsed(true);
        applyPlaybackSheetStyle(dialog, sheet);
    }

    /**
     * 与播放页浮层（{@code VideoSheet}）统一表面与动效。
     *
     * <p>播放控制条上的浮层用「60% 深色玻璃 + 14dp 圆角 + 1dp 半透明描边」，从浮层里再打开的
     * 子级弹窗（版本 / 章节 / 解析 / 字幕搜索 / 弹幕搜索 / 播放器引擎等）原来是 Material 默认的
     * 实心板 + 投影，两种材质放在一起很割裂。这里复刻父级那套表面与展开动效，
     * 只把贴底的两只角改成直角（{@code shape_video_sheet_bottom}），并去掉默认投影。
     *
     * <p>资源只由 leanback 提供，mobile 找不到对应资源时整体跳过、保持 Material 默认外观。
     */
    private void applyPlaybackSheetStyle(BottomSheetDialog dialog, FrameLayout sheet) {
        Context context = requireContext();
        String pkg = context.getPackageName();
        int background = context.getResources().getIdentifier("shape_video_sheet_bottom", "drawable", pkg);
        if (background == 0) return;
        sheet.setBackgroundResource(background);
        sheet.setElevation(0f);
        Window window = dialog.getWindow();
        if (window == null) return;
        int anim = context.getResources().getIdentifier("Animation_PlaybackSideSheet", "style", pkg);
        if (anim != 0) window.setWindowAnimations(anim);
    }

    private void setHeight(FrameLayout sheet, BottomSheetBehavior<FrameLayout> behavior) {
        int maxHeight = getMaxHeight();
        if (maxHeight <= 0) return;
        behavior.setMaxHeight(maxHeight);
        ViewGroup.LayoutParams params = sheet.getLayoutParams();
        params.height = ViewGroup.LayoutParams.MATCH_PARENT;
        sheet.setLayoutParams(params);
        ViewCompat.setOnApplyWindowInsetsListener(requireView(), (view, insets) -> {
            int keyboard = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            int navigation = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            int height = maxHeight + Math.max(0, keyboard - navigation);
            if (behavior.getMaxHeight() != height) {
                behavior.setMaxHeight(height);
                sheet.requestLayout();
            }
            return insets;
        });
        ViewCompat.requestApplyInsets(requireView());
    }

    private void setupBackDispatcher() {
        if (requireDialog() instanceof ComponentDialog dialog) dialog.getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), backCallback);
    }

    @Override
    public void onDestroyView() {
        backCallback.setEnabled(false);
        super.onDestroyView();
    }
}
