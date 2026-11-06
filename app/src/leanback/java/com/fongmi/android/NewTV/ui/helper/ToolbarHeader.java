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

/**
 * 收藏 / 搜索等页面的顶部品牌行绑定器。
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
        // 品牌组件自带锁定：图标 / 字标固定为内置资源，不再由站点配置动态加载。
        // 非首页/分类页：logo 仅作静态品牌展示，不触发站点选择菜单，也不做慢闪焦点特效。
        binding.logo.refreshLocked();
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

    /**
     * 顶部渐变遮罩开关。
     *
     * <p>搜索页 / 搜索详情页保持默认关闭；播放详情页、收藏页打开。
     * 遮罩用页头根布局的 background 承载，高度天然等于页头高度，
     * 下边界始终与页头（导航栏）下边界精确齐平，且不会影响 wrap_content 页头的测量。
     */
    public void setScrimVisible(boolean visible) {
        binding.getRoot().setBackgroundResource(visible ? R.drawable.shape_top_scrim : 0);
    }

    /**
     * 站点切换 / 配置刷新后调用。
     *
     * <p>品牌组件已锁定：忽略站点数据里的 logo，只把内置图标与字标重新校正回固定外观，
     * 保证运行时不会被数据源覆盖。
     */
    public void onRefreshLogo() {
        binding.logo.refreshLocked();
    }
}
