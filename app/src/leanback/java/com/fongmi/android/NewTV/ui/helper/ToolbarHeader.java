package com.fongmi.android.NewTV.ui.helper;

import android.app.Activity;

import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.NewTV.R;
import com.fongmi.android.NewTV.databinding.ViewToolbarBinding;
import com.fongmi.android.NewTV.ui.activity.KeepActivity;
import com.fongmi.android.NewTV.ui.activity.SearchActivity;
import com.fongmi.android.NewTV.ui.activity.SettingActivity;
import com.fongmi.android.NewTV.ui.dialog.LocalConnectDialog;
import com.fongmi.android.NewTV.utils.Clock;
import com.fongmi.android.NewTV.utils.ImgUtil;

/**
 * 收藏 / 搜索 / 播放详情等页面的顶部品牌行绑定器。
 *
 * <p>与首页 {@link HomeHeader} 的样式一致，但不含站点名称与导航栏胶囊：
 * logo（站点选择，慢闪提示）、搜索、收藏、连接、设置、时钟。
 */
public class ToolbarHeader {

    private final Activity activity;
    private final ViewToolbarBinding binding;
    private final Clock clock;
    private boolean clockRunning;

    private ToolbarHeader(Activity activity, ViewToolbarBinding binding) {
        this.activity = activity;
        this.binding = binding;
        this.clock = Clock.create(binding.clock);
    }

    public static ToolbarHeader bind(Activity activity, ViewToolbarBinding binding) {
        ToolbarHeader header = new ToolbarHeader(activity, binding);
        header.init();
        return header;
    }

    private void init() {
        ImgUtil.logo(binding.logoIcon);
        // 非首页/分类页：logo 仅作静态品牌展示，不再触发站点选择菜单，也不做慢闪焦点特效。
        binding.funcSearch.setOnClickListener(v -> openSearch());
        binding.funcKeep.setOnClickListener(v -> openKeep());
        binding.funcConnect.setOnClickListener(v -> openConnect());
        binding.funcSetting.setOnClickListener(v -> SettingActivity.start(activity));
    }

    private void openSearch() {
        if (activity instanceof SearchActivity) return;
        SearchActivity.start(activity);
    }

    private void openKeep() {
        if (activity instanceof KeepActivity) return;
        KeepActivity.start(activity);
    }

    private void openConnect() {
        if (!(activity instanceof FragmentActivity)) return;
        LocalConnectDialog.show((FragmentActivity) activity, "push");
    }

    public void onResume() {
        if (clockRunning) return;
        clockRunning = true;
        clock.start();
    }

    public void onPause() {
        if (!clockRunning) return;
        clockRunning = false;
        clock.stop();
    }

    public void onRefreshLogo() {
        ImgUtil.logo(binding.logoIcon);
    }
}
