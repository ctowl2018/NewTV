package com.fongmi.android.NewTV.ui.helper;

import android.app.Activity;
import android.content.Intent;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.NewTV.R;
import com.fongmi.android.NewTV.api.config.VodConfig;
import com.fongmi.android.NewTV.bean.Class;
import com.fongmi.android.NewTV.bean.Config;
import com.fongmi.android.NewTV.bean.Site;
import com.fongmi.android.NewTV.databinding.ViewHomeHeaderBinding;
import com.fongmi.android.NewTV.ui.activity.HomeActivity;
import com.fongmi.android.NewTV.ui.activity.KeepActivity;
import com.fongmi.android.NewTV.ui.activity.SearchActivity;
import com.fongmi.android.NewTV.ui.activity.SettingActivity;
import com.fongmi.android.NewTV.ui.activity.VodActivity;
import com.fongmi.android.NewTV.ui.adapter.TypeAdapter;
import com.fongmi.android.NewTV.ui.dialog.LocalConnectDialog;
import com.fongmi.android.NewTV.ui.dialog.SiteDialog;
import com.fongmi.android.NewTV.utils.Clock;
import com.fongmi.android.NewTV.utils.KeyUtil;
import com.fongmi.android.NewTV.utils.ResUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 全局顶部品牌行 + 类型导航栏的统一绑定器。
 *
 * <p>首页与分类页共用本组件，焦点规则也集中在这里：
 * <ul>
 *   <li>胶囊 ↑ → 搜索按钮；</li>
 *   <li>搜索按钮 ← → logo（站点源切换入口）；</li>
 *   <li>搜索按钮 / logo ↓ → 回到离开前的胶囊（无记录时取当前页分类胶囊）；</li>
 *   <li>胶囊 ↓ → 交给宿主进入内容区第一排海报。</li>
 * </ul>
 */
public class HomeHeader implements TypeAdapter.OnClickListener {

    /** 各页面共享的类型列表：首页加载完成后发布。 */
    private static final List<Class> PUBLISHED = new ArrayList<>();

    public interface Host {

        void onTypeClick(Class item, int position);

        default void onTypeRefresh(Class item) {
        }

        default void onTypeSelected(Class item, int position) {
        }

        /** 胶囊上按 ↓：宿主负责把焦点送进内容区第一排海报。 */
        default boolean onTypeDown(int position) {
            return false;
        }

        /** 当前页面实际对应的胶囊位置（首页=0，分类页=当前分页+1）。 */
        default int getTypePosition() {
            return 0;
        }
    }

    private final Activity activity;
    private final ViewHomeHeaderBinding binding;
    private final TypeAdapter adapter;
    private final Clock clock;

    private Host host;
    private View oldView;
    private boolean clockRunning;
    private int lastTypePosition;

    private HomeHeader(Activity activity, ViewHomeHeaderBinding binding) {
        this.activity = activity;
        this.binding = binding;
        this.adapter = new TypeAdapter(this);
        this.clock = Clock.create(binding.clock);
    }

    public static HomeHeader bind(Activity activity, ViewHomeHeaderBinding binding) {
        HomeHeader header = new HomeHeader(activity, binding);
        header.initView();
        header.initEvent();
        return header;
    }

    /** 发布当前源的分类列表。 */
    public static void publish(List<Class> types) {
        PUBLISHED.clear();
        if (types != null) PUBLISHED.addAll(types);
    }

    public static List<Class> published() {
        return Collections.unmodifiableList(PUBLISHED);
    }

    private void initView() {
        binding.typeNav.setHorizontalSpacing(ResUtil.dp2px(16));
        binding.typeNav.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        binding.typeNav.setAdapter(adapter);
        binding.typeNav.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                onTypeFocusChanged(child, position);
            }
        });
        // 导航栏按键统一在这里处理：↑ 去搜索，↓ 进当前页第一排海报。
        binding.typeNav.setOnKeyInterceptListener(event -> {
            if (!KeyUtil.isActionDown(event)) return false;
            int position = getFocusedTypePosition();
            if (KeyUtil.isUpKey(event)) {
                lastTypePosition = position;
                return binding.funcSearch.requestFocus();
            }
            if (KeyUtil.isDownKey(event)) {
                return host != null && host.onTypeDown(position);
            }
            return false;
        });
        // 品牌组件自带锁定：图标 / 字标固定为内置资源，不再由站点配置动态加载
        binding.logo.refreshLocked();
        ToolbarHelper.setupLogoBlink(binding.logo);
        setTitle();
    }

    /** 选中态跟随焦点：把胶囊的 selected 状态同步到当前项，并回传给 Host。 */
    private void onTypeFocusChanged(@Nullable RecyclerView.ViewHolder child, int position) {
        if (oldView != null) oldView.setSelected(false);
        oldView = child == null ? null : child.itemView;
        if (oldView != null) oldView.setSelected(true);
        if (position >= 0) lastTypePosition = position;
        dispatchSelected(position);
    }

    private void initEvent() {
        binding.funcSearch.setOnClickListener(v -> openSearch());
        binding.funcKeep.setOnClickListener(v -> openKeep());
        binding.funcConnect.setOnClickListener(v -> openConnect());
        binding.funcSetting.setOnClickListener(v -> SettingActivity.start(activity));
        // logo：站点源切换入口（聚焦后按确认键弹出站点列表）
        binding.logo.setOnClickListener(v -> openSite());

        View.OnKeyListener downToType = (v, keyCode, event) -> {
            if (!KeyUtil.isActionDown(event) || keyCode != KeyEvent.KEYCODE_DPAD_DOWN) return false;
            return focusTypeFromToolbar();
        };
        binding.logo.setOnKeyListener(downToType);
        binding.funcKeep.setOnKeyListener(downToType);
        binding.funcConnect.setOnKeyListener(downToType);
        binding.funcSetting.setOnKeyListener(downToType);
        binding.funcSearch.setOnKeyListener((v, keyCode, event) -> {
            if (!KeyUtil.isActionDown(event)) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) return binding.logo.requestFocus();
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) return focusTypeFromToolbar();
            return false;
        });
    }

    private void openConnect() {
        if (!(activity instanceof FragmentActivity)) return;
        LocalConnectDialog.show((FragmentActivity) activity, "push");
    }

    private void openSearch() {
        if (activity instanceof SearchActivity) return;
        SearchActivity.start(activity);
    }

    private void openKeep() {
        if (activity instanceof KeepActivity) return;
        KeepActivity.start(activity);
    }

    private void openSite() {
        if (!(activity instanceof FragmentActivity)) return;
        SiteDialog.create().show((FragmentActivity) activity);
    }

    /** 源名称：优先首页源名称，其次配置名称，最后应用名。 */
    public void setTitle() {
        Site home = VodConfig.get().getHome();
        Config config = VodConfig.get().getConfig();
        List<String> items = Arrays.asList(home == null ? "" : home.getName(), config == null ? "" : config.getName(), activity.getString(R.string.app_name));
        Optional<String> optional = items.stream().filter(s -> !TextUtils.isEmpty(s)).findFirst();
        optional.ifPresent(s -> binding.title.setText(s));
    }

    public void setHost(Host host) {
        this.host = host;
    }

    public TypeAdapter getAdapter() {
        return adapter;
    }

    public ViewHomeHeaderBinding getBinding() {
        return binding;
    }

    public void setTypes(List<Class> types) {
        setTypes(types, 0);
    }

    public void setTypes(List<Class> types, int selected) {
        if (types == null || types.isEmpty()) {
            adapter.clear();
            lastTypePosition = 0;
            return;
        }
        adapter.addAll(types);
        lastTypePosition = Math.max(0, Math.min(selected, adapter.getItemCount() - 1));
        select(lastTypePosition);
    }

    public void setTypesIfEmpty(List<Class> types) {
        if (adapter.getItemCount() > 0) return;
        setTypes(types);
    }

    public void select(int position) {
        if (adapter.getItemCount() == 0) return;
        int target = Math.max(0, Math.min(position, adapter.getItemCount() - 1));
        adapter.setSelected(target);
        binding.typeNav.setSelectedPosition(target);
        lastTypePosition = target;
    }

    /**
     * 把焦点精确落到某一枚胶囊。
     *
     * <p>{@code requestFocus()} 在网格里可能先落到可见的第一个子项，
     * 因此再补一帧按 adapter position 校正到目标胶囊。
     */
    public boolean focusType(int position) {
        if (adapter.getItemCount() == 0) return false;
        int target = Math.max(0, Math.min(position, adapter.getItemCount() - 1));
        select(target);
        if (focusTypeChild(target)) return true;
        // 目标胶囊还没布局出来：先让网格拿到焦点，下一帧再精确落到目标位置。
        binding.typeNav.requestFocus();
        binding.typeNav.post(() -> focusTypeChild(target));
        return true;
    }

    /**
     * 直接把焦点落到指定胶囊。
     *
     * <p>不能只调 {@code typeNav.requestFocus()}：网格可能先聚焦第 0 个可见子项，
     * 造成「分类页胶囊跳回首页、分页不切换」。这里按 adapter position 精确定位。
     */
    private boolean focusTypeChild(int target) {
        RecyclerView.ViewHolder holder = binding.typeNav.findViewHolderForAdapterPosition(target);
        if (holder == null) return false;
        boolean focused = holder.itemView.requestFocus();
        if (focused && binding.typeNav.getSelectedPosition() != target) select(target);
        return focused;
    }

    /** 搜索按钮/logo 按 ↓：优先回到离开前的胶囊，否则回到当前页对应的胶囊。 */
    private boolean focusTypeFromToolbar() {
        int target = lastTypePosition;
        if (target < 0 || target >= adapter.getItemCount()) target = host != null ? host.getTypePosition() : 0;
        if (target < 0 || target >= adapter.getItemCount()) return false;
        return focusType(target);
    }

    private int getFocusedTypePosition() {
        View focus = activity.getCurrentFocus();
        if (focus != null) {
            View item = binding.typeNav.findContainingItemView(focus);
            if (item != null) {
                int position = binding.typeNav.getChildAdapterPosition(item);
                if (position != RecyclerView.NO_POSITION) return position;
            }
        }
        return Math.max(0, binding.typeNav.getSelectedPosition());
    }

    public int getSelected() {
        return binding.typeNav.getSelectedPosition();
    }

    public void focus() {
        if (adapter.getItemCount() > 0) binding.typeNav.requestFocus();
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

    @Override
    public void onItemClick(Class item) {
        int position = adapter.indexOf(item);
        if (host != null) {
            host.onTypeClick(item, position);
            return;
        }
        if (item.isHome()) {
            backHome();
            return;
        }
        int target = adapter.hasHome() ? position - 1 : position;
        VodActivity.start(activity, getKey(), adapter.getTypes(), Math.max(0, target));
    }

    @Override
    public void onRefresh(Class item) {
        if (host != null) {
            host.onTypeRefresh(item);
            return;
        }
        onItemClick(item);
    }

    /** 供各页面把类型栏的选中变化回传给自己的 Host。 */
    public void dispatchSelected(int position) {
        if (host == null || position < 0 || position >= adapter.getItemCount()) return;
        host.onTypeSelected(adapter.get(position), position);
    }

    private String getKey() {
        Site home = VodConfig.get().getHome();
        return home == null ? "" : home.getKey();
    }

    /** 回到首页：首页自己直接结束，其余页面清栈回到已存在的首页实例。 */
    private void backHome() {
        if (activity instanceof HomeActivity) return;
        Intent intent = new Intent(activity, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        activity.startActivity(intent);
        activity.finish();
    }
}
