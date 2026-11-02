package com.fongmi.android.NewTV.ui.fragment;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.HorizontalGridView;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.leanback.widget.ListRow;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.NewTV.Product;
import com.fongmi.android.NewTV.R;
import com.fongmi.android.NewTV.bean.Collect;
import com.fongmi.android.NewTV.bean.Result;
import com.fongmi.android.NewTV.bean.Vod;
import com.fongmi.android.NewTV.databinding.FragmentTypeBinding;
import com.fongmi.android.NewTV.model.SiteViewModel;
import com.fongmi.android.NewTV.ui.activity.CollectActivity;
import com.fongmi.android.NewTV.ui.activity.VideoActivity;
import com.fongmi.android.NewTV.ui.activity.VodActivity;
import com.fongmi.android.NewTV.ui.activity.WebActivity;
import com.fongmi.android.NewTV.ui.base.BaseFragment;
import com.fongmi.android.NewTV.ui.custom.CustomRowPresenter;
import com.fongmi.android.NewTV.ui.custom.CustomScroller;
import com.fongmi.android.NewTV.ui.custom.CustomSelector;
import com.fongmi.android.NewTV.ui.presenter.VodPresenter;
import com.fongmi.android.NewTV.utils.KeyUtil;
import com.fongmi.android.NewTV.utils.ResUtil;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.List;

public class CollectFragment extends BaseFragment implements CustomScroller.Callback, VodPresenter.OnClickListener {

    private FragmentTypeBinding mBinding;
    private ArrayObjectAdapter mAdapter;
    private ArrayObjectAdapter mLast;
    private CustomScroller mScroller;
    private SiteViewModel mViewModel;
    private Collect mCollect;
    private String mKeyword;

    public static CollectFragment newInstance(String keyword, Collect collect) {
        Bundle args = new Bundle();
        args.putString("keyword", keyword);
        CollectFragment fragment = new CollectFragment().setCollect(collect);
        fragment.setArguments(args);
        return fragment;
    }

    private String getKeyword() {
        return mKeyword = mKeyword == null ? getArguments().getString("keyword") : mKeyword;
    }

    private CollectFragment setCollect(Collect collect) {
        this.mCollect = collect;
        return this;
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentTypeBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        setRecyclerView();
        setViewModel();
        addVideo(mCollect);
    }

    private void setRecyclerView() {
        CustomSelector selector = new CustomSelector();
        selector.addPresenter(ListRow.class, new CustomRowPresenter(16), VodPresenter.class);
        mBinding.recycler.setAdapter(new ItemBridgeAdapter(mAdapter = new ArrayObjectAdapter(selector)));
        mBinding.recycler.addOnScrollListener(mScroller = new CustomScroller(this));
        // 搜索结果页标题已移至顶部工具栏，不再作为海报网格的吸顶表头。
        mBinding.recycler.setVerticalSpacing(ResUtil.dp2px(16));
        // 左右键只负责「站点列表 ↔ 海报列表」的进出，且只在边缘列接管，行内移动仍交给网格：
        // 第一列 ← 站点列表；最后一列 → 消费按键，避免冒泡到 ViewPager 切换站点（与左侧行为对称）。
        mBinding.recycler.setOnKeyInterceptListener(event -> {
            if (!KeyUtil.isActionDown(event)) return false;
            HorizontalGridView row = focusedRow();
            if (row == null) return false;
            int column = focusedColumn(row);
            if (column < 0) return false;
            if (KeyUtil.isLeftKey(event)) return column == 0 && focusSiteNav();
            if (KeyUtil.isRightKey(event)) {
                int count = row.getAdapter() == null ? 0 : row.getAdapter().getItemCount();
                return count > 0 && column >= count - 1;
            }
            return false;
        });
    }

    /** 当前聚焦海报所在行的横向网格（行内列号由它判断）。 */
    private HorizontalGridView focusedRow() {
        View focus = getActivity() == null ? null : getActivity().getCurrentFocus();
        View view = focus;
        while (view != null) {
            if (view instanceof HorizontalGridView) return (HorizontalGridView) view;
            view = view.getParent() instanceof View ? (View) view.getParent() : null;
        }
        return null;
    }

    /** 当前聚焦海报在该行内的列号（0 = 第一列；-1 = 无法判断）。 */
    private int focusedColumn(HorizontalGridView row) {
        View focus = getActivity() == null ? null : getActivity().getCurrentFocus();
        if (focus == null) return -1;
        View item = focus;
        while (item != null && item.getParent() != row) {
            if (!(item.getParent() instanceof View)) return -1;
            item = (View) item.getParent();
        }
        if (item == null) return -1;
        int column = row.getChildAdapterPosition(item);
        return column < 0 ? row.getSelectedPosition() : column;
    }

    /** 切换站点后：海报选中项复位到左上角第一张，但不抢焦点（不按右键不进入，也不高亮任何海报）。 */
    public void resetToFirstPoster() {
        if (mBinding == null) return;
        mBinding.recycler.post(() -> {
            if (mAdapter == null || mAdapter.size() == 0) return;
            mBinding.recycler.scrollToPosition(0);
            mBinding.recycler.setSelectedPositionWithSub(0, 0);
        });
    }

    /** 站点列表按右键：进入海报列表，落在已初始化的第一张海报上。 */
    public boolean enterPoster() {
        if (mBinding == null || mAdapter == null || mAdapter.size() == 0) return false;
        if (mBinding.recycler.requestFocus()) return true;
        // 网格尚未布局完成：吃掉这次按键，下一帧再补一次焦点。
        mBinding.recycler.post(() -> mBinding.recycler.requestFocus());
        return true;
    }

    /** 是否已有海报行（用于决定默认焦点落在海报还是站点导航）。 */
    public boolean hasPoster() {
        return mAdapter != null && mAdapter.size() > 0;
    }

    /** 默认焦点落到第一个海报（左上角第一张）。 */
    public void focusFirstPoster() {
        if (mBinding == null) return;
        mBinding.recycler.post(() -> {
            if (mAdapter == null || mAdapter.size() == 0) return;
            mBinding.recycler.setSelectedPositionWithSub(0, 0);
            if (!mBinding.recycler.requestFocus()) mBinding.recycler.post(() -> mBinding.recycler.requestFocus());
        });
    }

    /** 把焦点移交给宿主：当前站点分类胶囊。 */
    private boolean focusSiteNav() {
        if (!(getActivity() instanceof CollectActivity)) return false;
        return ((CollectActivity) getActivity()).focusSiteNav();
    }

    /** 搜索结果海报每行数量：始终比首页/分类页少 1。 */
    private int getColumn() {
        return Math.max(1, Product.getColumn() - 1);
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.getAction().observe(getViewLifecycleOwner(), result -> WebActivity.handleAction(requireActivity(), result));
        mViewModel.getResult().observe(this, result -> {
            mScroller.endLoading(result);
            addVideo(result.getList());
        });
    }

    private boolean checkLastSize(List<Vod> items) {
        if (mLast == null || items.isEmpty()) return false;
        int size = getColumn() - mLast.size();
        if (size == 0) return false;
        size = Math.min(size, items.size());
        mLast.addAll(mLast.size(), items.subList(0, size));
        addVideo(items.subList(size, items.size()));
        return true;
    }

    private void addVideo(Collect collect) {
        if (collect != null) addVideo(collect.getList());
    }

    public void addVideo(List<Vod> items) {
        if (checkLastSize(items) || getActivity() == null || getActivity().isFinishing()) return;
        List<ListRow> rows = new ArrayList<>();
        VodPresenter presenter = new VodPresenter(this);
        for (List<Vod> part : Lists.partition(items, getColumn())) rows.add(createVideoRow(part, presenter));
        mAdapter.addAll(mAdapter.size(), rows);
    }

    private ListRow createVideoRow(List<Vod> items, VodPresenter presenter) {
        mLast = new ArrayObjectAdapter(presenter);
        mLast.addAll(0, items);
        return new ListRow(mLast);
    }

    @Override
    public void onItemClick(Vod item) {
        if (item.isAction()) {
            mViewModel.action(item.getSiteKey(), item.getAction());
            return;
        }
        requireActivity().setResult(Activity.RESULT_OK);
        if (item.isFolder()) VodActivity.start(requireActivity(), item.getSiteKey(), Result.folder(item));
        else VideoActivity.collect(requireActivity(), item.getSiteKey(), item.getId(), item.getName(), item.getPic());
    }

    @Override
    public boolean onLongClick(Vod item) {
        return false;
    }

    @Override
    public boolean onLoadMore(String page) {
        if (mCollect == null || "all".equals(mCollect.getSite().getKey())) return false;
        mViewModel.searchContent(mCollect.getSite(), getKeyword(), false, page);
        return true;
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (mBinding != null && !isVisibleToUser) mBinding.recycler.moveToTop();
    }
}
