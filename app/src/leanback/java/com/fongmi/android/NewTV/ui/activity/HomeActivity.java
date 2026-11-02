package com.fongmi.android.NewTV.ui.activity;

import android.annotation.SuppressLint;
import android.app.SearchManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.splashscreen.SplashScreen;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.FocusHighlight;
import androidx.leanback.widget.HorizontalGridView;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;
import androidx.viewpager.widget.ViewPager;

import com.fongmi.android.NewTV.App;
import com.fongmi.android.NewTV.Product;
import com.fongmi.android.NewTV.R;
import com.fongmi.android.NewTV.api.config.VodConfig;
import com.fongmi.android.NewTV.api.node.NodeRuntime;
import com.fongmi.android.NewTV.bean.Cache;
import com.fongmi.android.NewTV.bean.Class;
import com.fongmi.android.NewTV.bean.History;
import com.fongmi.android.NewTV.bean.Result;
import com.fongmi.android.NewTV.bean.Site;
import com.fongmi.android.NewTV.bean.Style;
import com.fongmi.android.NewTV.bean.Vod;
import com.fongmi.android.NewTV.databinding.ActivityHomeBinding;
import com.fongmi.android.NewTV.db.BackupManager;
import com.fongmi.android.NewTV.event.CastEvent;
import com.fongmi.android.NewTV.event.ConfigEvent;
import com.fongmi.android.NewTV.event.RefreshEvent;
import com.fongmi.android.NewTV.event.ServerEvent;
import com.fongmi.android.NewTV.impl.Callback;
import com.fongmi.android.NewTV.model.SiteViewModel;
import com.fongmi.android.NewTV.player.extractor.Source;
import com.fongmi.android.NewTV.playback.ExternalPlayback;
import com.fongmi.android.NewTV.server.Server;
import com.fongmi.android.NewTV.service.DLNARendererService;
import com.fongmi.android.NewTV.service.PlaybackService;
import com.fongmi.android.NewTV.ui.adapter.BaseDiffCallback;
import com.fongmi.android.NewTV.ui.base.BaseActivity;
import com.fongmi.android.NewTV.ui.custom.CustomRowPresenter;
import com.fongmi.android.NewTV.ui.custom.CustomSelector;
import com.fongmi.android.NewTV.ui.custom.CustomTitleView;
import com.fongmi.android.NewTV.ui.dialog.SiteDialog;
import com.fongmi.android.NewTV.ui.fragment.FolderFragment;
import com.fongmi.android.NewTV.ui.helper.ContentFocusHost;
import com.fongmi.android.NewTV.ui.helper.HomeHeader;
import com.fongmi.android.NewTV.ui.presenter.HeaderPresenter;
import com.fongmi.android.NewTV.ui.presenter.HistoryPresenter;
import com.fongmi.android.NewTV.ui.presenter.ProgressPresenter;
import com.fongmi.android.NewTV.ui.presenter.VodPresenter;
import com.fongmi.android.NewTV.utils.KeyUtil;
import com.fongmi.android.NewTV.utils.Notify;
import com.fongmi.android.NewTV.utils.PermissionUtil;
import com.fongmi.android.NewTV.utils.ResUtil;
import com.fongmi.android.NewTV.utils.Util;
import com.github.catvod.net.OkHttp;
import com.google.common.collect.Lists;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;

public class HomeActivity extends BaseActivity implements CustomTitleView.Listener, VodPresenter.OnClickListener, HistoryPresenter.OnClickListener, HomeHeader.Host, ContentFocusHost {

    private static final String STATE_ACTION_HANDLED = "action_handled";
    /** 组合标宽度占屏宽的比例（设计稿 1920x1080 中 920x75 的裁切图）。 */
    private static final float SPLASH_LOGO_WIDTH_RATIO = 0.479f;
    private static final float SPLASH_LOGO_ASPECT = 920f / 75f;
    /** 欢迎页最短展示时长：logo 出现到开始淡出至少 1 秒，避免一闪而过。 */
    private static final long SPLASH_MIN_DISPLAY = 1000L;
    /** 首页迟迟不就绪（站点异常）时的兜底时长，保证不会卡在欢迎页。 */
    private static final long SPLASH_TIMEOUT = 8000L;
    /** 首页「首页」胶囊上连按两次返回退出：两次间隔上限。 */
    private static final long BACK_EXIT_WINDOW = 2000L;
    private static volatile int instanceCount;

    public static boolean isRunning() {
        return instanceCount > 0;
    }

    private ActivityHomeBinding mBinding;
    private ArrayObjectAdapter mHistoryAdapter;
    private ArrayObjectAdapter mAdapter;
    private HistoryPresenter mPresenter;
    private VodPresenter mVodPresenter;
    private HomeHeader mHeader;
    private SiteViewModel mViewModel;
    private Intent pendingAction;
    private boolean actionReady;
    private boolean initialFocusApplied;
    private int lastTypePosition = -1;
    private Result mResult;
    /** 海报按返回时记住的那张海报，胶囊上按 ↓ 回到它。 */
    private View mPendingPoster;
    /** 欢迎页状态：logo 已出现 + 首页内容就绪，两者都满足才淡出；最少展示 2 秒、最长 8 秒兜底。 */
    private boolean splashShown;
    private boolean homeLoaded;
    private boolean splashDismissed;
    private long splashShownAt;
    /** 上次在「首页」胶囊上按返回的时刻，用于连按两次退出。 */
    private long mLastBackAt;
    /** 分类导航快速横移时的待切分页：去抖，避免逐个分类并发实例化/加载。 */
    private int mPendingCategory = -1;
    private final Runnable mCategoryTask = () -> {
        int target = mPendingCategory;
        mPendingCategory = -1;
        if (target >= 0) applyCategory(target);
    };

    private Site getHome() {
        return VodConfig.get().getHome();
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityHomeBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        pendingAction = intent;
        consumeAction();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != ExternalPlayback.REQUEST_CODE) return;
        setResult(resultCode, data);
        finish();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        instanceCount++;
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        if (savedInstanceState == null || !savedInstanceState.getBoolean(STATE_ACTION_HANDLED)) pendingAction = getIntent();
        mResult = Result.empty();
        mBinding.progressLayout.showProgress();
        initSplash(savedInstanceState);
        setHeader();
        setRecyclerView();
        setViewModel();
        setAdapter();
        setPager();
        checkPermission();
    }

    @Override
    protected void initEvent() {
        mBinding.header.title.setListener(this);
        // 首页内容第一排海报按上键：焦点回到当前分类胶囊（首页页即「首页」胶囊）。
        mBinding.recycler.setOnKeyInterceptListener(event -> {
            if (!KeyUtil.isActionDown(event) || !KeyUtil.isUpKey(event)) return false;
            View focus = getCurrentFocus();
            return focus != null && isFirstPosterRow(focus) && focusCapsule(mHeader.getSelected());
        });
        mBinding.recycler.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                if (mPresenter.isDelete()) setHistoryDelete(false);
            }
        });
        mBinding.pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                if (mHeader.getSelected() != position + 1) mHeader.select(position + 1);
                // 复位当前分页选中项到左上角第一张海报（不抢焦点）。当前分页此处已由 ViewPager 正常实例化。
                final int index = position;
                mBinding.pager.post(() -> {
                    FolderFragment folder = getFolder(index);
                    if (folder != null) folder.resetToFirstPoster();
                });
            }
        });
    }

    private void setHeader() {
        mHeader = HomeHeader.bind(this, mBinding.header);
        mHeader.setHost(this);
    }

    private void checkAction(Intent intent) {
        if (Intent.ACTION_SEND.equals(intent.getAction()) || Intent.ACTION_VIEW.equals(intent.getAction())) {
            // 直播已移除：外部推送的直播源不再处理，点播源不受影响。
            ExternalPlayback.open(this, intent, url -> { });
        } else if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            String keyword = intent.getStringExtra(SearchManager.QUERY);
            if (!TextUtils.isEmpty(keyword)) SearchActivity.start(this, keyword);
        }
    }

    private void consumeAction() {
        if (!actionReady || pendingAction == null) return;
        Intent intent = pendingAction;
        pendingAction = null;
        checkAction(intent);
    }

    @SuppressLint("RestrictedApi")
    private void setRecyclerView() {
        CustomSelector selector = new CustomSelector();
        selector.addPresenter(Integer.class, new HeaderPresenter());
        selector.addPresenter(String.class, new ProgressPresenter());
        selector.addPresenter(Vod.class, new VodPresenter(this, Style.list()));
        selector.addPresenter(ListRow.class, new CustomRowPresenter(16), VodPresenter.class);
        selector.addPresenter(ListRow.class, new CustomRowPresenter(16, FocusHighlight.ZOOM_FACTOR_SMALL, HorizontalGridView.FOCUS_SCROLL_ALIGNED), HistoryPresenter.class);
        mBinding.recycler.setAdapter(new ItemBridgeAdapter(mAdapter = new ArrayObjectAdapter(selector)));
        mBinding.recycler.setVerticalSpacing(ResUtil.dp2px(16));
    }

    private void setPager() {
        mBinding.pager.setAdapter(new PageAdapter(getSupportFragmentManager()));
        mBinding.pager.setOffscreenPageLimit(1);
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.getAction().observe(this, result -> WebActivity.handleAction(this, result));
        mViewModel.getResult().observe(this, this::onResult);
    }

    private void onResult(Result result) {
        mAdapter.remove("progress");
        mResult = result;
        setTypes();
        addVideo(result);
        // 分类为空时这里才会把初始焦点退回内容区（有分类时已在 setTypes 落到首页胶囊）。
        setFocus();
        Cache.clear().put(result);
        // 首页数据已就绪：欢迎页等淡入播完后自动淡出
        onHomeLoaded();
    }

    private void setTypes() {
        List<Class> types = mResult.getTypes();
        int current = mHeader.getSelected();
        // 分类列表变化（换源/刷新）：上一轮记住的海报已失效。
        clearPendingPoster();
        HomeHeader.publish(types);
        mHeader.setTypes(types, 0);
        if (mBinding.pager.getAdapter() != null) mBinding.pager.getAdapter().notifyDataSetChanged();
        // 刷新数据时保留当前位置：仍在同一个分类页，不把用户踢回首页
        if (current > 0 && current < mHeader.getAdapter().getItemCount()) mHeader.select(current);
        else showHome();
        // 冷启动时分类列表可能晚于内容到达，这里补一次「首页胶囊」初始焦点。
        setFocus();
    }

    private void setAdapter() {
        mHistoryAdapter = new ArrayObjectAdapter(mPresenter = new HistoryPresenter(this));
        mAdapter.add(R.string.home_history);
        mAdapter.add(R.string.home_recommend);
    }

    private void checkPermission() {
        PermissionUtil.requestLocalNetwork(this, this::onNetworkPermissionResult);
    }

    private void onNetworkPermissionResult(boolean allGranted) {
        PermissionUtil.requestNotify(this);
        if (allGranted) DLNARendererService.start(this);
        initConfig();
        mHeader.setTitle();
        mHeader.onRefreshLogo();
    }

    private void initConfig() {
        VodConfig.get().init().load(getCallback());
    }

    private Callback getCallback() {
        return new Callback() {
            @Override
            public void success() {
                onConfigReady(true);
            }

            @Override
            public void error(String msg) {
                Notify.show(msg);
                onConfigReady(false);
            }
        };
    }

    private void onConfigReady(boolean ok) {
        actionReady = true;
        showContent();
        // 配置就没加载成功：不会有首页数据，欢迎页不必再等
        if (!ok) onHomeLoaded();
        consumeAction();
    }

    private void showContent() {
        mBinding.progressLayout.showContent();
        setFocus();
    }

    // ------------------------------------------------------------ 启动欢迎页

    /** 首帧前准备欢迎页：按屏宽等比定尺寸，logo 直接以全透明度呈现（无淡入动画）。 */
    private void initSplash(Bundle savedInstanceState) {
        // 界面重建（切分辨率 / 深色模式等）不再重播欢迎页
        if (savedInstanceState != null) {
            splashDismissed = true;
            mBinding.splashLayer.setVisibility(View.GONE);
            return;
        }
        applySplashLogoSize();
        // 取消 logo 淡入动画：直接全透明显示，但保留「首页加载完成才消失、最少展示 2 秒」的时长控制。
        mBinding.splashLogo.setAlpha(1f);
        splashShown = true;
        splashShownAt = SystemClock.elapsedRealtime();
        dismissSplashIfReady();
        // 兜底：站点异常导致首页始终不就绪时，也保证能进入首页（最长 8 秒）
        App.post(() -> {
            homeLoaded = true;
            dismissSplashIfReady();
        }, SPLASH_TIMEOUT);
    }

    /** 组合标宽度取屏宽的 47.9%（设计稿 1920 宽里 920 宽），高度按原图比例，任何分辨率都不变形。 */
    private void applySplashLogoSize() {
        int width = Math.round(getResources().getDisplayMetrics().widthPixels * SPLASH_LOGO_WIDTH_RATIO);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, Math.round(width / SPLASH_LOGO_ASPECT));
        params.gravity = Gravity.CENTER;
        mBinding.splashLogo.setLayoutParams(params);
    }

    /** 首页内容加载完成（成功/失败/空结果都会走到）。 */
    private void onHomeLoaded() {
        homeLoaded = true;
        dismissSplashIfReady();
    }

    /** logo 已出现且首页已加载才淡出：展示时长由首页加载耗时动态决定，但至少 2 秒。 */
    private void dismissSplashIfReady() {
        if (splashDismissed || !splashShown || !homeLoaded) return;
        splashDismissed = true;
        long hold = SPLASH_MIN_DISPLAY - (SystemClock.elapsedRealtime() - splashShownAt);
        App.post(this::hideSplash, Math.max(0L, hold));
    }

    private void hideSplash() {
        if (mBinding.splashLayer.getVisibility() != View.VISIBLE) return;
        mBinding.splashLayer.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction(() -> mBinding.splashLayer.setVisibility(View.GONE))
                .start();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putBoolean(STATE_ACTION_HANDLED, pendingAction == null);
        super.onSaveInstanceState(outState);
    }

    /**
     * 冷启动初始焦点：默认落在「首页」胶囊。
     *
     * <p>分类列表可能晚于内容到达，因此本方法会在 {@code showContent()} 与
     * {@code setTypes()} 各调用一次，只生效一次；列表为空时才退回首页内容。
     */
    private void setFocus() {
        // 站点名称只作展示、不参与焦点，避免「海报上按上键」时被它抢走焦点
        mBinding.header.title.setSelected(true);
        if (initialFocusApplied) return;
        if (mHeader.getAdapter().getItemCount() > 0) {
            initialFocusApplied = true;
            mHeader.focusType(Math.max(0, mHeader.getSelected()));
        } else if (hasPoster()) {
            initialFocusApplied = true;
            focusHomeFirstPoster();
        }
    }

    /** 首页内容里是否已经有海报行（只看 ListRow，跳过历史/推荐文字标题行）。 */
    private boolean hasPoster() {
        if (mAdapter == null) return false;
        for (int i = 0; i < mAdapter.size(); i++) if (mAdapter.get(i) instanceof ListRow) return true;
        return false;
    }

    private void getVideo() {
        mResult = Result.empty();
        int index = getRecommendIndex();
        boolean gone = mAdapter.indexOf("progress") == -1;
        boolean hasItem = gone && mAdapter.size() > index;
        if (hasItem) mAdapter.removeItems(index, mAdapter.size() - index);
        if (gone) mAdapter.add("progress");
        mViewModel.homeContent();
    }

    private void addVideo(Result result) {
        Style style = result.getStyle(getHome().getStyle());
        if (style.isList()) mAdapter.addAll(mAdapter.size(), result.getList());
        else addGrid(result.getList(), style);
    }

    private void addGrid(List<Vod> items, Style style) {
        List<ListRow> rows = new ArrayList<>();
        if (mVodPresenter == null || !mVodPresenter.matches(style)) mVodPresenter = new VodPresenter(this, style);
        for (List<Vod> part : Lists.partition(items, Product.getColumn(style))) rows.add(createVideoRow(part));
        mAdapter.addAll(mAdapter.size(), rows);
    }

    private ListRow createVideoRow(List<Vod> items) {
        ArrayObjectAdapter adapter = new ArrayObjectAdapter(mVodPresenter);
        adapter.addAll(0, items);
        return new ListRow(adapter);
    }

    private void getHistory() {
        getHistory(false);
    }

    private void getHistory(boolean renew) {
        List<History> items = History.get();
        int historyIndex = getHistoryIndex();
        int recommendIndex = getRecommendIndex();
        boolean exist = recommendIndex - historyIndex == 2;
        if (renew) mHistoryAdapter = new ArrayObjectAdapter(mPresenter = new HistoryPresenter(this));
        if ((items.isEmpty() && exist) || (renew && exist)) mAdapter.removeItems(historyIndex, 1);
        if ((!items.isEmpty() && !exist) || (renew && exist)) mAdapter.add(historyIndex, new ListRow(mHistoryAdapter));
        mHistoryAdapter.setItems(items, new BaseDiffCallback<History>());
    }

    private void setHistoryDelete(boolean delete) {
        mPresenter.setDelete(delete);
        mHistoryAdapter.notifyArrayItemRangeChanged(0, mHistoryAdapter.size());
    }

    private void clearHistory() {
        mAdapter.removeItems(getHistoryIndex(), 1);
        History.clear(VodConfig.getCid());
        mPresenter.setDelete(false);
        mHistoryAdapter.clear();
    }

    private int getHistoryIndex() {
        return mAdapter.indexOf(R.string.home_history) + 1;
    }

    private int getRecommendIndex() {
        return mAdapter.indexOf(R.string.home_recommend) + 1;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConfigEvent(ConfigEvent event) {
        if (event.type() != ConfigEvent.Type.VOD) return;
        RefreshEvent.history();
        RefreshEvent.home();
        mHeader.onRefreshLogo();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        switch (event.getType()) {
            case HOME:
                getVideo();
                mHeader.setTitle();
                break;
            case HISTORY:
                getHistory();
                break;
            case SIZE:
                getVideo();
                getHistory(true);
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onServerEvent(ServerEvent event) {
        switch (event.type()) {
            case SEARCH:
                SearchActivity.start(this, event.text());
                break;
            case PUSH:
                VideoActivity.push(this, event.text());
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onCastEvent(CastEvent event) {
        if (VodConfig.get().getConfig().equals(event.config())) VideoActivity.cast(this, event.history());
        else VodConfig.load(event.config(), getCallback(event));
    }

    private Callback getCallback(CastEvent event) {
        return new Callback() {
            @Override
            public void success() {
                onCastEvent(event);
            }

            @Override
            public void error(String msg) {
                Notify.show(msg);
            }
        };
    }

    /** 首页类型栏：焦点横向移动到某个分类即切换内容，无需确认键。 */
    @Override
    public void onTypeSelected(Class item, int position) {
        boolean changed = position != lastTypePosition;
        lastTypePosition = position;
        if (position <= 0 || item == null || item.isHome()) {
            if (changed) clearPendingPoster();
            showHome();
            return;
        }
        if (changed) clearPendingPoster();
        // 去抖：长按左右快速移动时只对最终停留的分类切换分页，
        // 避免逐个分类 showCategory + 手动实例化非当前页，导致多个分类页并发加载而崩溃。
        mPendingCategory = position - 1;
        mBinding.pager.removeCallbacks(mCategoryTask);
        mBinding.pager.postDelayed(mCategoryTask, 100);
    }

    private void applyCategory(int index) {
        if (isFinishing() || isDestroyed()) return;
        if (mBinding.pager.getAdapter() == null) return;
        if (index < 0 || index >= mBinding.pager.getAdapter().getCount()) return;
        // 只切换当前分页；海报复位交给 onPageSelected（此时分页已由 ViewPager 正常实例化）。
        showCategory(index);
    }

    /** 胶囊 ↓：优先回到返回前记住的那张海报，否则进入当前分类第一张海报。 */
    @Override
    public boolean onTypeDown(int position) {
        if (restorePendingPoster()) return true;
        if (position <= 0) return focusHomeFirstPoster();
        FolderFragment folder = getFolder(position - 1);
        return folder != null && folder.focusFirstPoster();
    }

    /** 当前页面实际对应的胶囊：首页=「首页」项，分类页=当前分页+1。 */
    @Override
    public int getTypePosition() {
        return mHeader == null ? 0 : Math.max(0, mHeader.getSelected());
    }

    /** 分类内第一排海报按上键：焦点回当前分类胶囊。 */
    @Override
    public boolean onFirstRowUp() {
        return focusCapsule(mHeader.getSelected());
    }

    private boolean focusCapsule(int position) {
        return mHeader != null && mHeader.focusType(position);
    }

    private boolean restorePendingPoster() {
        if (mPendingPoster == null) return false;
        View target = mPendingPoster;
        mPendingPoster = null;
        return target.isAttachedToWindow() && target.requestFocus();
    }

    private void clearPendingPoster() {
        mPendingPoster = null;
    }

    /** 首页内容：进入第一排海报（跳过历史/推荐文字标题行）。 */
    private boolean focusHomeFirstPoster() {
        if (mAdapter == null) return false;
        for (int i = 0; i < mAdapter.size(); i++) {
            if (mAdapter.get(i) instanceof ListRow) {
                mBinding.recycler.setSelectedPositionWithSub(i, 0);
                return mBinding.recycler.requestFocus();
            }
        }
        return false;
    }

    private boolean isContentFocused() {
        View focus = getCurrentFocus();
        if (focus == null) return false;
        return isDescendant(focus, mBinding.recycler) || isDescendant(focus, mBinding.pager);
    }

    /** 当前是否处于首页（而非分类分页）：首页时分类分页隐藏。 */
    private boolean isHomePage() {
        return mBinding.pager.getVisibility() != View.VISIBLE;
    }

    private boolean isTypeBarFocused() {
        View focus = getCurrentFocus();
        if (focus == null) return false;
        if (focus == mBinding.header.typeNav) return true;
        return mBinding.header.typeNav.findContainingItemView(focus) != null;
    }

    private boolean isDescendant(View child, View parent) {
        View view = child;
        while (view != null) {
            if (view == parent) return true;
            view = view.getParent() instanceof View ? (View) view.getParent() : null;
        }
        return false;
    }

    /** 焦点是否停在内容区的第一排海报（上方没有任何海报行）。 */
    private boolean isFirstPosterRow(View focused) {
        View row = findRow(focused);
        if (row == null) return false;
        int position = mBinding.recycler.getChildAdapterPosition(row);
        if (position == RecyclerView.NO_POSITION || position >= mAdapter.size()) return false;
        if (!(mAdapter.get(position) instanceof ListRow)) return false;
        for (int i = 0; i < position; i++) {
            if (mAdapter.get(i) instanceof ListRow) return false;
        }
        return true;
    }

    private View findRow(View focused) {
        View child = focused;
        while (child != null && child.getParent() != mBinding.recycler) {
            if (!(child.getParent() instanceof View)) return null;
            child = (View) child.getParent();
        }
        return child;
    }

    @Override
    public void onTypeClick(Class item, int position) {
        if (position <= 0 || item == null || item.isHome()) {
            showHome();
            return;
        }
        // 焦点已经停在该分类上：确认键用于打开/收起二级分级筛选
        if (mHeader.getSelected() != position) {
            showCategory(position - 1);
            return;
        }
        toggleFilter(item, position);
    }

    private FolderFragment getFolder(int pagerIndex) {
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

    private void toggleFilter(Class item, int adapterPosition) {
        FolderFragment folder = getFolder(adapterPosition - 1);
        if (folder == null) return;
        clearPendingPoster();
        item.setFilter(!item.getFilter());
        folder.toggleFilter(item.getFilter());
        mHeader.getAdapter().notifyItemRangeChanged(adapterPosition, 1);
    }

    /** 当前分类是否展开了二级筛选。 */
    private boolean isFilterVisible() {
        int position = mHeader.getSelected();
        if (position <= 0 || position >= mHeader.getAdapter().getItemCount()) return false;
        return mHeader.getAdapter().get(position).getFilter();
    }

    private void closeFilter() {
        int position = mHeader.getSelected();
        if (position <= 0 || position >= mHeader.getAdapter().getItemCount()) return;
        Class item = mHeader.getAdapter().get(position);
        if (!item.getFilter()) return;
        toggleFilter(item, position);
    }

    @Override
    public void onTypeRefresh(Class item) {
        if (item.isHome()) {
            getVideo();
            return;
        }
        int position = mHeader.getAdapter().indexOf(item);
        if (position <= 0 || mBinding.pager.getAdapter() == null) return;
        Object fragment = mBinding.pager.getAdapter().instantiateItem(mBinding.pager, position - 1);
        if (fragment instanceof FolderFragment) ((FolderFragment) fragment).onRefresh();
    }

    private void showHome() {
        mBinding.pager.setVisibility(View.GONE);
        // 兜底：内容态下回首页必须保证首页网格可见，避免被分页里的滚动逻辑误置 GONE 后一片空白。
        if (mBinding.progressLayout.isContent()) mBinding.recycler.setVisibility(View.VISIBLE);
        mBinding.progressLayout.setVisibility(View.VISIBLE);
    }

    private void showCategory(int index) {
        if (mBinding.pager.getAdapter() == null) return;
        if (index < 0 || index >= mBinding.pager.getAdapter().getCount()) return;
        mBinding.progressLayout.setVisibility(View.GONE);
        mBinding.pager.setVisibility(View.VISIBLE);
        if (mBinding.pager.getCurrentItem() != index) mBinding.pager.setCurrentItem(index);
    }

    @Override
    public void onItemClick(Vod item) {
        if (item.isAction()) mViewModel.action(getHome().getKey(), item.getAction());
        else if (getHome().isIndex()) CollectActivity.start(this, item.getName());
        else VideoActivity.start(this, getHome().getKey(), item.getId(), item.getName(), item.getPic());
    }

    @Override
    public boolean onLongClick(Vod item) {
        if (item.isAction()) return false;
        CollectActivity.start(this, item.getName());
        return true;
    }

    @Override
    public void onItemClick(History item) {
        VideoActivity.start(this, item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic());
    }

    @Override
    public void onItemDelete(History item) {
        mHistoryAdapter.remove(item.delete());
        if (mHistoryAdapter.size() > 0) return;
        mAdapter.removeItems(getHistoryIndex(), 1);
        mPresenter.setDelete(false);
    }

    @Override
    public boolean onLongClick() {
        if (mPresenter.isDelete()) clearHistory();
        else setHistoryDelete(true);
        return true;
    }

    @Override
    public void showDialog() {
        SiteDialog.create().show(this);
    }

    @Override
    public void onRefresh() {
        getVideo();
    }

    @Override
    public void setSite(Site item) {
        VodConfig.get().setHome(item);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (KeyUtil.isMenuKey(event)) showDialog();
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
        if (mBinding.progressLayout.isProgress()) {
            showContent();
        } else if (mPresenter.isDelete()) {
            setHistoryDelete(false);
        } else if (isFilterVisible()) {
            closeFilter();
        } else if (isContentFocused()) {
            // 海报返回：焦点收回当前分类胶囊，并记住这张海报；再按 ↓ 可回到它。
            View poster = getCurrentFocus();
            focusCapsule(mHeader.getSelected());
            mPendingPoster = poster;
        } else if (!isTypeBarFocused()) {
            // 焦点在品牌行（logo / 搜索 / 收藏 / 连接 / 设置）：先回导航栏。
            focusCapsule(mHeader.getSelected());
        } else if (mBinding.pager.getVisibility() == View.VISIBLE) {
            // 分类胶囊返回：回到「首页」。
            clearPendingPoster();
            mHeader.select(0);
            showHome();
        } else if (isHomePage() && mHeader.getSelected() == 0 && isTypeBarFocused()) {
            // 首页页且焦点停在「首页」胶囊：连按两次返回退出应用，先提示、2 秒内再按一次退出
            long now = SystemClock.elapsedRealtime();
            if (now - mLastBackAt <= BACK_EXIT_WINDOW) {
                finish();
            } else {
                mLastBackAt = now;
                Notify.show(R.string.back_exit_hint);
            }
        } else {
            if (PlaybackService.isRunning()) Util.moveToBackground(this);
            else super.onBackInvoked();
        }
    }

    @Override
    protected void onDestroy() {
        if (--instanceCount == 0) {
            BackupManager.backup();
            releaseResources();
        }
        super.onDestroy();
    }

    public static void releaseResources() {
        if (isRunning() || PlaybackService.isRunning()) return;
        DLNARendererService.stop(App.get());
        VodConfig.get().clear();
        OkHttp.get().clear();
        Source.get().exit();
        Server.get().stop();
        NodeRuntime.get().releaseIfUnused();
    }

    class PageAdapter extends FragmentStatePagerAdapter {

        public PageAdapter(@NonNull FragmentManager fm) {
            super(fm);
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            List<Class> types = mHeader.getAdapter().getTypes();
            int index = Math.max(0, Math.min(position, types.size() - 1));
            return FolderFragment.newInstance(VodConfig.get().getHome().getKey(), types.get(index));
        }

        @Override
        public int getCount() {
            return mHeader.getAdapter().getTypes().size();
        }

        @Override
        public int getItemPosition(@NonNull Object object) {
            // 切换源后分类列表会变化，强制重建分页，避免残留旧分类内容。
            return POSITION_NONE;
        }
    }
}
