package com.fongmi.android.NewTV.ui.activity;

import android.annotation.SuppressLint;
import android.app.SearchManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
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
import com.fongmi.android.NewTV.api.TmdbApi;
import com.fongmi.android.NewTV.api.TmdbStore;
import com.fongmi.android.NewTV.api.config.VodConfig;
import com.fongmi.android.NewTV.api.node.NodeRuntime;
import com.fongmi.android.NewTV.bean.Cache;
import com.fongmi.android.NewTV.bean.Class;
import com.fongmi.android.NewTV.bean.History;
import com.fongmi.android.NewTV.bean.HomeBand;
import com.fongmi.android.NewTV.bean.Result;
import com.fongmi.android.NewTV.bean.Site;
import com.fongmi.android.NewTV.bean.Style;
import com.fongmi.android.NewTV.bean.TmdbArt;
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
import com.fongmi.android.NewTV.setting.TmdbSetting;
import com.fongmi.android.NewTV.ui.adapter.BaseDiffCallback;
import com.fongmi.android.NewTV.ui.base.BaseActivity;
import com.fongmi.android.NewTV.ui.custom.CustomRowPresenter;
import com.fongmi.android.NewTV.ui.custom.CustomSelector;
import com.fongmi.android.NewTV.ui.custom.CustomTitleView;
import com.fongmi.android.NewTV.ui.custom.HeroFade;
import com.fongmi.android.NewTV.ui.dialog.SiteDialog;
import com.fongmi.android.NewTV.ui.fragment.FolderFragment;
import com.fongmi.android.NewTV.ui.helper.ContentFocusHost;
import com.fongmi.android.NewTV.ui.helper.HomeHeader;
import com.fongmi.android.NewTV.ui.presenter.HeaderPresenter;
import com.fongmi.android.NewTV.ui.presenter.HistoryPresenter;
import com.fongmi.android.NewTV.ui.presenter.HomeBandPresenter;
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
    /** 首页右上角背景图宽度占屏宽的比例；高度按原图比例自动换算，各尺寸屏幕视觉比例一致。 */
    private static final float HERO_WIDTH_RATIO = 0.744f;
    /**
     * 「最近观看」**海报顶部**（不是板块标题）与右上角背景图下边界的重合高度：100px。
     *
     * <p>留白带由列表的第一个占位项（{@link HomeBand}）撑起，高度 =
     * 背景图底边 − 该重合量 − 「标题顶 → 海报顶」的实测高度差 − 网格起点，
     * 在布局完成后写入。它是列表的一部分，所以上翻时会被一起带出屏幕，
     * 下面的海报行立刻接上，顶部不会残留一条静止的空白。</p>
     *
     * <p>用物理像素而非 dp：这是「海报压住背景图多少」的实际重叠量，与屏幕密度无关
     * （与 {@link #HERO_LOGO_OVERLAP_PX} 同一口径）。</p>
     */
    private static final int HERO_POSTER_OVERLAP_PX = 100;
    /**
     * 留白带高度暂时算不出来时（标题 / 海报行还没排出来）的逐帧重试上限。
     *
     * <p>留白带高度依赖「标题顶 → 海报顶」的实测值，启动早期这一行可能还没布局；
     * 若就此放弃，列表会停在留白带高度 0 的位置，直到某次布局（用户滚动、焦点移动）
     * 才突然补上 —— 表现出来就是「板块要靠平移才能到达目标位置」。
     * 这里改成主动重试：页面加载完就是最终位置，与任何滚动 / 交互无关。</p>
     */
    private static final int BAND_RETRY_LIMIT = 12;
    /**
     * logo 相对留白带的淡出比例：留白带滚走 {@code 0.6 × 高度} 时 logo 已完全透明。
     *
     * <p>让 logo 先于海报「消失」而不是被海报从中间切一刀：片名始终完整地淡出，
     * 不会出现半个字压在透明图上、半个字压在卡片上的割裂画面。</p>
     */
    private static final float LOGO_FADE_RATIO = 0.6f;
    /** 欢迎页最短展示时长：logo 出现到开始淡出至少 1 秒，避免一闪而过。 */
    private static final long SPLASH_MIN_DISPLAY = 1000L;
    /** 首页迟迟不就绪（站点异常）时的兜底时长，保证不会卡在欢迎页。 */
    private static final long SPLASH_TIMEOUT = 8000L;
    /** 首页「首页」胶囊上连按两次返回退出：两次间隔上限。 */
    private static final long BACK_EXIT_WINDOW = 2000L;
    /** 行内海报的横向间距（dp）：「最近观看」4.5 个海报的行宽也按这个值推算。 */
    private static final int ROW_SPACING_DP = 16;
    /**
     * 片名 logo 右边缘越过右上角背景图左边界的距离（物理像素）。
     * 用 px 而非 dp：这里描述的是两张图的实际重叠量，与密度适配无关。
     */
    private static final int HERO_LOGO_OVERLAP_PX = 300;
    /**
     * logo 画布的逻辑尺寸（dp），与 activity_home.xml 中 homeLogo 的基准尺寸保持一致。
     *
     * <p>所有 logo 都画在同一尺寸的画布上，再由画布按 FIT_CENTER 缩放到控件里：
     * TransitionDrawable 的固有尺寸只由画布决定，不会随素材宽高比变化，也不会
     * 继承同一次转场里另一张 logo 的尺寸。</p>
     */
    private static final int LOGO_CANVAS_DP_WIDTH = 306;
    private static final int LOGO_CANVAS_DP_HEIGHT = 119;

    private static volatile int instanceCount;

    public static boolean isRunning() {
        return instanceCount > 0;
    }

    private ActivityHomeBinding mBinding;
    private ArrayObjectAdapter mHistoryAdapter;
    private ArrayObjectAdapter mAdapter;
    private HistoryPresenter mPresenter;
    private HomeBandPresenter mBandPresenter;
    /** 「区块标题顶部 → 第一张海报顶部」的实测高度差（px）：标题高度 + 标题与海报之间的行距。 */
    private int mSectionGap;
    /** 留白带高度重试计数：每次真正的布局由全局布局回调清零。 */
    private int mBandRetry;
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
    /** 当前右上角背景图对应的 TMDB 缓存文件名；null = 内置默认海报墙。 */
    private String mHeroArt;
    /** 当前片名 logo 对应的 TMDB 缓存文件名；null = 无 logo（淡出到透明）。 */
    private String mHeroLogo;
    /** logo 的基准尺寸（XML 首帧测量值）：溢出自适应缩小后仍以此为基准，避免逐次递减。 */
    private int mLogoBaseWidth = -1;
    private int mLogoBaseHeight = -1;
    /** 冷启动首次读取历史记录时，把初始焦点落到第一张海报；只生效一次。 */
    private boolean mInitialHistoryFocusPending = true;
    /** 后台抓取完成后刷回当前焦点海报；字段强引用，配合 TmdbApi 内的弱引用使用。 */
    private final Runnable mTmdbRefresh = () -> {
        View focus = getCurrentFocus();
        if (findHistory(focus) != null) updateHeroFor(focus);
    };
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
        setupHomeHero();
        setupHomeBand();
        setupHeroFocus();
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
        selector.addPresenter(HomeBand.class, mBandPresenter = new HomeBandPresenter());
        selector.addPresenter(Integer.class, new HeaderPresenter());
        selector.addPresenter(String.class, new ProgressPresenter());
        selector.addPresenter(Vod.class, new VodPresenter(this, Style.list()));
        selector.addPresenter(ListRow.class, new CustomRowPresenter(ROW_SPACING_DP), VodPresenter.class);
        mPresenter = new HistoryPresenter(this);
        // 「最近观看」行宽固定为 4.5 个海报：左边起点仍贴着列表内边距，只把右边界收进来。
        CustomRowPresenter historyRow = new CustomRowPresenter(ROW_SPACING_DP, FocusHighlight.ZOOM_FACTOR_SMALL, HorizontalGridView.FOCUS_SCROLL_ALIGNED);
        historyRow.setRowWidth(mPresenter.getRowWidth(ResUtil.dp2px(ROW_SPACING_DP)));
        selector.addPresenter(ListRow.class, historyRow, HistoryPresenter.class);
        mBinding.recycler.setAdapter(new ItemBridgeAdapter(mAdapter = new ArrayObjectAdapter(selector)));
        // 行间距 16dp → 8dp：首页「区块标题 ↔ 海报」之间的空白只有这一层，收紧后标题更贴海报。
        mBinding.recycler.setVerticalSpacing(ResUtil.dp2px(8));
        // 背景图 / logo 的淡出进度跟着列表滚动更新：内容上翻压过来时两张图已退场，不会被生硬切边。
        mBinding.recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                updateHeroScroll();
            }
        });
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
        // 导航栏至少有「首页」胶囊，初始焦点落在胶囊上；极端情况下没有胶囊才退回内容区。
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
        mHistoryAdapter = new ArrayObjectAdapter(mPresenter);
        // 列表第一项：顶部留白带（占位）。高度在布局完成后由 applyHomeBand() 写入。
        mAdapter.add(new HomeBand());
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
        // 先量留白带再显示内容：首帧就是最终位置，不靠之后的布局回调或滚动来「补正」。
        mBandRetry = 0;
        applyHomeBand();
        setFocus();
    }

    // ------------------------------------------------------------ 右上角背景图与首页板块定位

    /** 首页右上角背景图：按屏宽比例定尺寸，右上角贴边；左侧/底部羽化由控件自身完成。 */
    private void setupHomeHero() {
        mBinding.homeHero.load(R.drawable.home_hero, HERO_WIDTH_RATIO);
    }

    /**
     * 首页顶部留白带的定位与刷新。
     *
     * <p>留白带是列表的第一项（{@link HomeBand}），不是容器的 margin，也不在网格 padding 里：
     * 只有「真正的列表项」才会随内容一起滚动。做成 padding / margin 时，网格视口的上边界仍停在原处，
     * 海报一滚到边界就被裁掉，上面永远空着一条 —— 这正是之前「上翻后大片留白」的根因。</p>
     *
     * <p>定位基准是**海报顶部**而不是板块标题：留白带只补足
     * 「海报目标位置 − 标题与海报之间的实测高度差 − 网格起点」这一截，
     * 标题仍按列表原有的行距排在它上方，标题与海报的相对间距完全不动。</p>
     *
     * <p>**必须在页面加载完成前就把高度算出来**：高度一旦算出来就写在列表的第一项上，
     * 位置由列表排版本身给出，不涉及任何运行时的位移 / 平移，也不依赖之后的滚动事件。
     * 因此「标题 / 海报行还没排出来」时会主动逐帧重试（见 {@link #BAND_RETRY_LIMIT}），
     * 而不是等下一次布局（用户滚动、焦点移动）才补上 —— 否则首屏会停在留白带高度为 0 的位置，
     * 看起来就是「要靠滚动把板块带下来」。</p>
     */
    private void setupHomeBand() {
        // 每次真正的布局都重置重试预算：数据后到、换屏都会重新量一轮。
        mBinding.getRoot().getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            mBandRetry = 0;
            applyHomeBand();
        });
    }

    private void applyHomeBand() {
        if (mBinding.progressLayout.getVisibility() != View.VISIBLE) return;
        boolean pending = true;
        if (mBandPresenter != null && mBinding.homeHero.getHeight() > 0) {
            int gap = measureSectionGap();
            if (gap >= 0) {
                pending = false;
                mSectionGap = gap;
                int[] hero = new int[2];
                int[] box = new int[2];
                mBinding.homeHero.getLocationInWindow(hero);
                mBinding.recycler.getLocationInWindow(box);
                // 目标：海报顶部落在「背景图底边 − 重合量」处（窗口坐标）。
                int target = hero[1] + mBinding.homeHero.getHeight() - HERO_POSTER_OVERLAP_PX;
                mBandPresenter.setBandHeight(target - gap - (box[1] + mBinding.recycler.getPaddingTop()));
            }
        }
        // logo 的位置同样依赖运行时实测；放在提前返回之外，避免「已对齐」时漏更新。
        applyLogoPlacement();
        updateHeroScroll();
        // 标题 / 海报行还没排出来（首帧、数据未到、刚被回收）：下一帧接着量。
        // 这条重试是「初始位置」的保证：绝不能把生效时机交给用户滚动或其它交互。
        if (pending && mBandRetry < BAND_RETRY_LIMIT) {
            mBandRetry++;
            mBinding.recycler.postOnAnimation(this::applyHomeBand);
        }
    }

    /**
     * 实测「区块标题顶部 → 第一张海报顶部」的高度差：标题自身高度 + 标题与海报行之间的行距。
     *
     * <p>两个视图是同一次布局里的兄弟节点，测的是**相对距离**，与列表滚动位置无关，
     * 所以留白带高度不会随滚动反复变化；换字体缩放、改标题样式也会自动跟随。
     * 历史为空时第一行是「更新推荐」，同一套口径照样成立。</p>
     *
     * @return 高度差（px）；标题或海报行尚未布局 / 已滚出被回收时返回 -1。
     */
    private int measureSectionGap() {
        RecyclerView.LayoutManager manager = mBinding.recycler.getLayoutManager();
        if (manager == null) return -1;
        int title = getHistoryIndex() - 1;
        View titleView = manager.findViewByPosition(title);
        if (titleView == null) return -1;
        for (int i = title + 1; i < mAdapter.size(); i++) {
            if (!(mAdapter.get(i) instanceof ListRow)) continue;
            View row = manager.findViewByPosition(i);
            if (row == null) return -1;
            return row.getTop() - titleView.getTop();
        }
        return -1;
    }

    /**
     * 背景图与 logo 的淡出进度：跟着留白带滚出视口一起退场。
     *
     * <p>两者都排在内容之下（层级见 activity_home.xml），上翻时海报会压过来；
     * 与其让海报从半张片名中间切过去，不如按滚动距离先淡出：logo 在留白带走完
     * {@link #LOGO_FADE_RATIO} 时透明，背景图走满时透明，视觉上是一组自然的让位。</p>
     */
    private void updateHeroScroll() {
        int band = mBandPresenter == null ? 0 : mBandPresenter.getBandHeight();
        if (band <= 0) return;
        View first = mBinding.recycler.getLayoutManager() == null ? null
                : mBinding.recycler.getLayoutManager().findViewByPosition(0);
        // 留白带被回收即代表已完全滚出视口，按最大位移处理。
        int offset = first == null ? band : Math.max(0, mBinding.recycler.getPaddingTop() - first.getTop());
        mBinding.homeLogo.setAlpha(Math.max(0f, Math.min(1f, 1f - offset / (band * LOGO_FADE_RATIO))));
        mBinding.homeHero.setAlpha(Math.max(0f, Math.min(1f, 1f - offset / (float) band)));
    }

    /**
     * 片名 logo 定位：垂直中心落在「导航栏底边 ↔ 首张海报顶部上方的区块标题」这段留白区的正中；
     * 右边缘越过背景图左边界 {@link #HERO_LOGO_OVERLAP_PX} px（跨过左侧羽化带）。
     *
     * <p>若按基准尺寸摆放会顶出屏幕左边界，就保持右边缘不动、等比缩小宽高，
     * 让左边缘正好贴住屏幕：既不裁切也不变形。基准尺寸只取第一次测量值，
     * 避免每次布局回调都在上一次缩小结果上继续缩。</p>
     *
     * <p>海报顶部直接由背景图底边反推（底边 − 重合量），标题顶再由实测高度差反推，
     * 与留白带完全同源，因此留白带高度变化时 logo 会自动跟着重新居中。</p>
     */
    private void applyLogoPlacement() {
        View logo = mBinding.homeLogo;
        View hero = mBinding.homeHero;
        if (logo.getHeight() <= 0 || hero.getWidth() <= 0 || mBinding.header.typeNav.getHeight() <= 0
                || mBinding.progressLayout.getHeight() <= 0) return;
        if (mLogoBaseWidth <= 0) {
            mLogoBaseWidth = logo.getWidth();
            mLogoBaseHeight = logo.getHeight();
        }
        int[] root = new int[2];
        int[] heroPos = new int[2];
        int[] nav = new int[2];
        mBinding.getRoot().getLocationInWindow(root);
        hero.getLocationInWindow(heroPos);
        mBinding.header.typeNav.getLocationInWindow(nav);
        // 垂直：留白区中点 = (导航栏底边 + 首个内容「最近观看」标题顶) / 2。
        // 标题顶由「海报顶部目标 − 实测的标题↔海报高度差」反推，与留白带同源；
        // 高度差还没测出来时退回按海报顶居中，至少不会把 logo 甩到屏幕外。
        int navBottom = nav[1] + mBinding.header.typeNav.getHeight();
        int posterTop = heroPos[1] + hero.getHeight() - HERO_POSTER_OVERLAP_PX;
        int center = (navBottom + posterTop - mSectionGap) / 2;
        // 水平：右边缘固定为「背景图左边界 + 重叠量」，左边缘由基准宽度反推。
        int right = heroPos[0] + HERO_LOGO_OVERLAP_PX - root[0];
        int width = mLogoBaseWidth;
        int height = mLogoBaseHeight;
        int left = right - width;
        if (left < 0) {
            width = Math.max(1, right);
            height = Math.max(1, Math.round(mLogoBaseHeight * (width / (float) mLogoBaseWidth)));
            left = 0;
        }
        int top = center - height / 2 - root[1];
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) logo.getLayoutParams();
        if (params.leftMargin == left && params.topMargin == top
                && params.width == width && params.height == height) return;
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.width = width;
        params.height = height;
        params.leftMargin = left;
        params.topMargin = top;
        logo.setLayoutParams(params);
    }

    // ------------------------------------------------------------ 最近观看 ↔ TMDB 素材

    /**
     * 用全局焦点监听（而不是逐卡片 setOnFocusChangeListener）关联海报与背景图：
     * 不覆盖 Presenter / Leanback 自己挂的焦点回调，焦点落到任意历史卡片都能生效。
     */
    private void setupHeroFocus() {
        getWindow().getDecorView().getViewTreeObserver()
                .addOnGlobalFocusChangeListener((oldFocus, newFocus) -> updateHeroFor(newFocus));
        TmdbApi.setOnFetched(mTmdbRefresh);
    }

    /**
     * 焦点落到最近观看海报：先按海报名补抓一次，再用同一条 TmdbArt 同时刷新背景图与 logo。
     * 两者在同一次调用里启动 {@link HeroFade} 交叉淡入，时长完全一致，视觉上是一组转场。
     */
    private void updateHeroFor(View focus) {
        History history = findHistory(focus);
        if (history != null) fetchTmdbForHistory(history);
        TmdbArt art = history == null ? null : TmdbStore.find(history.getVodName());
        // 两张位图都在动画启动前备好：否则背景先淡入、logo 还在解码，视觉上会有先后差。
        String backdrop = art != null && art.hasBackdrop() ? art.getBackdrop() : null;
        Bitmap backdropBitmap = TextUtils.isEmpty(backdrop) ? null : TmdbStore.bitmap(backdrop);
        if (backdropBitmap == null) backdrop = null;
        String logo = art == null ? null : art.getLogo();
        Bitmap logoBitmap = TextUtils.isEmpty(logo) ? null : TmdbStore.logoBitmap(logo);
        if (logoBitmap == null) logo = null;
        updateBackdrop(backdrop, backdropBitmap);
        updateLogo(logo, logoBitmap);
    }

    private void updateBackdrop(@Nullable String path, @Nullable Bitmap bitmap) {
        // 同一张海报反复获得焦点时不重复触发淡入动画。
        if (TextUtils.equals(path, mHeroArt)) return;
        mHeroArt = path;
        if (path == null) mBinding.homeHero.load(R.drawable.home_hero, HERO_WIDTH_RATIO, true);
        else mBinding.homeHero.load(bitmap, HERO_WIDTH_RATIO);
    }

    /**
     * logo 与背景共用 HeroFade；没有 logo（或焦点离开历史卡片）时淡出到透明。
     *
     * <p>每张 logo 都包进 {@link HeroFade#logo(Bitmap, int, int)} 的固定画布层，
     * 而不是直接塞 BitmapDrawable：后者由 TransitionDrawable 统一给 bounds 并拉伸铺满，
     * 快速切换时会被相邻 logo 的固有尺寸带偏、拉变形。</p>
     */
    private void updateLogo(@Nullable String path, @Nullable Bitmap bitmap) {
        if (TextUtils.equals(path, mHeroLogo)) return;
        mHeroLogo = path;
        HeroFade.crossFade(mBinding.homeLogo,
                HeroFade.logo(bitmap, ResUtil.dp2px(LOGO_CANVAS_DP_WIDTH), ResUtil.dp2px(LOGO_CANVAS_DP_HEIGHT)),
                true);
    }

    /**
     * 第二个抓取入口：播放页之外，最近观看海报获得焦点时按海报名补抓。
     * 内部有总开关 / 焦点开关、缓存命中、并发去重与 miss 冷却，横移海报不会产生大量请求。
     */
    private void fetchTmdbForHistory(History history) {
        if (!TmdbSetting.isEnabled() || !TmdbSetting.isFocusFetch()) return;
        String name = history.getVodName();
        if (TextUtils.isEmpty(name)) return;
        TmdbApi.fetch(name, null, null);
    }

    /** 沿 parent 链找 HistoryPresenter 绑在卡片上的带 key tag。 */
    private History findHistory(View view) {
        while (view != null) {
            Object tag = view.getTag(R.id.tmdb_history);
            if (tag instanceof History) return (History) tag;
            ViewParent parent = view.getParent();
            view = parent instanceof View ? (View) parent : null;
        }
        return null;
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
     * 冷启动初始焦点：「最近观看」有记录时落在列表第一张海报，否则落在「首页」胶囊。
     *
     * <p>分类列表与历史数据都可能晚于内容到达，因此本方法会在 {@code showContent()}、
     * {@code setTypes()} 各调用一次，并由 {@code applyInitialHistoryFocus()} 在历史
     * 首次就绪后补一次；只生效一次。</p>
     */
    private void setFocus() {
        // 站点名称只作展示、不参与焦点，避免「海报上按上键」时被它抢走焦点
        mBinding.header.title.setSelected(true);
        if (initialFocusApplied) return;
        if (hasHistory() && focusHistoryFirstPoster()) {
            initialFocusApplied = true;
        } else if (mHeader.getAdapter().getItemCount() > 0) {
            initialFocusApplied = true;
            mHeader.focusType(Math.max(0, mHeader.getSelected()));
        } else if (hasPoster()) {
            initialFocusApplied = true;
            focusHomeFirstPoster();
        }
    }

    /** 「最近观看」是否已经插入了非空的 ListRow。 */
    private boolean hasHistory() {
        if (mAdapter == null || mHistoryAdapter == null || mHistoryAdapter.size() == 0) return false;
        int row = getHistoryIndex();
        return row > 0 && row < mAdapter.size() && mAdapter.get(row) instanceof ListRow;
    }

    /**
     * 冷启动首次读到历史记录：非空就把初始焦点落到第一张海报。
     * 用 post 等一帧，确保晚于同步的 setFocus（胶囊）执行，且 adapter 行已经提交。
     */
    private void applyInitialHistoryFocus(List<History> items) {
        if (!mInitialHistoryFocusPending) return;
        mInitialHistoryFocusPending = false;
        if (items.isEmpty()) return;
        mBinding.recycler.post(() -> {
            if (isFinishing() || isDestroyed() || !hasHistory()) return;
            if (focusHistoryFirstPoster()) initialFocusApplied = true;
        });
    }

    /** 精确落到「最近观看」行的第一张海报（不是推荐网格的第一张）。 */
    private boolean focusHistoryFirstPoster() {
        int row = getHistoryIndex();
        if (row <= 0 || row >= mAdapter.size() || !(mAdapter.get(row) instanceof ListRow)) return false;
        mBinding.recycler.setSelectedPositionWithSub(row, 0);
        mBinding.recycler.requestFocus();
        // 行内网格可能还没布局出来：下一帧再把焦点精确交给第 0 张海报。
        mBinding.recycler.post(() -> {
            if (isFinishing() || isDestroyed()) return;
            RecyclerView.ViewHolder holder = mBinding.recycler.findViewHolderForAdapterPosition(row);
            HorizontalGridView grid = holder == null ? null : findHorizontalGrid(holder.itemView);
            if (grid == null) return;
            grid.setSelectedPosition(0);
            grid.requestFocus();
        });
        return true;
    }

    private HorizontalGridView findHorizontalGrid(View view) {
        if (view instanceof HorizontalGridView) return (HorizontalGridView) view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            HorizontalGridView grid = findHorizontalGrid(group.getChildAt(i));
            if (grid != null) return grid;
        }
        return null;
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
        // 冷启动：历史首次就绪且非空 → 初始焦点落在第一张海报（同时触发背景图 / logo 关联）。
        applyInitialHistoryFocus(items);
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
        // 回到首页时按当前滚动位置重新对齐背景图 / logo 的透明度（分类页期间进度不会更新）。
        updateHeroScroll();
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
        TmdbApi.setOnFetched(null);
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
