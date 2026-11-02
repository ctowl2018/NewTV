package com.fongmi.android.NewTV.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;
import androidx.viewpager.widget.ViewPager;

import com.fongmi.android.NewTV.App;
import com.fongmi.android.NewTV.Product;
import com.fongmi.android.NewTV.R;
import com.fongmi.android.NewTV.api.config.VodConfig;
import com.fongmi.android.NewTV.bean.Collect;
import com.fongmi.android.NewTV.bean.Result;
import com.fongmi.android.NewTV.bean.Site;
import com.fongmi.android.NewTV.databinding.ActivityCollectBinding;
import com.fongmi.android.NewTV.model.SiteViewModel;
import com.fongmi.android.NewTV.setting.Setting;
import com.fongmi.android.NewTV.ui.adapter.CollectAdapter;
import com.fongmi.android.NewTV.ui.base.BaseActivity;
import com.fongmi.android.NewTV.ui.fragment.CollectFragment;
import com.fongmi.android.NewTV.ui.helper.ToolbarHeader;
import com.fongmi.android.NewTV.utils.KeyUtil;
import com.fongmi.android.NewTV.utils.ResUtil;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;

public class CollectActivity extends BaseActivity {

    private ActivityCollectBinding mBinding;
    private ToolbarHeader mHeader;
    private CollectAdapter mAdapter;
    private SiteViewModel mViewModel;
    private List<Site> mSites;
    private View mOldView;
    /** 搜索进行中：用于忽略旧结果，并在全部站点返回后判定默认焦点落位。 */
    private boolean searching;
    /** 默认焦点只落一次：有结果显示第一个海报，无结果显示站点分类第一行（「全部」）。 */
    private boolean initialFocusApplied;

    public static void start(Activity activity, String keyword) {
        Intent intent = new Intent(activity, CollectActivity.class);
        intent.putExtra("keyword", keyword);
        activity.startActivity(intent);
    }

    private CollectFragment getFragment() {
        return getFragment(0);
    }

    private CollectFragment getFragment(int position) {
        if (mBinding.pager.getAdapter() == null) return null;
        if (position < 0 || position >= mBinding.pager.getAdapter().getCount()) return null;
        Object fragment = mBinding.pager.getAdapter().instantiateItem(mBinding.pager, position);
        return fragment instanceof CollectFragment ? (CollectFragment) fragment : null;
    }

    private String getKeyword() {
        return getIntent().getStringExtra("keyword");
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityCollectBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        getIntent().putExtras(intent);
        mAdapter.clear();
        setPager();
        search();
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mHeader = ToolbarHeader.bind(this, mBinding.toolbar);
        setRecyclerView();
        // 左侧站点导航宽度按「单列海报宽」动态设置（屏宽 / 首页列数）。
        mBinding.recycler.post(this::setNavWidth);
        setViewModel();
        saveKeyword();
        setSites();
        setPager();
        search();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mHeader.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mHeader.onPause();
    }

    @Override
    protected void initEvent() {
        mBinding.pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                // 只同步导航选中项，不抢焦点：焦点由初始落位规则（有结果→第一个海报 / 空→「全部」）决定。
                mBinding.recycler.setSelectedPosition(position);
                // 切换站点后：海报选中项复位到第一张，但不抢焦点（不按右键不进海报区，也不高亮任何海报）。
                CollectFragment fragment = getFragment(position);
                if (fragment != null) fragment.resetToFirstPoster();
            }
        });
        mBinding.recycler.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                onChildSelected(child);
            }
        });
        // 站点列表按右键：进入当前站点的海报列表，从已初始化的第一张开始（左右方向逻辑对称、互不干扰）。
        mBinding.recycler.setOnKeyInterceptListener(event -> {
            if (!KeyUtil.isActionDown(event) || !KeyUtil.isRightKey(event)) return false;
            CollectFragment fragment = getFragment(mBinding.pager.getCurrentItem());
            return fragment != null && fragment.enterPoster();
        });
    }

    private void setRecyclerView() {
        // VerticalGridView 的纵向尺寸由内容决定，列宽由网格宽度/列数推导（此处单列=导航容器宽度）。
        mBinding.recycler.setHorizontalSpacing(ResUtil.dp2px(16));
        mBinding.recycler.setVerticalSpacing(ResUtil.dp2px(16));
        mBinding.recycler.setAdapter(mAdapter = new CollectAdapter());
    }

    /** 左侧站点导航宽度 = 单列海报宽度（屏宽 / 首页列数），使其与右侧海报列等宽。 */
    private void setNavWidth() {
        int columns = Math.max(2, Product.getColumn());
        int navWidth = ResUtil.getScreenWidth() / columns;
        ViewGroup.LayoutParams lp = mBinding.recycler.getLayoutParams();
        if (lp == null) return;
        lp.width = navWidth;
        mBinding.recycler.setLayoutParams(lp);
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.getSearch().observe(this, this::onSearchResult);
    }

    /** 单个站点的搜索结果：首个非空结果到达即把默认焦点落到第一个海报。 */
    private void onSearchResult(Result result) {
        if (!searching) return;
        if (result == null || result.getList().isEmpty()) return;
        CollectFragment fragment = getFragment();
        if (fragment != null) fragment.addVideo(result.getList());
        mAdapter.add(Collect.create(result.getList()));
        if (mBinding.pager.getAdapter() != null) mBinding.pager.getAdapter().notifyDataSetChanged();
        if (!initialFocusApplied) focusFirstPoster();
    }

    /** 全部站点返回后：仍没有海报即结果为空，默认焦点落到站点分类第一行（「全部」）。 */
    private void onSearchComplete() {
        if (!searching) return;
        searching = false;
        if (initialFocusApplied) return;
        CollectFragment fragment = getFragment();
        if (fragment != null && fragment.hasPoster()) focusFirstPoster();
        else focusSiteNav();
    }

    /** 结果非空：默认焦点落到第一个海报。 */
    private void focusFirstPoster() {
        // 用户已切到其它站点：初始落位不再生效，避免把焦点抢回首页分页的海报。
        if (mBinding.pager.getCurrentItem() != 0) {
            initialFocusApplied = true;
            return;
        }
        CollectFragment fragment = getFragment();
        if (fragment == null || !fragment.hasPoster()) return;
        initialFocusApplied = true;
        mBinding.pager.post(fragment::focusFirstPoster);
    }

    /** 结果为空（或从海报左滑）：焦点落到当前站点分类胶囊（初始为空时即「全部」）。 */
    public boolean focusSiteNav() {
        if (mAdapter == null || mAdapter.getItemCount() == 0) return false;
        initialFocusApplied = true;
        int position = Math.max(0, Math.min(mBinding.pager.getCurrentItem(), mAdapter.getItemCount() - 1));
        mBinding.recycler.setSelectedPosition(position);
        return mBinding.recycler.requestFocus();
    }

    private void saveKeyword() {
        List<String> items = Setting.getKeyword().isEmpty() ? new ArrayList<>() : App.gson().fromJson(Setting.getKeyword(), TypeToken.getParameterized(List.class, String.class).getType());
        items.remove(getKeyword());
        items.add(0, getKeyword());
        if (items.size() > 9) items.remove(9);
        Setting.putKeyword(App.gson().toJson(items));
    }

    private void setSites() {
        mSites = VodConfig.get().getSites().stream().filter(Site::isSearchable).toList();
    }

    private void setPager() {
        mBinding.pager.setAdapter(new PageAdapter(getSupportFragmentManager()));
    }

    private void search() {
        if (mSites.isEmpty()) return;
        searching = true;
        initialFocusApplied = false;
        mAdapter.add(Collect.all());
        mBinding.pager.getAdapter().notifyDataSetChanged();
        mBinding.toolbar.title.setText(getString(R.string.collect_result, getKeyword()));
        mViewModel.searchContent(mSites, getKeyword(), false, () -> runOnUiThread(this::onSearchComplete));
    }

    private void onChildSelected(@Nullable RecyclerView.ViewHolder child) {
        if (mOldView != null) mOldView.setSelected(false);
        if ((mOldView = child != null ? child.itemView : null) == null) return;
        mOldView.setSelected(true);
        App.post(mRunnable, 100);
    }

    private final Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            mBinding.pager.setCurrentItem(mBinding.recycler.getSelectedPosition());
        }
    };

    @Override
    protected void onBackInvoked() {
        mViewModel.stopSearch();
        super.onBackInvoked();
    }

    class PageAdapter extends FragmentStatePagerAdapter {

        public PageAdapter(@NonNull FragmentManager fm) {
            super(fm);
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            return CollectFragment.newInstance(getKeyword(), mAdapter.get(position));
        }

        @Override
        public int getCount() {
            return mAdapter.getItemCount();
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
        }

        @Nullable
        @Override
        public Parcelable saveState() {
            return null;
        }

        @Override
        public void restoreState(@Nullable Parcelable state, @Nullable ClassLoader loader) {
        }
    }
}
