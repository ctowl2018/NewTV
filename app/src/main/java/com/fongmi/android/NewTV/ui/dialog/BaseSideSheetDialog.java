package com.fongmi.android.NewTV.ui.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.NewTV.utils.Util;
import com.google.android.material.sidesheet.SideSheetDialog;

public abstract class BaseSideSheetDialog extends AppCompatDialogFragment {

    protected abstract ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container);

    protected abstract int getWidth();

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        SideSheetDialog dialog = new SideSheetDialog(requireContext());
        dialog.getBehavior().setDraggable(false);
        Window window = dialog.getWindow();
        if (window == null) return dialog;
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if (Util.isFullscreen(getActivity())) window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        // 与播放页浮层统一的展开/收起动效（资源只在 leanback 提供，mobile 找不到则保持默认）
        int anim = requireContext().getResources().getIdentifier("Animation_PlaybackSideSheet", "style", requireContext().getPackageName());
        if (anim != 0) window.setWindowAnimations(anim);
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
        initView();
        initEvent();
    }

    protected void initView() {
    }

    protected void initEvent() {
    }

    @Override
    public void onStart() {
        super.onStart();
        FrameLayout sheet = getDialog().findViewById(com.google.android.material.R.id.m3_side_sheet);
        if (sheet == null) return;
        ViewGroup.LayoutParams params = sheet.getLayoutParams();
        params.width = getWidth();
        sheet.setLayoutParams(params);
        // 与播放页浮层同一背景（深色玻璃 + 圆角 + 描边）；资源只在 leanback 提供
        int background = requireContext().getResources().getIdentifier("shape_video_sheet", "drawable", requireContext().getPackageName());
        if (background != 0) sheet.setBackgroundResource(background);
    }
}
