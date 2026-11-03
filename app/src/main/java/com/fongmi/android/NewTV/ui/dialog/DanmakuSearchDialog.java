package com.fongmi.android.NewTV.ui.dialog;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.media3.common.MediaMetadata;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.NewTV.App;
import com.fongmi.android.NewTV.R;
import com.fongmi.android.NewTV.api.DanmakuApi;
import com.fongmi.android.NewTV.bean.Danmaku;
import com.fongmi.android.NewTV.bean.DanmakuTitle;
import com.fongmi.android.NewTV.databinding.DialogDanmakuSearchBinding;
import com.fongmi.android.NewTV.impl.DanmakuListener;
import com.fongmi.android.NewTV.player.PlayerManager;
import com.fongmi.android.NewTV.setting.DanmakuSetting;
import com.fongmi.android.NewTV.ui.adapter.DanmakuSearchAdapter;
import com.fongmi.android.NewTV.ui.adapter.FilterChipAdapter;
import com.fongmi.android.NewTV.ui.custom.SpaceItemDecoration;
import com.fongmi.android.NewTV.utils.KeyUtil;
import com.fongmi.android.NewTV.utils.Notify;
import com.fongmi.android.NewTV.utils.ResUtil;
import com.fongmi.android.NewTV.utils.Util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 弹幕搜索弹窗。
 *
 * <p><b>为什么能筛选</b>：接口返回的 {@code name} 是一条结构化复合串
 * （{@code 剧名(年份)【类型】from 数据源 - 【平台】 集数 分集标题}），
 * {@link DanmakuTitle} 把它拆成「类型」与「平台」两个稳定的 key，
 * 本弹窗据此提供两行筛选胶囊：
 * <ul>
 *   <li><b>类型</b>：剧集 / 电影 / 动漫 / 综艺 / 纪录 / 其他；</li>
 *   <li><b>来源</b>：爱奇艺 / 腾讯视频 / 优酷 / 哔哩哔哩 / 咪咕 / 芒果TV …（弹幕真正来自哪个平台）。</li>
 * </ul>
 * 两个维度是**本地过滤**，不重新请求接口：一次搜索的全部结果留在内存里，切筛选只重建列表，
 * 因此来回切换没有网络等待。胶囊上的数字按全量结果统计、不随另一个维度联动，
 * 保证切换时胶囊行宽度稳定、当前选中的胶囊也不会因为联动变 0 而消失。
 *
 * <p>筛选行只在「当前结果里确实存在多个可选值」时才显示，单一类型的片子（例如整页都是同一部剧）
 * 不会白占两行高度。
 */
public final class DanmakuSearchDialog extends BaseBottomSheetDialog implements DanmakuListener {

    /** 「全部」用一个不可能与真实 key 冲突的空串表示。 */
    private static final String KEY_ALL = "";

    private final List<Danmaku> results = new ArrayList<>();
    private final List<Danmaku> visible = new ArrayList<>();

    private DialogDanmakuSearchBinding binding;
    private DanmakuSearchAdapter adapter;
    private FilterChipAdapter typeAdapter;
    private FilterChipAdapter sourceAdapter;
    private PlayerManager player;
    private String typeKey = KEY_ALL;
    private String sourceKey = KEY_ALL;
    private boolean keywordInitialized;
    private boolean autoSearch;

    public DanmakuSearchDialog() {
    }

    public static DanmakuSearchDialog create() {
        return new DanmakuSearchDialog();
    }

    /** 打开后用预填的关键词立即搜索一次（控制条弹幕浮层的放大镜入口）。 */
    public DanmakuSearchDialog autoSearch() {
        this.autoSearch = true;
        return this;
    }

    public void show(FragmentActivity activity) {
        FragmentManager manager = activity.getSupportFragmentManager();
        if (manager.isStateSaved()) return;
        for (Fragment fragment : manager.getFragments()) if (fragment instanceof DanmakuSearchDialog) return;
        show(manager, null);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        keywordInitialized = savedInstanceState != null && savedInstanceState.getBoolean("keywordInitialized");
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("keywordInitialized", keywordInitialized);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogDanmakuSearchBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        adapter = new DanmakuSearchAdapter(this::onItemClick);
        binding.recycler.setAdapter(adapter);
        binding.recycler.setItemAnimator(null);
        binding.recycler.setHasFixedSize(false);
        binding.recycler.addItemDecoration(new SpaceItemDecoration(1, 6));

        typeAdapter = new FilterChipAdapter(this::onTypeSelected);
        binding.typeFilter.setAdapter(typeAdapter);
        binding.typeFilter.setItemAnimator(null);
        sourceAdapter = new FilterChipAdapter(this::onSourceSelected);
        binding.sourceFilter.setAdapter(sourceAdapter);
        binding.sourceFilter.setItemAnimator(null);

        PlaybackDialog.observe(this, this::bindPlayer);
    }

    private void bindPlayer(PlayerManager player) {
        if (this.player != null && player == null) {
            DanmakuApi.cancel();
            showEmpty(adapter.getItemCount() == 0);
        }
        this.player = player;
        if (player == null || keywordInitialized) return;
        binding.keyword.setText(getTitle());
        binding.keyword.setSelection(binding.keyword.length());
        keywordInitialized = true;
        if (autoSearch) {
            autoSearch = false;
            if (!getKeyword().isEmpty()) App.post(this::search);
        } else {
            Util.showKeyboard(binding.keyword);
        }
    }

    @Override
    protected void initEvent() {
        binding.setting.setOnClickListener(this::onSetting);
        binding.search.setOnClickListener(view -> {
            if (!getKeyword().isEmpty()) search();
        });
        binding.keyword.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH && !getKeyword().isEmpty()) search();
            return true;
        });
        // 输入框按 ↓ 先落到筛选行（不可用时直接进结果列表），键盘只用于输入、不参与导航
        binding.keyword.setOnKeyListener((view, keyCode, event) -> {
            if (!KeyUtil.isActionDown(event) || !KeyUtil.isDownKey(event)) return false;
            return focusBelowKeyword();
        });
    }

    private boolean focusAboveResults() {
        if (binding.typeRow.getVisibility() == VISIBLE) return binding.typeFilter.requestFocus();
        if (binding.sourceRow.getVisibility() == VISIBLE) return binding.sourceFilter.requestFocus();
        return false;
    }

    private boolean focusBelowKeyword() {
        if (focusAboveResults()) return true;
        if (binding.recycler.getVisibility() == VISIBLE && adapter.getItemCount() > 0) return binding.recycler.requestFocus();
        return false;
    }

    private void onSetting(View view) {
        DanmakuApiDialog.show(this);
    }

    private CharSequence getTitle() {
        MediaMetadata metadata = player.getMetadata();
        return metadata == null || TextUtils.isEmpty(metadata.title) ? "" : metadata.title;
    }

    private String getEpisode() {
        MediaMetadata metadata = player.getMetadata();
        return metadata == null || TextUtils.isEmpty(metadata.artist) ? "" : metadata.artist.toString().trim();
    }

    private String getKeyword() {
        return binding.keyword.getText().toString().trim();
    }

    // ------------------------------------------------------------------ 搜索

    private void search() {
        if (!PlaybackDialog.isCurrentPlayer(this, player)) return;
        Util.hideKeyboard(binding.keyword);
        progress(true);
        DanmakuApi.search(getKeyword(), getEpisode(), this::onSuccess, this::onError);
    }

    private void onSuccess(List<Danmaku> items) {
        results.clear();
        results.addAll(items);
        // 新一次搜索回到「全部」，避免上一轮的筛选项把结果全过滤掉
        typeKey = KEY_ALL;
        sourceKey = KEY_ALL;
        buildFilters();
        progress(false);
        if (items.isEmpty()) Notify.show(R.string.error_empty);
        applyFilter(true);
    }

    private void onError(Exception e) {
        results.clear();
        buildFilters();
        progress(false);
        applyFilter(false);
        Notify.show(TextUtils.isEmpty(e.getMessage()) ? ResUtil.getString(R.string.error_empty) : e.getMessage());
    }

    private void progress(boolean running) {
        binding.progress.setVisibility(running ? VISIBLE : GONE);
        if (running) {
            binding.recycler.setVisibility(GONE);
            binding.empty.setVisibility(GONE);
            results.clear();
            buildFilters();
        }
    }

    // ------------------------------------------------------------------ 筛选

    private void onTypeSelected(String key) {
        if (typeKey.equals(key)) return;
        typeKey = key;
        typeAdapter.select(key);
        applyFilter(false);
    }

    private void onSourceSelected(String key) {
        if (sourceKey.equals(key)) return;
        sourceKey = key;
        sourceAdapter.select(key);
        applyFilter(false);
    }

    /** 按全量结果统计出两行胶囊；某一维度只有一种取值时整行隐藏。 */
    private void buildFilters() {
        Map<String, Integer> groups = new HashMap<>();
        Map<String, Integer> sources = new LinkedHashMap<>();
        for (Danmaku item : results) {
            DanmakuTitle meta = item.getMeta();
            groups.merge(meta.getGroup(), 1, Integer::sum);
            String source = meta.getSourceKey();
            if (!source.isEmpty()) sources.merge(source, 1, Integer::sum);
        }

        List<FilterChipAdapter.Item> typeItems = new ArrayList<>();
        typeItems.add(new FilterChipAdapter.Item(KEY_ALL, ResUtil.getString(R.string.danmaku_filter_all), results.size()));
        for (String group : DanmakuTitle.GROUP_ORDER) {
            Integer count = groups.remove(group);
            if (count != null) typeItems.add(new FilterChipAdapter.Item(group, ResUtil.getString(DanmakuTitle.groupLabel(group)), count));
        }
        for (Map.Entry<String, Integer> entry : groups.entrySet()) {
            typeItems.add(new FilterChipAdapter.Item(entry.getKey(), ResUtil.getString(DanmakuTitle.groupLabel(entry.getKey())), entry.getValue()));
        }
        typeAdapter.submit(typeItems, typeKey);

        List<FilterChipAdapter.Item> sourceItems = new ArrayList<>();
        sourceItems.add(new FilterChipAdapter.Item(KEY_ALL, ResUtil.getString(R.string.danmaku_filter_all), results.size()));
        for (String key : DanmakuTitle.SOURCE_ORDER) {
            Integer count = sources.remove(key);
            if (count != null) sourceItems.add(new FilterChipAdapter.Item(key, sourceName(key), count));
        }
        for (Map.Entry<String, Integer> entry : sources.entrySet()) {
            sourceItems.add(new FilterChipAdapter.Item(entry.getKey(), sourceName(entry.getKey()), entry.getValue()));
        }
        sourceAdapter.submit(sourceItems, sourceKey);

        // 「全部 + 仅一种取值」时筛选没有意义，整行收起来
        boolean showType = typeAdapter.size() > 2;
        boolean showSource = sourceAdapter.size() > 2;
        binding.typeRow.setVisibility(showType ? VISIBLE : GONE);
        binding.sourceRow.setVisibility(showSource ? VISIBLE : GONE);
        relink(showType, showSource);
    }

    private String sourceName(String key) {
        int label = DanmakuTitle.sourceLabel(key);
        return label == 0 ? key : ResUtil.getString(label);
    }

    /** 搜索行按 ↓ 要落到「当前可见的第一个筛选行」，否则会跳过筛选直奔列表。 */
    private void relink(boolean showType, boolean showSource) {
        int target = showType ? binding.typeFilter.getId() : showSource ? binding.sourceFilter.getId() : binding.recycler.getId();
        binding.keyword.setNextFocusDownId(target);
        binding.search.setNextFocusDownId(target);
    }

    private void applyFilter(boolean focusResults) {
        visible.clear();
        for (Danmaku item : results) {
            DanmakuTitle meta = item.getMeta();
            if (!KEY_ALL.equals(typeKey) && !typeKey.equals(meta.getGroup())) continue;
            if (!KEY_ALL.equals(sourceKey) && !sourceKey.equals(meta.getSourceKey())) continue;
            visible.add(item);
        }
        adapter.submit(visible);
        updateSummary();
        binding.divider.setVisibility(results.isEmpty() ? GONE : VISIBLE);
        showEmpty(visible.isEmpty());
        if (!visible.isEmpty()) {
            // 用 LayoutManager 的滚动而不是 CustomRecyclerView 的重载：后者会顺手把焦点抢到条目上
            if (binding.recycler.getLayoutManager() != null) binding.recycler.getLayoutManager().scrollToPosition(0);
            if (focusResults) binding.recycler.requestFocus();
        }
    }

    private void updateSummary() {
        int total = results.size();
        int shown = visible.size();
        if (total == 0) {
            binding.summary.setText("");
        } else if (shown == total) {
            binding.summary.setText(ResUtil.getString(R.string.danmaku_search_count, total));
        } else {
            binding.summary.setText(ResUtil.getString(R.string.danmaku_search_count_filtered, shown, total));
        }
    }

    private void showEmpty(boolean empty) {
        binding.progress.setVisibility(GONE);
        binding.recycler.setVisibility(empty ? GONE : VISIBLE);
        binding.empty.setVisibility(empty ? VISIBLE : GONE);
        if (empty) binding.empty.setText(results.isEmpty() ? R.string.danmaku_search_empty : R.string.danmaku_search_empty_filter);
    }

    // ------------------------------------------------------------------ 选择

    private void onItemClick(Danmaku item) {
        if (!PlaybackDialog.isCurrentPlayer(this, player)) return;
        player.setDanmaku(item);
        dismiss();
    }

    @Override
    public void setDanmakuApi(String url) {
        DanmakuSetting.putApiUrl(url);
        if (!getKeyword().isEmpty() && !TextUtils.isEmpty(DanmakuSetting.getEffectiveApiUrl())) search();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        player = null;
        DanmakuApi.cancel();
        super.onDestroyView();
    }
}
