package com.fongmi.android.NewTV.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;
import androidx.viewpager.widget.ViewPager;

import com.fongmi.android.NewTV.App;
import com.fongmi.android.NewTV.api.config.VodConfig;
import com.fongmi.android.NewTV.bean.Class;
import com.fongmi.android.NewTV.bean.Result;
import com.fongmi.android.NewTV.bean.Site;
import com.fongmi.android.NewTV.databinding.ActivityVodBinding;
import com.fongmi.android.NewTV.event.RefreshEvent;
import com.fongmi.android.NewTV.impl.SiteListener;
import com.fongmi.android.NewTV.ui.adapter.TypeAdapter;
import com.fongmi.android.NewTV.ui.base.BaseActivity;
import com.fongmi.android.NewTV.ui.fragment.FolderFragment;
import com.fongmi.android.NewTV.ui.helper.ContentFocusHost;
import com.fongmi.android.NewTV.ui.helper.HomeHeader;
import com.fongmi.android.NewTV.utils.KeyUtil;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.List;
import java.util.Optional;

public class VodActivity extends BaseActivity implements HomeHeader.Host, ContentFocusHost, SiteListener {

    private ActivityVodBinding mBinding;
    private HomeHeader mHeader;
    private TypeAdapter mAdapter;
    private View mOldView;
    /** 海报按返回时记住的那张海报，胶囊上按 ↓ 回到它。 */
    private View mPendingPoster;
    /** 横滑/连续选胶囊时待执行的分类切换：去抖，避免快速操作并发加载多分页。 */
    private int mPendingSwitch = -1;
    private final Runnable mSwitchTask = () -> {
        int target = mPendingSwitch;
        mPendingSwitch = -1;
        if (target >= 0) switchCategory(target);
    };

    public static void start(Activity activity, Result result) {
        start(activity, VodConfig.get().getHome().getKey(), result, 0);
    }

    public static void start(Activity activity, String key, Result result) {
        start(activity, key, result, 0);
    }

    public static void start(Activity activity, String key, Result result, int position) {
        if (result == null || result.getTypes().isEmpty()) return;
        Intent intent = new Intent(activity, VodActivity.class);
        intent.putExtra("key", key);
        intent.putExtra("result", result);
        intent.putExtra("position", position);
        activity.startActivity(intent);
    }

    /** 供顶部导航栏直接跳转使用：只需要分类列表，无完整首页数据。 */
    public static void start(Activity activity, String key, List<Class> types, int position) {
        if (types == null || types.isEmpty()) return;
        Result result = Result.empty();
        result.setTypes(types);
        start(activity, key, result, position);
    }

    private String getKey() {
        return getIntent().getStringExtra("key");
    }

    private Result getResult() {
        return getIntent().getParcelableExtra("result");
    }

    /** 首页项占用类型栏第 0 位，分页从第 1 位开始。 */
    private int getStartPosition() {
        int position = getIntent().getIntExtra("position", 0);
        return Math.max(0, Math.min(position, getResult().getTypes().size() - 1));
    }

    private int toAdapterPosition(int pagerPosition) {
        return pagerPosition + (mAdapter.hasHome() ? 1 : 0);
    }

    private int toPagerPosition(int adapterPosition) {
        return adapterPosition - (mAdapter.hasHome() ? 1 : 0);
    }

    private Class getType() {
        return mAdapter.get(toAdapterPosition(mBinding.pager.getCurrentItem()));
    }

    private FolderFragment getFragment() {
        return getFragment(mBinding.pager.getCurrentItem());
    }

    private FolderFragment getFragment(int pagerIndex) {
        if (mBinding.pager.getAdapter() == null) return null;
        if (pagerIndex < 0 || pagerIndex >= mBinding.pager.getAdapter().getCount()) return null;
        try {
            Object fragment = mBinding.pager.getAdapter().instantiateItem(mBinding.pager, pagerIndex);
            return fragment instanceof FolderFragment ? (FolderFragment) fragment : null;
        } catch (Exception e) {
            // 快速滑动导致适配器状态瞬时不一致时，放弃本次访问，避免进程崩溃。
            return null;
        }
    }

    /** 焦点是否停留在顶部导航胶囊上。 */
    private boolean isTypeBarFocused() {
        View focus = getCurrentFocus();
        if (focus == null) return false;
        if (focus == mBinding.header.typeNav) return true;
        return mBinding.header.typeNav.findContainingItemView(focus) != null;
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityVodBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        setHeader();
        setTypes();
        setPager();
    }

    @Override
    protected void initEvent() {
        mBinding.pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                int adapterPosition = toAdapterPosition(position);
                mHeader.select(adapterPosition);
                // 切页后焦点保持在导航栏，不自动进入内容区。
                mHeader.focusType(adapterPosition);
                // 新分类加载后，把海报选中项复位到左上角第一张（不抢焦点）。只在当前分页（已被 ViewPager 实例化）上操作。
                final int page = position;
                mBinding.pager.post(() -> {
                    FolderFragment folder = getFragment(page);
                    if (folder != null) folder.resetToFirstPoster();
                });
            }
        });
        mBinding.header.typeNav.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                onChildSelected(child, position);
            }
        });
    }

    private void setHeader() {
        mHeader = HomeHeader.bind(this, mBinding.header);
        mHeader.setHost(this);
        mAdapter = mHeader.getAdapter();
    }

    private void setTypes() {
        List<Class> types = getResult().getTypes();
        // 先灌入类型（addAll 会在首位插入「首页」项），再按分页位置选中，避免 home 项导致错位。
        mHeader.setTypes(types, 0);
        mHeader.select(toAdapterPosition(getStartPosition()));
        HomeHeader.publish(types);
    }

    private void setPager() {
        mBinding.pager.setAdapter(new PageAdapter(getSupportFragmentManager()));
        // initEvent 尚未注册选中监听，这里直接把分页落到目标类型，避免首页点分类进来仍停在第一页。
        mBinding.pager.setCurrentItem(getStartPosition(), false);
        int adapterPosition = toAdapterPosition(getStartPosition());
        mHeader.select(adapterPosition);
        mHeader.focusType(adapterPosition);
        // 初始化海报选中项到左上角第一张，但不抢焦点（不按 ↓ 不进入）。
        App.post(() -> {
            FolderFragment folder = getFragment();
            if (folder != null) folder.resetToFirstPoster();
        }, 120);
    }

    /**
     * 胶囊选中回调。
     *
     * <p>用回调自带的 position 而不是 {@code getSelectedPosition()}：后者在快速横移时
     * 可能还是上一项，导致分页不切换或来回跳。
     */
    private void onChildSelected(@Nullable RecyclerView.ViewHolder child, int position) {
        if (mOldView != null) mOldView.setSelected(false);
        if ((mOldView = child != null ? child.itemView : null) == null) return;
        mOldView.setSelected(true);
        // 胶囊已移到别的分类：上一页记住的海报立即作废，避免 ↓ 回到旧页。
        if (position >= 0 && position < mAdapter.getItemCount()
                && !mAdapter.get(position).isHome()
                && toPagerPosition(position) != mBinding.pager.getCurrentItem()) {
            mPendingPoster = null;
        }
        // 去抖：快速横滑/连续选胶囊时只保留最后一次，避免反复触发分页切换与并发加载。
        mPendingSwitch = position;
        mBinding.pager.removeCallbacks(mSwitchTask);
        mBinding.pager.postDelayed(mSwitchTask, 100);
    }

    private void switchCategory(int position) {
        if (isFinishing() || isDestroyed()) return;
        if (position < 0 || position >= mAdapter.getItemCount()) return;
        // 选中的是首页项：保持当前分页不动，按确认键时再返回首页。
        if (mAdapter.get(position).isHome()) return;
        if (mBinding.pager.getAdapter() == null) return;
        int pager = toPagerPosition(position);
        if (pager < 0 || pager >= mBinding.pager.getAdapter().getCount()) return;
        if (pager == mBinding.pager.getCurrentItem()) return;
        mPendingPoster = null;
        // 仅切换当前分页；真正的海报复位交给 onPageSelected（此时分页已由 ViewPager 正常实例化，
        // 不再手动 instantiateItem 非当前分页，从而避免快速滑动时并发实例化多分页引发崩溃）。
        mBinding.pager.setCurrentItem(pager);
    }

    private boolean isFilterVisible() {
        return Optional.ofNullable(getType()).map(Class::getFilter).orElse(false);
    }

    private void updateFilter() {
        Class item = getType();
        if (item != null) updateFilter(item, mBinding.pager.getCurrentItem());
    }

    /** 按「被点的那枚胶囊」定位分页再切筛选，避免焦点与分页短暂不同步时改错页。 */
    private void updateFilter(Class item, int pagerIndex) {
        FolderFragment folder = getFragment(pagerIndex);
        if (folder == null) return;
        mPendingPoster = null;
        item.setFilter(!item.getFilter());
        folder.toggleFilter(item.getFilter());
        mAdapter.notifyItemRangeChanged(mAdapter.indexOf(item), 1);
    }

    public void closeFilter() {
        if (isFilterVisible()) updateFilter();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (event.getType() == RefreshEvent.Type.CATEGORY) getFragment().onRefresh();
    }

    @Override
    public void onTypeClick(Class item, int position) {
        // 首页项：返回首页
        if (item.isHome()) {
            finish();
            return;
        }
        updateFilter(item, Math.max(0, toPagerPosition(position)));
    }

    @Override
    public void onTypeRefresh(Class item) {
        if (item.isHome()) {
            finish();
            return;
        }
        getFragment().onRefresh();
    }

    /** 胶囊 ↓：优先回到返回前记住的那张海报，否则进入当前分类第一张海报。 */
    @Override
    public boolean onTypeDown(int position) {
        if (mPendingPoster != null) {
            View target = mPendingPoster;
            mPendingPoster = null;
            if (target.isAttachedToWindow() && target.requestFocus()) return true;
        }
        FolderFragment folder = getFragment();
        return folder != null && folder.focusFirstPoster();
    }

    /** 当前页面实际对应的胶囊位置。 */
    @Override
    public int getTypePosition() {
        return toAdapterPosition(mBinding.pager.getCurrentItem());
    }

    /** 分类内第一排海报按上键：焦点回当前分类胶囊。 */
    @Override
    public boolean onFirstRowUp() {
        return focusCapsule(toAdapterPosition(mBinding.pager.getCurrentItem()));
    }

    private boolean focusCapsule(int position) {
        return mHeader != null && mHeader.focusType(position);
    }

    private boolean isContentFocused() {
        View focus = getCurrentFocus();
        if (focus == null) return false;
        View view = focus;
        while (view != null) {
            if (view == mBinding.pager) return true;
            view = view.getParent() instanceof View ? (View) view.getParent() : null;
        }
        return false;
    }

    @Override
    public void setSite(Site item) {
        VodConfig.get().setHome(item);
        // 站点切换后原有分类列表失效，回首页重新加载。
        Intent intent = new Intent(this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (KeyUtil.isMenuKey(event)) updateFilter();
        return super.dispatchKeyEvent(event);
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
    protected void onBackInvoked() {
        FolderFragment folder = getFragment();
        if (isFilterVisible()) {
            updateFilter();
        } else if (isContentFocused()) {
            // 海报返回：焦点收回当前分类胶囊，并记住这张海报；再按 ↓ 可回到它。
            View poster = getCurrentFocus();
            focusCapsule(toAdapterPosition(mBinding.pager.getCurrentItem()));
            mPendingPoster = poster;
        } else if (!isTypeBarFocused()) {
            // 焦点在品牌行：先回导航栏。
            focusCapsule(toAdapterPosition(mBinding.pager.getCurrentItem()));
        } else if (folder != null && folder.canBack()) {
            // 已在导航栏：有二级文件夹则退回上一层。
            folder.goBack();
        } else {
            super.onBackInvoked();
        }
    }

    class PageAdapter extends FragmentStatePagerAdapter {

        public PageAdapter(@NonNull FragmentManager fm) {
            super(fm);
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            Class type = mAdapter.get(toAdapterPosition(position));
            return FolderFragment.newInstance(getKey(), type);
        }

        @Override
        public int getCount() {
            return Math.max(0, mAdapter.getItemCount() - (mAdapter.hasHome() ? 1 : 0));
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
        }
    }
}
