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
import android.widget.RelativeLayout;

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
import com.fongmi.android.NewTV.R;
import com.fongmi.android.NewTV.api.TmdbApi;
import com.fongmi.android.NewTV.api.TmdbStore;
import com.fongmi.android.NewTV.api.config.VodConfig;
import com.fongmi.android.NewTV.api.node.NodeRuntime;
import com.fongmi.android.NewTV.bean.Cache;
import com.fongmi.android.NewTV.bean.Class;
import com.fongmi.android.NewTV.bean.History;
import com.fongmi.android.NewTV.bean.Result;
import com.fongmi.android.NewTV.bean.Site;
import com.fongmi.android.NewTV.bean.TmdbArt;
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
import com.fongmi.android.NewTV.ui.custom.FeatheredImageView;
import com.fongmi.android.NewTV.ui.custom.HeroFade;
import com.fongmi.android.NewTV.ui.dialog.SiteDialog;
import com.fongmi.android.NewTV.ui.fragment.FolderFragment;
import com.fongmi.android.NewTV.ui.fragment.RecommendFragment;
import com.fongmi.android.NewTV.ui.helper.ContentFocusHost;
import com.fongmi.android.NewTV.ui.helper.HomeHeader;
import com.fongmi.android.NewTV.ui.helper.PageFocus;
import com.fongmi.android.NewTV.ui.presenter.HeaderPresenter;
import com.fongmi.android.NewTV.ui.presenter.HistoryPresenter;
import com.fongmi.android.NewTV.utils.KeyUtil;
import com.fongmi.android.NewTV.utils.Notify;
import com.fongmi.android.NewTV.utils.PermissionUtil;
import com.fongmi.android.NewTV.utils.ResUtil;
import com.github.catvod.net.OkHttp;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.Collections;
import java.util.List;

/**
 * 主页面：顶部导航 + 「最近观看」内容区 + 内嵌分类分页。
 *
 * <p>导航胶囊下标与分页下标的约定（与 {@link TypeAdapter} 的两枚前置固定胶囊对应）：</p>
 * <pre>
 *   胶囊 0 = 最近观看（本 Activity 的 progressLayout / recycler，不占分页）
 *   胶囊 1 = 首页（分页 0，RecommendFragment；应用启动默认页）
 *   胶囊 2.. = 真实分类（分页 1..N，FolderFragment）  → 分页下标 = 胶囊下标 − 1
 * </pre>
 *
 * <p>焦点规则与 {@link VodActivity} 的分类页完全一致：胶囊 ↓ 进第一张海报、
 * 第一排海报 ↑ 回胶囊、按返回记住海报、从其它页面回来时把选中项复位到左上角第一张
 * （只复位位置、不抢焦点）。</p>
 */
public class HomeActivity extends BaseActivity implements CustomTitleView.Listener, HistoryPresenter.OnClickListener, HomeHeader.Host, ContentFocusHost {

    private static final String STATE_ACTION_HANDLED = "action_handled";
    /** 组合标宽度占屏宽的比例（设计稿 1920x1080 中 920x75 的裁切图）。 */
    private static final float SPLASH_LOGO_WIDTH_RATIO = 0.479f;
    private static final float SPLASH_LOGO_ASPECT = 920f / 75f;
    /** 右上角背景图宽度占屏宽的比例；高度按原图比自动换算，各尺寸屏幕视觉比例一致。 */
    private static final float HERO_WIDTH_RATIO = FeatheredImageView.WIDTH_RATIO;
    /** 欢迎页最短展示时长：logo 出现到开始淡出至少 1 秒，避免一闪而过。 */
    private static final long SPLASH_MIN_DISPLAY = 1000L;
    /** 首页迟迟不就绪（站点异常）时的兜底时长，保证不会卡在欢迎页。 */
    private static final long SPLASH_TIMEOUT = 8000L;
    /** 「最近观看」胶囊上连按两次返回退出：两次间隔上限。 */
    private static final long BACK_EXIT_WINDOW = 2000L;
    /** 行内海报的横向间距（dp）。 */
    private static final int ROW_SPACING_DP = 16;
    /**
     * 片名 logo 右边缘越过右上角背景图左边界的距离（物理像素）。
     * 用 px 而非 dp：这里描述的是两张图的实际重叠量，与密度适配无关。
     */
    private static final int HERO_LOGO_OVERLAP_PX = 300;
    /**
     * 「最近观看」海报卡片底边到屏幕底边的留白（物理像素）。
     *
     * <p>留白由 recycler 的 {@code paddingBottom} 承担（XML 给首帧兜底，
     * {@link #applyRecentBottomLayout()} 运行时按本常量重新写入），
     * 而不是 layout_marginBottom：留白落在容器<b>内部</b>，选中放大的海报画进这段
     * padding 里，始终不超出 recycler 的边界，也不会超出屏幕。
     *
     * <p>用 px 而非 dp：这是屏幕上的绝对空隙，各端保持同一物理像素值。
     */
    private static final int RECENT_BOTTOM_GAP_PX = 60;
    /** logo 画布的逻辑尺寸（dp），与 activity_home.xml 中 homeLogo 的基准尺寸保持一致。 */
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
    private HomeHeader mHeader;
    private SiteViewModel mViewModel;
    private Intent pendingAction;
    private boolean actionReady;
    private boolean initialFocusApplied;
    private int lastTypePosition = -1;
    /** 启动 / onNewIntent 指定的落地页；默认「首页」。-1 = 已生效，仅生效一次，避免刷新时把用户踢回该页。 */
    private int mInitialPage = HomeHeader.PAGE_HOME;
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
    /** 板块高度是否已由实测写入过 LayoutParams（避免反复 setLayoutParams）。 */
    private boolean mRecentLayoutApplied;
    /** 贴底布局校准是否已排入下一帧：避免一次布局变化连发多个 post。 */
    private boolean mRecentLayoutPending;
    /** 焦点不在任何海报上时，刮削背景的轮换间隔。 */
    private static final long HERO_ROTATE_INTERVAL_MS = 15_000L;
    /** 背景轮换是否运行中（仅焦点离开海报时运行）。 */
    private boolean mHeroRotating;
    /** 当前轮换到的海报下标，顺序与「最近观看」列表一致（从左到右）。 */
    private int mHeroRotateIndex;
    /** 离开海报列表时的最后焦点下标；-1 = 本次进入首页后尚未落过任何海报。 */
    private int mHeroRotateAnchor = -1;
    /** 「最近观看」列表快照，供轮换定时器使用，避免每次轮换都查库。 */
    private List<History> mHeroRotateItems = Collections.emptyList();
    /** 轮换任务：只换背景与 logo，绝不改动列表焦点或选中位置。 */
    private final Runnable mHeroRotateTask = () -> {
        if (!mHeroRotating) return;
        rotateHero();
    };

    /** 后台抓取完成后刷回当前焦点海报；字段强引用，配合 TmdbApi 内的弱引用使用。 */
    private final Runnable mTmdbRefresh = () -> {
        History history = findHistory(getCurrentFocus());
        if (history != null) applyHeroArt(history, false);
        else if (mHeroRotating) showHeroAt(mHeroRotateIndex);
    };
    /** 欢迎页状态：logo 已出现 + 首页内容就绪，两者都满足才淡出。 */
    private boolean splashShown;
    private boolean homeLoaded;
    private boolean splashDismissed;
    private long splashShownAt;
    /** 上次在「最近观看」胶囊上按返回的时刻，用于连按两次退出。 */
    private long mLastBackAt;
    /** 分类导航快速横移时的待切分页：去抖，避免逐个分类并发实例化/加载。 */
    private int mPendingCategory = -1;
    private final Runnable mCategoryTask = () -> {
        int target = mPendingCategory;
        mPendingCategory = -1;
        if (target >= 0) applyCategory(target);
    };

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityHomeBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        pendingAction = intent;
        int page = intent.getIntExtra(HomeHeader.EXTRA_PAGE, -1);
        if (page == HomeHeader.PAGE_HOME || page == HomeHeader.PAGE_RECENT) {
            mInitialPage = page;
            // 热启动：页面已就绪，直接落过去；冷启动由 setTypes() 在分类栏就绪后落地。
            if (mHeader != null && mHeader.getAdapter().getItemCount() > 1) {
                mInitialPage = -1;
                clearPendingPoster();
                cancelPendingCategory();
                mHeader.focusType(page);
                if (page == HomeHeader.PAGE_RECENT) showRecent();
                else showCategory(0);
            }
        }
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
        int page = getIntent().getIntExtra(HomeHeader.EXTRA_PAGE, -1);
        if (page == HomeHeader.PAGE_HOME || page == HomeHeader.PAGE_RECENT) mInitialPage = page;
        mResult = Result.empty();
        mBinding.progressLayout.showProgress();
        setupHomeHero();
        setupHeroLayout();
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
        // 「最近观看」第一排海报按上键：走与分类页相同的 ContentFocusHost 通道回到当前胶囊。
        mBinding.recycler.setOnKeyInterceptListener(event -> {
            if (!KeyUtil.isActionDown(event) || !KeyUtil.isUpKey(event)) return false;
            View focus = getCurrentFocus();
            return focus != null && isFirstPosterRow(focus) && onFirstRowUp();
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
                // 与分类页一致：切页只把选中项复位到左上角第一张，不抢焦点。
                final int index = position;
                mBinding.pager.post(() -> {
                    PageFocus page = getPage(index);
                    if (page != null) page.resetToFirstPoster();
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
        // 历史为空时给板块补一行提示文字，仍由 HeaderPresenter 渲染，保证与标题同一套样式。
        selector.addPresenter(String.class, new HeaderPresenter());
        mPresenter = new HistoryPresenter(this);
        // 「最近观看」行与分类页海报行共用布局：行宽不设上限，只沿用「对齐滚动」策略。
        selector.addPresenter(ListRow.class, new CustomRowPresenter(ROW_SPACING_DP, FocusHighlight.ZOOM_FACTOR_SMALL, HorizontalGridView.FOCUS_SCROLL_ALIGNED), HistoryPresenter.class);
        mBinding.recycler.setAdapter(new ItemBridgeAdapter(mAdapter = new ArrayObjectAdapter(selector)));
        mBinding.recycler.setVerticalSpacing(ResUtil.dp2px(8));
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

    /** homeContent 是分类栏、推荐页内容与筛选缓存的共同数据源，只在这里发起一次请求。 */
    private void getHomeContent() {
        mViewModel.homeContent();
    }

    /**
     * 刷新站点首页数据（分类导航 + 「首页」海报）。
     *
     * <p><b>请求必须由本 Activity 发起</b>：冷启动 / 换源时分页仍是 GONE，
     * RecommendFragment 及其子 TypeFragment 还没创建完，{@link FolderFragment#onRefresh()}
     * 会静默空转；若把 homeContent() 委托给页面并提前 return，就会出现
     * 「启动后导航只剩两枚固定胶囊、推荐页空白，必须手动点一次才加载」。
     * 页面只在真正可见时负责清旧行 / 摆进度态，不再承担发起请求的职责。</p>
     */
    private void refreshRecommend() {
        // 只在推荐页确实可见时才做 UI 准备：pager GONE 时不要 instantiateItem，
        // 否则会绕过 ViewPager 提前建 Fragment（并发建页 / 状态不一致的隐患）。
        if (mBinding.pager.getVisibility() == View.VISIBLE && mBinding.pager.getCurrentItem() == 0) {
            PageFocus page = getPage(0);
            if (page != null) page.onRefresh();
        }
        getHomeContent();
    }

    private void onResult(Result result) {
        mResult = result;
        setTypes();
        Cache.clear().put(result);
        // 首页数据已就绪：欢迎页等淡入播完后自动淡出
        onHomeLoaded();
    }

    private void setTypes() {
        List<Class> types = mResult.getTypes();
        int current = mHeader.getSelected();
        // 分类列表变化（换源/刷新）：上一轮记住的海报已失效，未落地的切换也一并作废。
        clearPendingPoster();
        cancelPendingCategory();
        HomeHeader.publish(types);
        // 首次进入默认落在「首页」胶囊（1）；外部 Intent 可指定「最近观看」（0）；
        // 之后的刷新保留用户当前所在页，不把人踢回默认页。
        int target = mInitialPage >= 0 ? mInitialPage : current;
        mInitialPage = -1;
        mHeader.setTypes(types, target);
        if (mBinding.pager.getAdapter() != null) mBinding.pager.getAdapter().notifyDataSetChanged();
        int selected = mHeader.getSelected();
        if (selected <= 0) showRecent();
        else showCategory(selected - 1);
        setFocus();
    }

    private void setAdapter() {
        mHistoryAdapter = new ArrayObjectAdapter(mPresenter);
        // 「最近观看」页只有一块内容：标题行 + 历史海报行（有记录时才插入）。
        mAdapter.add(R.string.home_history);
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
        // 配置加载失败：不会有首页数据，退到「最近观看」兜底，
        // 避免高亮「首页」胶囊却仍显示最近观看内容。
        if (!ok && mHeader != null && mHeader.getAdapter().getTypes().isEmpty()) {
            mInitialPage = HomeHeader.PAGE_RECENT;
            mHeader.select(HomeHeader.PAGE_RECENT);
        }
        showContent();
        // 配置就没加载成功：不会有首页数据，欢迎页不必再等
        if (!ok) onHomeLoaded();
        consumeAction();
    }

    private void showContent() {
        mBinding.progressLayout.showContent();
        setFocus();
    }

    // ------------------------------------------------------------ 右上角背景图与片名 logo

    /** 右上角背景图：按屏宽比例定尺寸，右上角贴边；左侧/底部羽化由控件自身完成。 */
    private void setupHomeHero() {
        mBinding.homeHero.load(R.drawable.home_hero, HERO_WIDTH_RATIO);
    }

    /**
     * 「最近观看」板块改为沿屏幕底部对齐后，顶部留白带已移除：
     * 背景图与 logo 不再需要跟着列表滚动淡出，只在布局变化时重新给 logo 定位。
     */
    private void setupHeroLayout() {
        mBinding.getRoot().getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            scheduleRecentBottomLayout();
            applyLogoPlacement();
        });
    }

    /**
     * 「最近观看」板块贴底对齐，并为选中放大预留安全区。
     *
     * <p><b>不能依赖 XML 的 wrap_content</b>：Leanback 的 VerticalGridView 在 RelativeLayout
     * 里以 AT_MOST 测量时会直接取父容器全高（ADB 实测 2560x1440 设备上
     * recycler = [0,286][2560,1440]，标题与海报排在顶部、卡片底边 788，
     * 距屏幕底 652px），此时 alignParentBottom 与 marginBottom 一并失效。
     * 这里按「已布局子项的最大 bottom」实测内容高度，显式写 LayoutParams.height，
     * 再重新声明贴底规则。</p>
     *
     * <p>底部留白 {@link #RECENT_BOTTOM_GAP_PX} 改由 {@code paddingBottom} 承担，
     * {@code bottomMargin} 固定为 0：recycler 底边贴屏幕底，卡片底边距屏幕底正好 60px。
     * 这段 padding 同时是焦点放大的安全区 —— 放大后的海报画进 padding 区，
     * 仍完全落在 recycler 的 bounds 内，既不超出容器也不超出屏幕。</p>
     *
     * <p>高度 = 标题行 + 一行历史（或空态提示）+ 底部留白；历史增删后
     * {@link #refreshRecentLayout()} 会再触发一次。参数不变时不再 setLayoutParams，
     * 避免反复布局。</p>
     *
     * <p>放大余量核算：当前 {@code FocusHighlight.ZOOM_FACTOR_SMALL = 110%}，
     * 卡片高约 400px 时单侧只溢出约 20px，远小于 60px；若日后上调放大倍率，
     * 需同步复核这段留白是否仍够容纳溢出。</p>
     */
    private void scheduleRecentBottomLayout() {
        if (mRecentLayoutPending) return;
        mRecentLayoutPending = true;
        mBinding.recycler.post(() -> {
            mRecentLayoutPending = false;
            applyRecentBottomLayout();
        });
    }

    private void applyRecentBottomLayout() {
        // 切到分类分页时 progressLayout 被隐藏，子项坐标是陈旧的，此时不动布局参数。
        if (mBinding.progressLayout.getVisibility() != View.VISIBLE
                || mBinding.recycler.getVisibility() != View.VISIBLE) return;
        // 留白统一按常量写成物理像素，避免 XML 与运行时不同步（各端一致）。
        if (mBinding.recycler.getPaddingBottom() != RECENT_BOTTOM_GAP_PX) {
            mBinding.recycler.setPaddingRelative(mBinding.recycler.getPaddingStart(),
                    mBinding.recycler.getPaddingTop(), mBinding.recycler.getPaddingEnd(),
                    RECENT_BOTTOM_GAP_PX);
        }
        int contentHeight = 0;
        for (int i = 0; i < mBinding.recycler.getChildCount(); i++) {
            contentHeight = Math.max(contentHeight, mBinding.recycler.getChildAt(i).getBottom());
        }
        if (contentHeight <= 0) return; // 子项尚未布局，等下一次全局布局回调
        contentHeight += mBinding.recycler.getPaddingBottom();
        // 焦点放大作用在 item view 上，行容器默认会裁掉放大出来的边角。
        disableClipping(mBinding.recycler);
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) mBinding.recycler.getLayoutParams();
        if (mRecentLayoutApplied && params.height == contentHeight && params.bottomMargin == 0) return;
        params.height = contentHeight;
        params.bottomMargin = 0;
        params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        mRecentLayoutApplied = true;
        mBinding.recycler.setLayoutParams(params);
    }

    /**
     * 关闭「最近观看」网格子树的裁剪，让选中放大完整显示且不被切边。
     *
     * <p>Leanback 的 {@code FocusHighlight} 在 item view 上做 scaleX/scaleY，放大后的
     * 绘制区域大于 layout bounds；HorizontalGridView / ListRowView 默认
     * {@code clipChildren=true}，会把放大的边角直接裁掉。放大后的内容仍完全落在
     * recycler 的 bounds 内（下方 60px padding、左右 24dp padding、上方是标题行），
     * 因此只需关掉本网格子树的裁剪：不影响外层布局，也不影响其它页面。</p>
     *
     * <p>每次布局回调都跑一遍是幂等的，且能覆盖历史数据变化后新建的 item view。</p>
     */
    private void disableClipping(View view) {
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        group.setClipChildren(false);
        group.setClipToPadding(false);
        for (int i = 0; i < group.getChildCount(); i++) disableClipping(group.getChildAt(i));
    }

    /**
     * 片名 logo 定位：垂直中心落在「导航栏底边 ↔ 最近观看板块顶边」这段留白区正中；
     * 右边缘越过背景图左边界 {@link #HERO_LOGO_OVERLAP_PX} px。
     *
     * <p>底部对齐后板块高度会随历史记录数量变化，这里直接取 recycler 的实测顶边，
     * 不再按「背景图底边 − 重合量」反推（那是旧顶部留白带布局的口径）。</p>
     */
    private void applyLogoPlacement() {
        View logo = mBinding.homeLogo;
        View hero = mBinding.homeHero;
        if (logo.getHeight() <= 0 || hero.getWidth() <= 0 || mBinding.header.typeNav.getHeight() <= 0
                || mBinding.progressLayout.getHeight() <= 0 || mBinding.recycler.getHeight() <= 0) return;
        if (mLogoBaseWidth <= 0) {
            mLogoBaseWidth = logo.getWidth();
            mLogoBaseHeight = logo.getHeight();
        }
        int[] root = new int[2];
        int[] heroPos = new int[2];
        int[] nav = new int[2];
        int[] box = new int[2];
        mBinding.getRoot().getLocationInWindow(root);
        hero.getLocationInWindow(heroPos);
        mBinding.header.typeNav.getLocationInWindow(nav);
        mBinding.recycler.getLocationInWindow(box);
        int navBottom = nav[1] + mBinding.header.typeNav.getHeight();
        int center = (navBottom + box[1]) / 2;
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

    /** 用全局焦点监听关联海报与背景图：不覆盖 Presenter / Leanback 自己挂的焦点回调。 */
    private void setupHeroFocus() {
        getWindow().getDecorView().getViewTreeObserver()
                .addOnGlobalFocusChangeListener(this::onHeroFocusChanged);
        TmdbApi.addOnFetched(mTmdbRefresh);
    }

    /**
     * 焦点进出海报的统一入口。
     *
     * <ul>
     *   <li>焦点落在海报上 → 停轮换，立即显示该海报的刮削素材，并记住它在列表中的位置；</li>
     *   <li>焦点离开海报 → 以「离开时那张」为起点开始 15s 轮换；若从未落过海报则从第 0 张开始。</li>
     * </ul>
     * 轮换只改背景与 logo，不动 recycler 的焦点与选中位置。
     */
    private void onHeroFocusChanged(View oldFocus, View newFocus) {
        History target = findHistory(newFocus);
        if (target != null) {
            int index = indexOfHero(target);
            if (index >= 0) mHeroRotateAnchor = index;
            stopHeroRotate();
            applyHeroArt(target, true);
            return;
        }
        if (!isRecentSectionActive()) {
            // 焦点在其它板块：不轮换，背景由该板块自己的海报焦点逻辑负责。
            stopHeroRotate();
            clearHeroArt();
            return;
        }
        History previous = findHistory(oldFocus);
        if (previous != null) {
            int index = indexOfHero(previous);
            if (index >= 0) mHeroRotateAnchor = index;
        }
        if (!mHeroRotating) startHeroRotate();
    }

    private int indexOfHero(History history) {
        for (int i = 0; i < mHeroRotateItems.size(); i++) {
            History item = mHeroRotateItems.get(i);
            if (item == history || TextUtils.equals(item.getVodName(), history.getVodName())) return i;
        }
        return -1;
    }

    /** 把某条历史对应的刮削素材贴到背景与 logo 上（不涉及焦点）。 */
    private void applyHeroArt(History history, boolean fetch) {
        if (fetch) fetchTmdbForHistory(history);
        TmdbArt art = TmdbStore.find(history.getVodName());
        String backdrop = art != null && art.hasBackdrop() ? art.getBackdrop() : null;
        Bitmap backdropBitmap = TextUtils.isEmpty(backdrop) ? null : TmdbStore.bitmap(backdrop);
        if (backdropBitmap == null) backdrop = null;
        String logo = art == null ? null : art.getLogo();
        Bitmap logoBitmap = TextUtils.isEmpty(logo) ? null : TmdbStore.logoBitmap(logo);
        if (logoBitmap == null) logo = null;
        updateBackdrop(backdrop, backdropBitmap);
        updateLogo(logo, logoBitmap);
    }

    /**
     * 「最近观看」板块是否正在展示：分页不可见，且该板块自身的网格可见。
     * 背景轮换只在这个板块内生效，推荐 / 分类等其它板块一律不轮换。
     */
    private boolean isRecentSectionActive() {
        return mBinding.pager.getVisibility() != View.VISIBLE
                && mBinding.recycler.getVisibility() == View.VISIBLE;
    }

    /**
     * 清空「最近观看」板块的刮削素材：背景层直接隐藏（不加载内置海报墙，避免仍显示海报拼图），
     * logo 淡出。其它板块不渲染任何属于本板块的海报素材。
     */
    private void clearHeroArt() {
        mHeroArt = null;
        mHeroLogo = null;
        mBinding.homeHero.setVisibility(View.GONE);
        HeroFade.crossFade(mBinding.homeLogo, null, true);
    }

    private void startHeroRotate() {
        if (mHeroRotating) return;
        // 只服务于「最近观看」板块：其它板块（首页 / 分类）有自己的海报焦点，不在此轮换。
        if (!isRecentSectionActive()) return;
        if (mHeroRotateItems.isEmpty()) return; // 数据未就绪，等 getHistory() 回调再启动
        mHeroRotating = true;
        int index = findHeroWithArt(mHeroRotateAnchor < 0 ? 0 : mHeroRotateAnchor, true);
        if (index < 0) {
            // 一条可用素材都没有：回内置海报墙，但仍按轮换间隔重试，等刮削结果落盘。
            fetchTmdbForHistory(mHeroRotateItems.get(Math.floorMod(mHeroRotateAnchor < 0 ? 0 : mHeroRotateAnchor, mHeroRotateItems.size())));
            updateBackdrop(null, null);
            updateLogo(null, null);
        } else {
            mHeroRotateIndex = index;
            showHeroAt(index);
        }
        scheduleHeroRotate();
    }

    private void rotateHero() {
        // 预取下一张的素材：刮削结果落盘后，下一轮即可参与轮换（fetch 自身有缓存/miss 节流）。
        int size = mHeroRotateItems.size();
        if (size > 0) fetchTmdbForHistory(mHeroRotateItems.get(Math.floorMod(mHeroRotateIndex + 1, size)));
        int next = findHeroWithArt(mHeroRotateIndex + 1, false);
        if (next >= 0 && next != mHeroRotateIndex) {
            mHeroRotateIndex = next;
            showHeroAt(next);
        }
        scheduleHeroRotate();
    }

    private void showHeroAt(int index) {
        if (index < 0 || index >= mHeroRotateItems.size()) return;
        applyHeroArt(mHeroRotateItems.get(index), false);
    }

    /** 从 from 起环形找一张「有 backdrop 且已落盘」的海报；inclusive=false 时跳过 from 本身。 */
    private int findHeroWithArt(int from, boolean inclusive) {
        int size = mHeroRotateItems.size();
        if (size == 0) return -1;
        int start = Math.floorMod(from, size);
        int step = inclusive ? 0 : 1;
        for (int i = 0; i < size; i++) {
            int index = (start + i + step) % size;
            TmdbArt art = TmdbStore.find(mHeroRotateItems.get(index).getVodName());
            String path = art != null && art.hasBackdrop() ? art.getBackdrop() : null;
            if (!TextUtils.isEmpty(path) && TmdbStore.bitmap(path) != null) return index;
        }
        return -1;
    }

    private void scheduleHeroRotate() {
        App.post(mHeroRotateTask, HERO_ROTATE_INTERVAL_MS);
    }

    private void stopHeroRotate() {
        mHeroRotating = false;
        App.removeCallbacks(mHeroRotateTask);
    }

    /** onResume / 列表刷新后调用：焦点不在海报上就恢复轮换。 */
    private void resumeHeroRotateIfIdle() {
        if (mHeroRotating) return;
        if (findHistory(getCurrentFocus()) != null) return;
        startHeroRotate();
    }

    private void updateBackdrop(@Nullable String path, @Nullable Bitmap bitmap) {
        // 隐藏状态（其它板块）下同一张图也要重新上屏，不能因为 path 相同就跳过。
        boolean hidden = mBinding.homeHero.getVisibility() != View.VISIBLE;
        if (!hidden && TextUtils.equals(path, mHeroArt)) return;
        mHeroArt = path;
        mBinding.homeHero.setVisibility(View.VISIBLE);
        if (path == null) mBinding.homeHero.load(R.drawable.home_hero, HERO_WIDTH_RATIO, true);
        else mBinding.homeHero.load(bitmap, HERO_WIDTH_RATIO);
    }

    private void updateLogo(@Nullable String path, @Nullable Bitmap bitmap) {
        if (TextUtils.equals(path, mHeroLogo)) return;
        mHeroLogo = path;
        HeroFade.crossFade(mBinding.homeLogo,
                HeroFade.logo(bitmap, ResUtil.dp2px(LOGO_CANVAS_DP_WIDTH), ResUtil.dp2px(LOGO_CANVAS_DP_HEIGHT)),
                true);
    }

    private void fetchTmdbForHistory(History history) {
        if (!TmdbSetting.isEnabled()) return;
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

    private void initSplash(Bundle savedInstanceState) {
        // 界面重建（切分辨率 / 深色模式等）不再重播欢迎页
        if (savedInstanceState != null) {
            splashDismissed = true;
            mBinding.splashLayer.setVisibility(View.GONE);
            return;
        }
        applySplashLogoSize();
        mBinding.splashLogo.setAlpha(1f);
        splashShown = true;
        splashShownAt = SystemClock.elapsedRealtime();
        dismissSplashIfReady();
        App.post(() -> {
            homeLoaded = true;
            dismissSplashIfReady();
        }, SPLASH_TIMEOUT);
    }

    private void applySplashLogoSize() {
        int width = Math.round(getResources().getDisplayMetrics().widthPixels * SPLASH_LOGO_WIDTH_RATIO);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, Math.round(width / SPLASH_LOGO_ASPECT));
        params.gravity = Gravity.CENTER;
        mBinding.splashLogo.setLayoutParams(params);
    }

    private void onHomeLoaded() {
        homeLoaded = true;
        dismissSplashIfReady();
    }

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

    // ------------------------------------------------------------ 焦点（与分类页同一套契约）

    /**
     * 初始焦点落在落地页对应的胶囊上，而不是海报 —— 与 VodActivity 的分类页一致：
     * 胶囊 ↓ 才进入内容区，切页时焦点也不会自己跳进海报。
     */
    private void setFocus() {
        // 站点名称只作展示、不参与焦点，避免「海报上按上键」时被它抢走焦点
        mBinding.header.title.setSelected(true);
        if (initialFocusApplied) return;
        if (mHeader.getAdapter().getItemCount() == 0) return;
        initialFocusApplied = true;
        // 落地页由 setTypes() 先确定，这里只把焦点交给当前选中的胶囊（默认「首页」）。
        mHeader.focusType(Math.max(0, mHeader.getSelected()));
    }

    /** 第一张历史海报所在行；没有历史记录时返回 -1。 */
    private int firstRecentPosterRow() {
        if (mAdapter == null) return -1;
        for (int i = 0; i < mAdapter.size(); i++) if (mAdapter.get(i) instanceof ListRow) return i;
        return -1;
    }

    /** 胶囊 ↓：焦点精确落到「最近观看」第一张海报（含行内第 0 个）。 */
    private boolean focusRecentFirstPoster() {
        int row = firstRecentPosterRow();
        if (row < 0) return false;
        mBinding.recycler.setSelectedPositionWithSub(row, 0);
        mBinding.recycler.requestFocus();
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

    /**
     * 从其它分类页回到「最近观看」时初始化焦点位置：复位到左上角第一张，但不抢焦点。
     *
     * <p>对应分类页 {@code onPageSelected → resetToFirstPoster()}；焦点保持在导航栏，
     * 用户按 ↓ 才进入，避免「切回首页时画面突然跳到某张海报」。</p>
     */
    private void resetRecentPoster() {
        int row = firstRecentPosterRow();
        if (row < 0) return;
        mBinding.recycler.post(() -> {
            if (isFinishing() || isDestroyed()) return;
            mBinding.recycler.scrollToPosition(row);
            mBinding.recycler.setSelectedPositionWithSub(row, 0);
            RecyclerView.ViewHolder holder = mBinding.recycler.findViewHolderForAdapterPosition(row);
            HorizontalGridView grid = holder == null ? null : findHorizontalGrid(holder.itemView);
            if (grid != null) grid.setSelectedPosition(0);
        });
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

    private boolean isContentFocused() {
        View focus = getCurrentFocus();
        if (focus == null) return false;
        return isDescendant(focus, mBinding.recycler) || isDescendant(focus, mBinding.pager);
    }

    /** 当前是否停在「首页」胶囊对应的分页（分页 0，且已真正切过去）。 */
    private boolean isHomeCapsule() {
        return mHeader.getSelected() == HomeHeader.PAGE_HOME
                && mBinding.pager.getVisibility() == View.VISIBLE
                && mBinding.pager.getCurrentItem() == 0;
    }

    /** 取消还在去抖中的分类切换任务，避免返回 / 落地后被旧任务重新拉回分类页。 */
    private void cancelPendingCategory() {
        mPendingCategory = -1;
        mBinding.pager.removeCallbacks(mCategoryTask);
    }

    /** 统一返回「首页」胶囊：分页切回 0，焦点落回该胶囊。 */
    private void backToHomeCapsule() {
        clearPendingPoster();
        cancelPendingCategory();
        mHeader.focusType(HomeHeader.PAGE_HOME);
        showCategory(0);
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
        for (int i = 0; i < position; i++) if (mAdapter.get(i) instanceof ListRow) return false;
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

    // ------------------------------------------------------------ 最近观看数据

    private void getHistory() {
        getHistory(false);
    }

    private void getHistory(boolean renew) {
        List<History> items = History.get();
        mHeroRotateItems = items;
        if (renew) {
            mHistoryAdapter = new ArrayObjectAdapter(mPresenter = new HistoryPresenter(this));
            if (hasHistoryRow()) mAdapter.removeItems(getHistoryIndex(), 1);
        }
        boolean exist = hasHistoryRow();
        if (items.isEmpty()) {
            if (exist) mAdapter.removeItems(getHistoryIndex(), 1);
            showEmptyHint();
            exist = false;
        } else {
            if (hasEmptyHint()) mAdapter.removeItems(getHistoryIndex(), 1);
            if (!exist) {
                mAdapter.add(getHistoryIndex(), new ListRow(mHistoryAdapter));
                exist = true;
            }
        }
        ArrayObjectAdapter target = exist
                ? (ArrayObjectAdapter) ((ListRow) mAdapter.get(getHistoryIndex())).getAdapter()
                : mHistoryAdapter;
        target.setItems(items, new BaseDiffCallback<History>());
        refreshRecentLayout();
        // 列表变动后轮换下标可能越界，回到第一张；焦点不在海报时继续轮换。
        if (mHeroRotating) {
            int index = findHeroWithArt(mHeroRotateIndex, true);
            if (index >= 0) {
                mHeroRotateIndex = index;
                showHeroAt(index);
            }
        }
        resumeHeroRotateIfIdle();
    }

    /**
     * 行数 / 行高变化时必须让网格重新测量并重算板块高度，
     * 否则 Leanback 的 BaseGridView 会沿用旧高度，板块位置就不贴底了。
     */
    private void refreshRecentLayout() {
        mBinding.recycler.requestLayout();
        // 板块高度变了，logo 的居中基准与贴底布局都要跟着重算。
        mBinding.recycler.post(this::applyLogoPlacement);
        scheduleRecentBottomLayout();
    }

    private boolean hasHistoryRow() {
        int row = getHistoryIndex();
        return row > 0 && row < mAdapter.size() && mAdapter.get(row) instanceof ListRow;
    }

    private boolean hasEmptyHint() {
        int row = getHistoryIndex();
        return row > 0 && row < mAdapter.size() && mAdapter.get(row) instanceof String;
    }

    /** 最近观看为空时保留一行提示，避免整页只剩一个标题显得像没加载出来。 */
    private void showEmptyHint() {
        if (hasEmptyHint()) return;
        mAdapter.add(getHistoryIndex(), ResUtil.getString(R.string.error_empty));
    }

    private void setHistoryDelete(boolean delete) {
        mPresenter.setDelete(delete);
        mHistoryAdapter.notifyArrayItemRangeChanged(0, mHistoryAdapter.size());
    }

    private void clearHistory() {
        if (hasHistoryRow()) mAdapter.removeItems(getHistoryIndex(), 1);
        History.clear(VodConfig.getCid());
        mPresenter.setDelete(false);
        mHistoryAdapter.clear();
        mHeroRotateItems = Collections.emptyList();
        stopHeroRotate();
        updateBackdrop(null, null);
        updateLogo(null, null);
        showEmptyHint();
        refreshRecentLayout();
    }

    private int getHistoryIndex() {
        return mAdapter.indexOf(R.string.home_history) + 1;
    }

    // ------------------------------------------------------------ 事件

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
                refreshRecommend();
                mHeader.setTitle();
                break;
            case HISTORY:
                getHistory();
                break;
            case SIZE:
                refreshRecommend();
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

    // ------------------------------------------------------------ 顶部导航 Host

    /** 导航即切页：胶囊 0 = 最近观看，胶囊 n(≥1) = 分页 n−1。 */
    @Override
    public void onTypeSelected(Class item, int position) {
        boolean changed = position != lastTypePosition;
        lastTypePosition = position;
        if (position <= 0 || item == null || item.isHome()) {
            if (changed) clearPendingPoster();
            showRecent();
            return;
        }
        if (changed) clearPendingPoster();
        // 去抖：长按左右快速移动时只对最终停留的分类切换分页，避免并发加载多分页而崩溃。
        mPendingCategory = position - 1;
        mBinding.pager.removeCallbacks(mCategoryTask);
        mBinding.pager.postDelayed(mCategoryTask, 100);
    }

    private void applyCategory(int index) {
        if (isFinishing() || isDestroyed()) return;
        if (mBinding.pager.getAdapter() == null) return;
        if (index < 0 || index >= mBinding.pager.getAdapter().getCount()) return;
        showCategory(index);
    }

    /** 胶囊 ↓：优先回到返回前记住的那张海报，否则进入当前页第一张海报。 */
    @Override
    public boolean onTypeDown(int position) {
        if (restorePendingPoster()) return true;
        if (position <= 0) return focusRecentFirstPoster();
        int index = position - 1;
        // 导航切换有 100ms 去抖：紧跟着按 ↓ 时分页可能还没落地，这里先落地再等一帧，
        // 避免 instantiateItem 去建一个非当前页的 Fragment（快速横移时并发实例化会崩）。
        if (mBinding.pager.getCurrentItem() != index) {
            showCategory(index);
            mBinding.pager.post(() -> {
                PageFocus target = getPage(index);
                if (target != null) target.focusFirstPoster();
            });
            return true;
        }
        PageFocus page = getPage(index);
        return page != null && page.focusFirstPoster();
    }

    /** 当前页面实际对应的胶囊：最近观看=0，其余=分页下标+1。 */
    @Override
    public int getTypePosition() {
        return mHeader == null ? 0 : Math.max(0, mHeader.getSelected());
    }

    /** 内容区第一排海报按上键：焦点回当前胶囊。 */
    @Override
    public boolean onFirstRowUp() {
        return focusCapsule(mHeader.getSelected());
    }

    @Override
    public void onTypeClick(Class item, int position) {
        if (position <= 0 || item == null || item.isHome()) {
            showRecent();
            return;
        }
        // 「首页」没有二级筛选：确认键先确保落在首页分页，再刷新内容。
        if (item.isRecommend()) {
            showCategory(0);
            refreshRecommend();
            return;
        }
        if (mHeader.getSelected() != position) {
            showCategory(position - 1);
            return;
        }
        toggleFilter(item, position);
    }

    /** 取当前分页（统一按 PageFocus 契约，推荐页与分类页无差别）。 */
    private PageFocus getPage(int pagerIndex) {
        if (mBinding.pager.getAdapter() == null) return null;
        if (pagerIndex < 0 || pagerIndex >= mBinding.pager.getAdapter().getCount()) return null;
        try {
            Object fragment = mBinding.pager.getAdapter().instantiateItem(mBinding.pager, pagerIndex);
            return fragment instanceof PageFocus ? (PageFocus) fragment : null;
        } catch (Exception e) {
            // 快速滑动导致适配器状态瞬时不一致时，放弃本次访问，避免进程崩溃。
            return null;
        }
    }

    private FolderFragment getFolder(int pagerIndex) {
        PageFocus page = getPage(pagerIndex);
        return page instanceof FolderFragment ? (FolderFragment) page : null;
    }

    private void toggleFilter(Class item, int adapterPosition) {
        FolderFragment folder = getFolder(adapterPosition - 1);
        if (folder == null) return;
        clearPendingPoster();
        item.setFilter(!item.getFilter());
        folder.toggleFilter(item.getFilter());
        mHeader.getAdapter().notifyItemRangeChanged(adapterPosition, 1);
    }

    /** 当前分类是否展开了二级筛选（两枚固定胶囊不参与）。 */
    private boolean isFilterVisible() {
        int position = mHeader.getSelected();
        if (position <= 1 || position >= mHeader.getAdapter().getItemCount()) return false;
        return mHeader.getAdapter().get(position).getFilter();
    }

    private void closeFilter() {
        int position = mHeader.getSelected();
        if (position <= 1 || position >= mHeader.getAdapter().getItemCount()) return;
        Class item = mHeader.getAdapter().get(position);
        if (!item.getFilter()) return;
        toggleFilter(item, position);
    }

    @Override
    public void onTypeRefresh(Class item) {
        if (item.isHome()) {
            getHistory();
            return;
        }
        if (item.isRecommend()) {
            refreshRecommend();
            return;
        }
        PageFocus page = getPage(mBinding.pager.getCurrentItem());
        if (page != null) page.onRefresh();
    }

    /** 「最近观看」页：从其它分页回来时把选中项复位到第一张（不抢焦点）。 */
    private void showRecent() {
        mLastBackAt = 0; // 离开首页后重置「连按两次返回退出」的计时窗口
        boolean returning = mBinding.pager.getVisibility() == View.VISIBLE;
        mBinding.pager.setVisibility(View.GONE);
        // 兜底：内容态下回本页必须保证网格可见，避免被分页里的滚动逻辑误置 GONE 后一片空白。
        if (mBinding.progressLayout.isContent()) mBinding.recycler.setVisibility(View.VISIBLE);
        mBinding.progressLayout.setVisibility(View.VISIBLE);
        if (returning) resetRecentPoster();
        // 回到本页：等焦点复位后再恢复背景轮换。
        App.post(this::resumeHeroRotateIfIdle);
    }

    private void showCategory(int index) {
        if (mBinding.pager.getAdapter() == null) return;
        if (index < 0 || index >= mBinding.pager.getAdapter().getCount()) return;
        stopHeroRotate(); // 离开「最近观看」板块：停止轮换，并清掉该板块的刮削素材
        clearHeroArt();
        boolean alreadyShown = mBinding.pager.getVisibility() == View.VISIBLE && mBinding.pager.getCurrentItem() == index;
        mBinding.progressLayout.setVisibility(View.GONE);
        mBinding.pager.setVisibility(View.VISIBLE);
        if (mBinding.pager.getCurrentItem() != index) mBinding.pager.setCurrentItem(index);
        // 已经停在该页时 onPageSelected 不会触发，这里本该补一次复位；但导航去抖任务可能在
        // 「↓ 刚把焦点送进海报」之后才重复到达，重复复位会把刚落好的焦点又拨回第一张，故跳过。
        if (alreadyShown) return;
        mLastBackAt = 0; // 真正切页后重置退出计时，避免上一次在首页的提示误伤本次返回
        final int page = index;
        mBinding.pager.post(() -> {
            PageFocus target = getPage(page);
            if (target != null) target.resetToFirstPoster();
        });
    }

    // ------------------------------------------------------------ 最近观看交互

    @Override
    public void onItemClick(History item) {
        VideoActivity.start(this, item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic());
    }

    @Override
    public void onItemDelete(History item) {
        mHistoryAdapter.remove(item.delete());
        if (mHistoryAdapter.size() > 0) return;
        if (hasHistoryRow()) mAdapter.removeItems(getHistoryIndex(), 1);
        mPresenter.setDelete(false);
        showEmptyHint();
        refreshRecentLayout();
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
        refreshRecommend();
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
        resumeHeroRotateIfIdle();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mHeader.onPause();
        stopHeroRotate();
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
            // 海报返回：焦点收回当前胶囊，并记住这张海报；再按 ↓ 可回到它。
            View poster = getCurrentFocus();
            focusCapsule(mHeader.getSelected());
            mPendingPoster = poster;
        } else if (!isTypeBarFocused()) {
            // 焦点在品牌行（logo / 搜索 / 收藏 / 连接 / 设置）：先回导航栏。
            focusCapsule(mHeader.getSelected());
        } else if (isHomeCapsule()) {
            // 首页胶囊：连按两次返回退出应用，先提示、2 秒内再按一次退出
            long now = SystemClock.elapsedRealtime();
            if (now - mLastBackAt <= BACK_EXIT_WINDOW) {
                finish();
            } else {
                mLastBackAt = now;
                Notify.show(R.string.back_exit_hint);
            }
        } else {
            // 其余所有胶囊（最近观看 / 各内容分类页）统一返回「首页」胶囊。
            backToHomeCapsule();
        }
    }

    @Override
    protected void onDestroy() {
        stopHeroRotate();
        TmdbApi.removeOnFetched(mTmdbRefresh);
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
            String key = VodConfig.get().getHome().getKey();
            // 分页 0 是「首页」独立页，它继承 FolderFragment 的整套分类页框架。
            if (position == 0) return RecommendFragment.newInstance(key);
            List<Class> types = mHeader.getAdapter().getTypes();
            if (types.isEmpty()) return RecommendFragment.newInstance(key);
            int index = Math.max(0, Math.min(position - 1, types.size() - 1));
            return FolderFragment.newInstance(key, types.get(index));
        }

        @Override
        public int getCount() {
            return mHeader.getAdapter().getTypes().size() + 1;
        }

        @Override
        public int getItemPosition(@NonNull Object object) {
            // 切换源后分类列表会变化，强制重建分页，避免残留旧分类内容。
            return POSITION_NONE;
        }
    }
}
