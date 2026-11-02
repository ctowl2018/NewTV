package com.fongmi.android.NewTV.ui.fragment;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.FocusHighlight;
import androidx.leanback.widget.HorizontalGridView;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.leanback.widget.ListRow;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.NewTV.Product;
import com.fongmi.android.NewTV.R;
import com.fongmi.android.NewTV.api.config.VodConfig;
import com.fongmi.android.NewTV.bean.Cache;
import com.fongmi.android.NewTV.bean.Filter;
import com.fongmi.android.NewTV.bean.Result;
import com.fongmi.android.NewTV.bean.Site;
import com.fongmi.android.NewTV.bean.Style;
import com.fongmi.android.NewTV.bean.Value;
import com.fongmi.android.NewTV.bean.Vod;
import com.fongmi.android.NewTV.databinding.FragmentTypeBinding;
import com.fongmi.android.NewTV.model.SiteViewModel;
import com.fongmi.android.NewTV.ui.activity.CollectActivity;
import com.fongmi.android.NewTV.ui.activity.VideoActivity;
import com.fongmi.android.NewTV.ui.activity.WebActivity;
import com.fongmi.android.NewTV.ui.base.BaseFragment;
import com.fongmi.android.NewTV.ui.custom.CustomRowPresenter;
import com.fongmi.android.NewTV.ui.custom.CustomScroller;
import com.fongmi.android.NewTV.ui.custom.CustomSelector;
import com.fongmi.android.NewTV.ui.presenter.FilterPresenter;
import com.fongmi.android.NewTV.ui.presenter.VodPresenter;
import com.fongmi.android.NewTV.utils.KeyUtil;
import com.fongmi.android.NewTV.utils.ResUtil;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class TypeFragment extends BaseFragment implements CustomScroller.Callback, VodPresenter.OnClickListener, SwipeRefreshLayout.OnRefreshListener {

    private HashMap<String, String> mExtends;
    private FragmentTypeBinding mBinding;
    private ArrayObjectAdapter mAdapter;
    private ArrayObjectAdapter mLast;
    private VodPresenter mVodPresenter;
    private CustomScroller mScroller;
    private SiteViewModel mViewModel;
    private List<Filter> mFilters;
    private boolean headerVisible;
    private boolean filterVisible;

    public static TypeFragment newInstance(String key, String typeId, Style style, HashMap<String, String> extend, boolean folder) {
        Bundle args = new Bundle();
        args.putString("key", key);
        args.putString("typeId", typeId);
        args.putBoolean("folder", folder);
        args.putParcelable("style", style);
        args.putSerializable("extend", extend);
        TypeFragment fragment = new TypeFragment();
        fragment.setArguments(args);
        return fragment;
    }

    private String getKey() {
        return getArguments().getString("key");
    }

    private String getTypeId() {
        return getArguments().getString("typeId");
    }

    private boolean isFolder() {
        return getArguments().getBoolean("folder");
    }

    private Style getStyle() {
        return isFolder() ? Style.list() : getSite().getStyle(getArguments().getParcelable("style"));
    }

    private HashMap<String, String> getExtend() {
        return (HashMap<String, String>) getArguments().getSerializable("extend");
    }

    private List<Filter> getFilter() {
        String typeId = getTypeId();
        if (!Cache.contains(typeId)) typeId = getParent().getType().getTypeId();
        return Cache.copy(typeId);
    }

    private Site getSite() {
        return VodConfig.get().getSite(getKey());
    }

    private FolderFragment getParent() {
        return ((FolderFragment) getParentFragment());
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentTypeBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        mScroller = new CustomScroller(this);
        mExtends = getExtend();
        mFilters = getFilter();
        setRecyclerView();
        setViewModel();
        setFilters();
        getVideo();
    }

    @Override
    protected void initEvent() {
        mBinding.swipeLayout.setOnRefreshListener(this);
        mBinding.recycler.addOnScrollListener(mScroller);
        // 行边界统一在这里处理：第一排海报 ↑ 去过滤器/导航栏，过滤器最上排 ↑ 回导航栏。
        mBinding.recycler.setOnKeyInterceptListener(event -> {
            if (!KeyUtil.isActionDown(event) || !KeyUtil.isUpKey(event)) return false;
            return onUpKey();
        });
    }

    @SuppressLint("RestrictedApi")
    private void setRecyclerView() {
        CustomSelector selector = new CustomSelector();
        selector.addPresenter(Vod.class, new VodPresenter(this, Style.list()));
        selector.addPresenter(ListRow.class, new CustomRowPresenter(16), VodPresenter.class);
        selector.addPresenter(ListRow.class, new CustomRowPresenter(8, FocusHighlight.ZOOM_FACTOR_NONE, HorizontalGridView.FOCUS_SCROLL_ALIGNED), FilterPresenter.class);
        mBinding.recycler.setAdapter(new ItemBridgeAdapter(mAdapter = new ArrayObjectAdapter(selector)));
        // 不再调用 setHeader(activity, R.id.recycler)：该 API 按 id 在整个 Activity 里找「顶栏」，
        // 而 activity_home 的首页内容网格同样使用 R.id.recycler，会被误当成顶栏并由
        // hideHeader() 置为 GONE —— 下滑到第二行后返回首页，首页内容就再也不显示。
        // 新版顶栏是 view_home_header 的 typeNav（常驻），本来也不需要随网格滚动隐藏。
        mBinding.recycler.setVerticalSpacing(ResUtil.dp2px(16));
        // 返回键交给 Activity 统一分层处理（海报→胶囊→退出），网格不再自己消费 BACK 去滚顶部。
        mBinding.recycler.setMoveTop(false);
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.getResult().observe(getViewLifecycleOwner(), this::setAdapter);
        mViewModel.getAction().observe(getViewLifecycleOwner(), result -> WebActivity.handleAction(requireActivity(), result));
    }

    private void setFilters() {
        for (Filter filter : mFilters) if (mExtends.containsKey(filter.getKey())) filter.setSelected(mExtends.get(filter.getKey()));
    }

    private void setClick(ArrayObjectAdapter adapter, String key, Value item) {
        for (int i = 0; i < adapter.size(); i++) ((Value) adapter.get(i)).setSelected(item);
        adapter.notifyArrayItemRangeChanged(0, adapter.size());
        if (item.isSelected()) mExtends.put(key, item.getV());
        else mExtends.remove(key);
        onRefresh();
    }

    private void getVideo() {
        mLast = null;
        checkFilter();
        mScroller.reset();
        getVideo(getTypeId(), "1");
    }

    private void getVideo(String typeId, String page) {
        mViewModel.categoryContent(getKey(), typeId, page, true, mExtends);
    }

    private void setAdapter(Result result) {
        boolean first = mScroller.first();
        boolean flag = mExtends.isEmpty();
        int size = result.getList().size();
        mBinding.progressLayout.showContent(first & flag, size);
        mBinding.swipeLayout.setRefreshing(false);
        mScroller.endLoading(result);
        if (size > 0) addVideo(result);
    }

    private void addVideo(Result result) {
        Style style = result.getStyle(getStyle());
        if (style.isList()) mAdapter.addAll(mAdapter.size(), result.getList());
        else addGrid(result.getList(), style);
        checkMore();
    }

    private void checkMore() {
        if (mScroller.isDisable() || mAdapter.size() >= 5) return;
        mScroller.checkMore();
    }

    private boolean checkLastSize(List<Vod> items, Style style) {
        if (mLast == null || items.isEmpty()) return false;
        int size = Product.getColumn(style) - mLast.size();
        if (size == 0) return false;
        size = Math.min(size, items.size());
        mLast.addAll(mLast.size(), items.subList(0, size));
        addGrid(items.subList(size, items.size()), style);
        return true;
    }

    private void addGrid(List<Vod> items, Style style) {
        if (mVodPresenter == null || !mVodPresenter.matches(style)) {
            mVodPresenter = new VodPresenter(this, style);
            mLast = null;
        }
        if (checkLastSize(items, style)) return;
        List<ListRow> rows = new ArrayList<>();
        for (List<Vod> part : Lists.partition(items, Product.getColumn(style))) rows.add(createVideoRow(part));
        mAdapter.addAll(mAdapter.size(), rows);
    }

    private ListRow createVideoRow(List<Vod> items) {
        mLast = new ArrayObjectAdapter(mVodPresenter);
        mLast.addAll(0, items);
        return new ListRow(mLast);
    }

    private ListRow getRow(Filter filter) {
        FilterPresenter presenter = new FilterPresenter(filter.getKey());
        ArrayObjectAdapter adapter = new ArrayObjectAdapter(presenter);
        presenter.setOnClickListener((key, item) -> setClick(adapter, key, item));
        adapter.setItems(filter.getValue(), null);
        return new ListRow(adapter);
    }

    private void showFilter() {
        List<ListRow> rows = new ArrayList<>();
        for (Filter filter : mFilters) rows.add(getRow(filter));
        mBinding.recycler.postDelayed(() -> mBinding.recycler.scrollToPosition(0), 48);
        mAdapter.addAll(0, rows);
    }

    private void hideFilter() {
        mAdapter.removeItems(0, mFilters.size());
    }

    public void toggleFilter(boolean visible) {
        if (mFilters.isEmpty()) return;
        this.filterVisible = visible;
        if (visible) showFilter();
        else hideFilter();
    }

    private void checkFilter() {
        int adapterSize = mAdapter.size();
        int filterSize = filterSize();
        if (adapterSize > filterSize) mAdapter.removeItems(filterSize, mAdapter.size() - filterSize);
        if (adapterSize == 0) mBinding.progressLayout.showProgress();
        else mBinding.swipeLayout.setRefreshing(true);
    }

    private int filterSize() {
        return filterVisible && mFilters != null ? mFilters.size() : 0;
    }

    /** 第一排海报 / 过滤器最上排按上键的分流。 */
    private boolean onUpKey() {
        View focus = getActivity() == null ? null : getActivity().getCurrentFocus();
        if (focus == null) return false;
        View row = findRow(focus);
        if (row == null) return false;
        int position = mBinding.recycler.getChildAdapterPosition(row);
        if (position == RecyclerView.NO_POSITION) return false;
        int filterCount = filterSize();
        if (filterVisible && position < filterCount) {
            // 过滤器区域：最上一排回导航栏，其余交给默认向上。
            return position == 0 && notifyFirstRowUp();
        }
        if (position == filterCount) {
            // 第一排海报：过滤器打开时先回最近的过滤器行，否则回导航栏。
            return filterVisible ? focusRow(filterCount - 1) : notifyFirstRowUp();
        }
        return false;
    }

    private boolean notifyFirstRowUp() {
        return getParentFragment() instanceof FolderFragment && ((FolderFragment) getParentFragment()).onFirstRowUp();
    }

    private View findRow(View focused) {
        View child = focused;
        while (child != null && child.getParent() != mBinding.recycler) {
            if (!(child.getParent() instanceof View)) return null;
            child = (View) child.getParent();
        }
        return child;
    }

    private boolean focusRow(int position) {
        if (position < 0) return false;
        mBinding.recycler.scrollToPosition(position);
        View row = getRowView(position);
        if (row != null) return focusFirstChild(row);
        mBinding.recycler.post(() -> {
            View target = getRowView(position);
            if (target != null) focusFirstChild(target);
        });
        return true;
    }

    private View getRowView(int position) {
        RecyclerView.ViewHolder holder = mBinding.recycler.findViewHolderForAdapterPosition(position);
        return holder == null ? null : holder.itemView;
    }

    private boolean focusFirstChild(View row) {
        if (row == null) return false;
        if (row instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) row;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child.getVisibility() != View.VISIBLE) continue;
                if (child.isFocusable() && child.requestFocus()) return true;
            }
        }
        return row.requestFocus();
    }

    /** 胶囊 ↓：过滤器展开时先进二级分类导航（过滤器首行），否则直接进第一张海报。 */
    public boolean focusFirstPoster() {
        if (mBinding == null || mAdapter == null || mAdapter.size() == 0) return false;
        // 严格按「分类胶囊 → 二级分类导航 → 海报」的层级：过滤器展开时先落到过滤器首行，
        // 未展开时才直接进第一张海报（原实现无条件跳过过滤器，↓ 会越过二级导航）。
        int start = filterSize() > 0 ? 0 : firstPosterRow();
        // 行 + 行内第 0 个，确保是左上角第一张，而不是上次停留的那张。
        mBinding.recycler.setSelectedPositionWithSub(start, 0);
        return focusRow(start);
    }

    /** 横向切换分类：只复位选中项到左上角第一张海报，不抢焦点。 */
    public void resetToFirstPoster() {
        if (mBinding == null) return;
        mBinding.recycler.post(() -> {
            if (mAdapter == null || mAdapter.size() == 0) return;
            int start = firstPosterRow();
            mBinding.recycler.scrollToPosition(start);
            mBinding.recycler.setSelectedPositionWithSub(start, 0);
        });
    }

    private int firstPosterRow() {
        int start = filterSize();
        return start >= (mAdapter == null ? 0 : mAdapter.size()) ? 0 : start;
    }

    public void onRefresh() {
        getVideo();
    }

    public boolean moveToTop() {
        return mBinding != null && mBinding.recycler.moveToTop();
    }

    @Override
    public void onItemClick(Vod item) {
        if (item.isAction()) {
            mViewModel.action(getKey(), item.getAction());
        } else if (item.isFolder()) {
            getParent().openFolder(item.getId(), mExtends);
            headerVisible = mBinding.recycler.isHeaderVisible();
        } else {
            if (getSite().isIndex()) CollectActivity.start(requireActivity(), item.getName());
            else VideoActivity.start(requireActivity(), getKey(), item.getId(), item.getName(), item.getPic(), isFolder() ? item.getName() : null);
        }
    }

    @Override
    public boolean onLongClick(Vod item) {
        if (item.isAction() || item.isFolder()) return false;
        CollectActivity.start(requireActivity(), item.getName());
        return true;
    }

    @Override
    public boolean onLoadMore(String page) {
        getVideo(getTypeId(), page);
        return true;
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden) {
            mBinding.recycler.showHeader();
        } else {
            if (headerVisible) mBinding.recycler.showHeader();
            else mBinding.recycler.hideHeader();
            mBinding.recycler.requestFocus();
        }
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        // 切页时只复位选中项，不抢焦点；用户按 ↓ 才真正进入内容区。
        if (isVisibleToUser && mBinding != null) resetToFirstPoster();
    }
}
