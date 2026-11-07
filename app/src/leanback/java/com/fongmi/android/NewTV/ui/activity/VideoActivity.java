package com.fongmi.android.NewTV.ui.activity;

import android.animation.TimeInterpolator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.view.OneShotPreDrawListener;
import androidx.fragment.app.FragmentActivity;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.C;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Player;
import androidx.media3.ui.PlayerSeekView;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.bumptech.glide.request.transition.Transition;
import com.fongmi.android.NewTV.App;
import com.fongmi.android.NewTV.R;
import com.fongmi.android.NewTV.api.DanmakuApi;
import com.fongmi.android.NewTV.api.SiteApi;
import com.fongmi.android.NewTV.api.TmdbApi;
import com.fongmi.android.NewTV.api.TmdbStore;
import com.fongmi.android.NewTV.api.config.VodConfig;
import com.fongmi.android.NewTV.bean.Danmaku;
import com.fongmi.android.NewTV.bean.DanmakuTitle;
import com.fongmi.android.NewTV.bean.Episode;
import com.fongmi.android.NewTV.bean.Flag;
import com.fongmi.android.NewTV.bean.History;
import com.fongmi.android.NewTV.bean.Keep;
import com.fongmi.android.NewTV.bean.Parse;
import com.fongmi.android.NewTV.bean.Result;
import com.fongmi.android.NewTV.bean.Site;
import com.fongmi.android.NewTV.bean.Sub;
import com.fongmi.android.NewTV.bean.TmdbArt;
import com.fongmi.android.NewTV.bean.Vod;
import com.fongmi.android.NewTV.databinding.ActivityVideoBinding;
import com.fongmi.android.NewTV.db.AppDatabase;
import com.fongmi.android.NewTV.event.RefreshEvent;
import com.fongmi.android.NewTV.impl.CustomTarget;
import com.fongmi.android.NewTV.impl.DanmakuListener;
import com.fongmi.android.NewTV.model.VideoViewModel;
import com.fongmi.android.NewTV.playback.ExternalPlayback;
import com.fongmi.android.NewTV.playback.PlaybackAction;
import com.fongmi.android.NewTV.playback.PlaybackIntent;
import com.fongmi.android.NewTV.playback.PlaybackReset;
import com.fongmi.android.NewTV.playback.PlaybackResult;
import com.fongmi.android.NewTV.playback.vod.VodDetailResult;
import com.fongmi.android.NewTV.playback.vod.VodPlayRequest;
import com.fongmi.android.NewTV.playback.vod.VodPlaybackController;
import com.fongmi.android.NewTV.playback.vod.VodPlaybackHost;
import com.fongmi.android.NewTV.playback.vod.VodPlaybackMedia;
import com.fongmi.android.NewTV.player.media.MediaItemFactory;
import com.fongmi.android.NewTV.player.media.PlaySpec;
import com.fongmi.android.NewTV.service.PlaybackService;
import com.fongmi.android.NewTV.setting.DanmakuSetting;
import com.fongmi.android.NewTV.setting.PlayerSetting;
import com.fongmi.android.NewTV.setting.TmdbSetting;
import com.fongmi.android.NewTV.ui.adapter.ArrayAdapter;
import com.fongmi.android.NewTV.ui.adapter.EpisodeAdapter;
import com.fongmi.android.NewTV.ui.adapter.FlagAdapter;
import com.fongmi.android.NewTV.ui.adapter.PartAdapter;
import com.fongmi.android.NewTV.ui.adapter.QualityAdapter;
import com.fongmi.android.NewTV.ui.adapter.QuickAdapter;
import com.fongmi.android.NewTV.ui.custom.FeatheredImageView;
import com.fongmi.android.NewTV.ui.custom.LeanbackTimeBar;
import com.fongmi.android.NewTV.ui.custom.VodInput;
import com.fongmi.android.NewTV.ui.custom.CustomMovement;
import com.fongmi.android.NewTV.ui.danmaku.DanmakuHeat;
import com.fongmi.android.NewTV.ui.helper.ControlFocusEffect;
import com.fongmi.android.NewTV.ui.sheet.DanmakuSearchSheet;
import com.fongmi.android.NewTV.ui.sheet.PanelSheet;
import com.fongmi.android.NewTV.ui.sheet.SheetList;
import com.fongmi.android.NewTV.ui.sheet.TrackSheet;
import com.fongmi.android.NewTV.ui.sheet.VideoSheet;
import com.fongmi.android.NewTV.ui.dialog.ChapterDialog;
import com.fongmi.android.NewTV.ui.dialog.ContentDialog;
import com.fongmi.android.NewTV.ui.dialog.DecoderSettingDialog;
import com.fongmi.android.NewTV.ui.dialog.EditionDialog;
import com.fongmi.android.NewTV.ui.dialog.ParseDialog;
import com.fongmi.android.NewTV.ui.dialog.PlayerEngineDialog;
import com.fongmi.android.NewTV.utils.Clock;
import com.fongmi.android.NewTV.utils.FileChooser;
import com.fongmi.android.NewTV.utils.FileUtil;
import com.fongmi.android.NewTV.utils.ImgUtil;
import com.fongmi.android.NewTV.utils.KeyUtil;
import com.fongmi.android.NewTV.utils.Notify;
import com.fongmi.android.NewTV.utils.PartUtil;
import com.fongmi.android.NewTV.utils.ResUtil;
import com.fongmi.android.NewTV.utils.Sniffer;
import com.fongmi.android.NewTV.utils.Traffic;
import com.fongmi.android.NewTV.utils.Util;
import com.github.bassaer.library.MDColor;
import com.github.catvod.utils.UriUtil;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class VideoActivity extends PlaybackActivity implements VodPlaybackHost, VodInput.Listener, ParseDialog.Listener, ArrayAdapter.OnClickListener, FlagAdapter.OnClickListener, EpisodeAdapter.OnClickListener, QualityAdapter.OnClickListener, QuickAdapter.OnClickListener, Clock.Callback, DanmakuListener {

    /** 控制条弹出后的自动隐藏延时：10 秒（原为 5 秒）。 */
    private static final long HIDE_DELAY = 10_000L;
    /**
     * 控制条整体下压量（物理像素）：把 {@code view_control_vod} 根部原本 48dp 的底部内边距
     * 收掉一截，让进度条与按钮行更贴近屏幕下沿，画面底部不留一片纯黑。
     *
     * <p>用 px 而不是 dp：这个量是照着「屏幕像素」定的，换一台密度不同的设备时下压的观感应当一致。
     */
    private static final int CONTROL_BOTTOM_LIFT_PX = 100;
    /**
     * 控制条底部的最小安全留白：下压到极限也要保留这么高，
     * 否则按钮的焦点光晕（{@code GlowBackground}）会贴住屏幕下沿、看起来像被切掉。
     */
    private static final int CONTROL_BOTTOM_MIN_DP = 8;
    /** {@code view_control_vod.xml} 根布局的底部内边距基准值，与 XML 里的 48dp 必须一致。 */
    private static final int CONTROL_BOTTOM_BASE_DP = 48;
    /** 控制条 / 顶部信息条的滑入、滑出动画：入先快后慢（减速），出先慢后快（加速）。 */
    private static final long SLIDE_IN_DURATION = 240L;
    private static final long SLIDE_OUT_DURATION = 200L;
    private static final TimeInterpolator SLIDE_IN = new PathInterpolator(0.05f, 0.7f, 0.1f, 1f);
    private static final TimeInterpolator SLIDE_OUT = new PathInterpolator(0.3f, 0f, 0.8f, 0.15f);
    /** 详情页右上角背景图的宽度占屏宽比例：直接复用首页那个常量（高度按原图比例换算）。 */
    private static final float DETAIL_HERO_WIDTH_RATIO = FeatheredImageView.WIDTH_RATIO;
    /**
     * 详情页背景图的最终不透明度：30%（即 70% 透明）。
     *
     * <p>与 {@code activity_video.xml} 里 {@code videoHero} 的 {@code android:alpha} 必须一致 ——
     * 布局里那份是静态预览值，运行时一律由这里的渐入动画收到这个终值。</p>
     */
    private static final float DETAIL_HERO_ALPHA = 0.3f;
    /** 背景图渐入时长：比控件自身的过渡稍长，让「浮现」是这一帧之后唯一还在动的东西。 */
    private static final long DETAIL_HERO_FADE_DURATION = 420L;
    /**
     * 兜底等待：内容首帧迟迟不来（详情接口超时、页面被遮挡没有绘制）时，最多等这么久就把背景图
     * 直接摆到最终透明度 —— 宁可少一次渐入，也不要一直停在隐形态。
     */
    private static final long DETAIL_HERO_READY_TIMEOUT = 2000L;
    /** 倍速浮层的候选值，升序；菜单里从下往上排，所以显示时倒序。 */
    private static final float[] SPEEDS = {0.5f, 0.8f, 1.0f, 1.2f, 1.5f, 2.0f, 3.0f, 5.0f};
    /** 倍速浮层一屏显示的行数，其余靠滚动查看（8 档 > 5 行，因此打开时可把 1.0x 停在最底部）。 */
    private static final int SPEED_VISIBLE_ROWS = 5;
    /**
     * 浮层列表行的左右内边距合计，三层各 12dp、两侧共 72dp：
     * 浮层容器（{@code view_video_sheet} 的 padding 12dp）+ 行容器（{@code view_sheet_item} 的 12dp）
     * + 文字自身（{@code sheetItemText} 的 12dp）。
     *
     * <p>早先只算了后两层（48dp），漏掉浮层容器那 24dp，结果列条总比最长的一行窄 24dp：
     * 明明按最长行量好了宽度，实际却每一行都差一两个字、被迫跑马灯。这里补齐。
     */
    private static final int SHEET_ROW_CHROME_DP = 72;
    /** 「正在播放」指示器槽位宽度（10dp 图标 + 6dp 间距），只加宽列条、不占文字宽度。 */
    private static final int SHEET_PLAYING_SLOT_DP = 16;

    private TextView mSheetProbe;
    private final ActivityResultLauncher<Intent> mSubtitleLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> FileChooser.getUri(result, this::setSubtitleFile));

    private ActivityVideoBinding mBinding;
    private VideoSheet mSheet;
    /** 控制条焦点特效句柄：浮层打开期间用它把锚点按钮钉在「选中态」。 */
    private ControlFocusEffect.Effect mControlFocusEffect;
    /** 当前被钉住的锚点按钮（同一时刻只允许一个），用于维护 state_activated。 */
    private View mPinnedAnchor;
    private PanelSheet mPanelSheet;
    private TrackSheet mTrackSheet;
    /** 弹幕搜索：与 TrackSheet 共用同一个 VideoSheet 宿主的另一页内容。 */
    private DanmakuSearchSheet mDanmakuSearchSheet;
    private SheetList mEpisodeList;
    private SheetList mSpeedList;
    private final List<Episode> mEpisodes = new ArrayList<>();
    private VideoViewModel mViewModel;
    private VodPlaybackController mVod;
    private FlagAdapter mFlagAdapter;
    private EpisodeAdapter mEpisodeAdapter;
    private QualityAdapter mQualityAdapter;
    private ArrayAdapter mArrayAdapter;
    private PartAdapter mPartAdapter;
    private QuickAdapter mQuickAdapter;
    private ViewGroup.LayoutParams mFrameParams;
    private VodInput mInput;
    private Clock mClock;
    private View mFocus1;
    private View mFocus2;
    private Runnable mR1;
    private Runnable mR2;
    private Runnable mR3;
    private Runnable mR4;
    private History mHistory;
    private boolean fullscreen;
    private boolean mControlShown;
    private boolean webFullscreen;
    private boolean externalPlaybackCompleted;
    private boolean useParse;
    /** TMDB 抓取：等「开始播放」与「片名关键字就绪」两个条件都满足才发起，每部片只抓一次。 */
    private String mTmdbKeyword;
    private String mTmdbPosterName;
    private String mTmdbFetchedKey;
    private boolean mTmdbKeywordReady;
    private boolean mTmdbPlaybackStarted;
    /** 这一次抓取由用户点「TMDB刮削」按钮发起：结果无论成功失败都要提示，不受设置里的提示开关限制。 */
    private boolean mTmdbManual;
    /** 当前显示的详情页背景图对应的 TMDB 缓存文件名；null = 没有可显示的背景图。 */
    private String mHeroArt;
    /** 背景图素材已载入控件、正等内容首帧（或已显示）。 */
    private boolean mHeroPrepared;
    /** 背景图已经渐入（或兜底直接显示）过一次；避免每条回调都把透明度重播一遍。 */
    private boolean mHeroShown;
    /** 内容层（封面 / 标题 / 控制栏）已经真正绘制过一帧，这是背景图渐入的放行条件。 */
    private boolean mContentDrawn;
    /** 内容首帧监听已挂上，防止重复注册 OneShotPreDrawListener。 */
    private boolean mContentDrawnPending;
    /**
     * 页面所有控件是否已经加载完成（内容层首帧真正上屏）。
     * 背景位图解码 / 羽化比较重，就绪前一律只登记片名、不加载，避免和控件初始化抢主线程。
     */
    private boolean mHeroReady;
    /** 控件就绪前收到的最后一次背景请求片名，就绪后补做一次加载。 */
    private String mPendingHeroName;
    /**
     * 兜底：内容首帧没来（接口超时 / 页面未绘制）时，直接把背景图摆到最终透明度，
     * 保证「有素材却始终看不见」这种异常状态不会持续。超时同样先补做加载，否则无图可显示。
     */
    private final Runnable mHeroFallback = () -> {
        markHeroReady();
        fadeInDetailHero(false);
    };
    /**
     * 刮削完成后的背景图刷新：不区分这次抓取由谁发起（首页海报聚焦 / 本页播放起播），
     * 结果落地后都按当前片名重新对一次缓存 —— 否则「首页发起、详情页正开着」时会漏掉这张图。
     */
    private final Runnable mHeroRefresh = () -> updateDetailHero(detailName());
    private final Runnable mTmdbFallback = this::onTmdbDanmakuTimeout;
    /** 弹幕热力数据变化：解析完成 / 换集清空都在主线程回调，这里只管把数据交给进度条。 */
    private final DanmakuHeat.Listener mHeatListener = this::applyHeatmap;

    public static void push(FragmentActivity activity, String text) {
        if (TextUtils.isEmpty(text)) return;
        Uri uri = UriUtil.uri(text);
        if (FileChooser.isFileSource(uri)) file(activity, uri);
        else start(activity, Sniffer.getUrl(text));
    }

    public static void file(FragmentActivity activity, Uri fileUri) {
        if (fileUri == null || activity.isFinishing() || activity.isDestroyed()) return;
        start(activity, SiteApi.LOCAL, fileUri.toString(), FileUtil.getDisplayName(fileUri));
    }

    public static void cast(Activity activity, History history) {
        start(activity, history.getSiteKey(), history.getVodId(), history.getVodName(), history.getVodPic(), null, false, true);
    }

    public static void collect(Activity activity, String key, String id, String name, String pic) {
        start(activity, key, id, name, pic, null, true, false);
    }

    public static void start(Activity activity, String url) {
        start(activity, SiteApi.PUSH, url, url);
    }

    public static void start(Activity activity, String key, String id, String name) {
        start(activity, key, id, name, null);
    }

    public static void start(Activity activity, String key, String id, String name, String pic) {
        start(activity, key, id, name, pic, null);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark) {
        start(activity, key, id, name, pic, mark, false, false);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, boolean collect, boolean cast) {
        Intent intent = new Intent(activity, VideoActivity.class);
        intent.putExtra("collect", collect);
        intent.putExtra("cast", cast);
        intent.putExtra("mark", mark);
        intent.putExtra("name", name);
        intent.putExtra("key", key);
        intent.putExtra("id", id);
        putPic(intent, pic);
        activity.startActivity(intent);
    }

    private static void putPic(Intent intent, String pic) {
        intent.removeExtra("pic");
        pic = ImgUtil.cache(pic);
        if (!TextUtils.isEmpty(pic)) intent.putExtra("pic", pic);
    }

    private boolean isCast() {
        return getIntent().getBooleanExtra("cast", false);
    }

    private String getName() {
        return Objects.toString(getIntent().getStringExtra("name"), "");
    }

    private String getPic() {
        return Objects.toString(getIntent().getStringExtra("pic"), "");
    }

    private String getMark() {
        return Objects.toString(getIntent().getStringExtra("mark"), "");
    }

    private String getKey() {
        return Objects.toString(getIntent().getStringExtra("key"), "");
    }

    private String getId() {
        return Objects.toString(getIntent().getStringExtra("id"), "");
    }

    private boolean isSameVideo(Intent intent) {
        String key = Objects.toString(intent.getStringExtra("key"), "");
        String id = Objects.toString(intent.getStringExtra("id"), "");
        return TextUtils.equals(key, getKey()) && TextUtils.equals(id, getId());
    }

    @Override
    public String getHistoryKey() {
        return getKey().concat(AppDatabase.SYMBOL).concat(getId()).concat(AppDatabase.SYMBOL) + VodConfig.getCid();
    }

    private Site getSite() {
        return VodConfig.get().getSite(getKey());
    }

    private Episode getEpisode() {
        return mEpisodeAdapter.getActivated();
    }

    private int getScale() {
        return mHistory != null && mHistory.getScale() != -1 ? Math.clamp(mHistory.getScale(), PlayerSetting.MIN_SCALE, PlayerSetting.MAX_SCALE) : PlayerSetting.getScale();
    }

    private void setScale(int scale) {
        mVod.setScale(scale);
        mBinding.player.setResizeMode(PlayerSetting.getScaleMode(scale));
        mBinding.control.action.scale.setText(ResUtil.getStringArray(R.array.select_scale)[scale]);
    }

    @Override
    public boolean isFromCollect() {
        return getIntent().getBooleanExtra("collect", false);
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityVideoBinding.inflate(getLayoutInflater());
    }

    @Override
    protected PlaybackService.NavigationCallback getNavigationCallback() {
        return mNavigationCallback;
    }

    @Override
    protected PlayerView getPlayerView() {
        return mBinding.player;
    }

    @Override
    protected PlayerSeekView getSeekView() {
        return mBinding.control.seek;
    }

    @Override
    protected void onServiceConnected() {
        mVod.onPlaybackServiceReady();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (TextUtils.isEmpty(intent.getStringExtra("id")) || isSameVideo(intent)) return;
        mInput.cancelSeek();
        externalPlaybackCompleted = false;
        saveHistory(true);
        mVod.reset();
        setIntent(intent);
        renderFallbackName(getName());
        // 换片：背景图状态复位，新片重新走一遍「等内容首帧再渐入」，不会沿用上一部的透明度。
        resetDetailHero();
        updateDetailHero(getName());
        updateNavigationKey();
        checkId();
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        mFrameParams = mBinding.video.getLayoutParams();
        applyControlBottomInset();
        mClock = Clock.create(mBinding.widget.clock);
        mInput = VodInput.create(this, this);
        mR1 = this::hideControl;
        mR2 = this::updateFocus;
        mR3 = this::setTraffic;
        mR4 = this::showEmpty;
        setRecyclerView();
        setVideoView();
        setViewModel();
        renderFallbackName(getName());
        // 先按 intent 片名登记背景请求；真正加载要等该页所有控件加载完成（见 markHeroReady）。
        updateDetailHero(getName());
        // 兜底计时：内容首帧迟迟不来（接口卡住 / 页面没绘制）时不至于一直看不见这张图。
        App.post(mHeroFallback, DETAIL_HERO_READY_TIMEOUT);
        // 抓取结果可能由首页发起、也可能由本页发起：两种情况都要能实时回填这张背景图。
        TmdbApi.addOnFetched(mHeroRefresh);
        // 弹幕热力图：解析完成 / 换集清空都会回调，先把已有数据贴上（换集后是空）
        DanmakuHeat.addListener(mHeatListener);
        applyHeatmap();
        checkCast();
        checkId();
    }

    /**
     * 把控制条整体向下压 {@link #CONTROL_BOTTOM_LIFT_PX} px。
     *
     * <p>控制条根部是 {@code layout_gravity=bottom} 的 {@code wrap_content} 面板，底边恒贴屏幕下沿，
     * 所以「整体下移」等价于减少它的底部内边距：面板高度随之变矮、背景渐变的上沿一起下移，
     * 进度条与按钮行都会向下走，不会只挪内容却留下一块空背景。
     *
     * <p>底部留白始终不少于 {@link #CONTROL_BOTTOM_MIN_DP}，低密度设备上 48dp 本身就不足 100px 时
     * 只会把留白压到下限，不会出现负 padding（负值在布局里会被当 0，反而丢掉了安全区）。
     */
    private void applyControlBottomInset() {
        View control = mBinding.control.getRoot();
        int base = ResUtil.dp2px(CONTROL_BOTTOM_BASE_DP);
        int bottom = Math.max(ResUtil.dp2px(CONTROL_BOTTOM_MIN_DP), base - CONTROL_BOTTOM_LIFT_PX);
        control.setPaddingRelative(control.getPaddingStart(), control.getPaddingTop(), control.getPaddingEnd(), bottom);
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    protected void initEvent() {
        mBinding.keep.setOnClickListener(view -> onKeep());
        mBinding.video.setOnClickListener(view -> onVideo());
        mBinding.change.setOnClickListener(view -> onChange());
        mBinding.tmdb.setOnClickListener(view -> onTmdbRetry());
        mBinding.content.setOnClickListener(view -> onContent());
        mBinding.control.action.text.setOnClickListener(this::onTrack);
        mBinding.control.action.audio.setOnClickListener(this::onTrack);
        mBinding.control.action.video.setOnClickListener(this::onTrack);
        // 片头 / 片尾统一用「下键」调节时长：两者按下键都让标记沿时间轴向前推进
        // （片头 片头→片尾；片尾 片尾→片头）。上键不再参与调节，交还系统做焦点移动。
        mBinding.control.action.ending.setDownListener(this::onEndingAdd);
        mBinding.control.action.ending.setUpListener(this::onEndingSub);
        mBinding.control.action.opening.setDownListener(this::onOpeningAdd);
        mBinding.control.action.opening.setUpListener(this::onOpeningSub);
        mBinding.control.action.playPause.setOnClickListener(view -> onPlayPause());
        mBinding.control.action.panelBtn.setOnClickListener(view -> onControlPanel());
        // 弹幕：单击 = 当前播放的快捷开关，长按 = 打开「选择弹幕」浮层
        mBinding.control.action.danmaku.setOnClickListener(view -> onDanmakuToggle());
        mBinding.control.action.danmaku.setOnLongClickListener(view -> {
            onDanmakuSheet();
            return true;
        });
        // 全局弹幕开关关闭时按钮置灰：一旦被「选中」（获得焦点）即提示去设置里开启。
        mBinding.control.action.danmaku.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus && !DanmakuSetting.isLoad()) Notify.show(R.string.danmaku_disabled_hint);
        });
        mBinding.control.action.next.setOnClickListener(view -> checkNext());
        mBinding.control.action.next.setOnLongClickListener(view -> {
            onEpisodeSheet(view);
            return true;
        });
        // 选集：点击在按钮正上方弹出选集浮层（与倍速菜单同一容器与动效）
        mBinding.control.action.episodes.setOnClickListener(view -> onEpisodeSheet(view));
        mBinding.control.action.prev.setOnClickListener(view -> checkPrev());
        mBinding.control.action.prev.setOnLongClickListener(view -> {
            onEpisodeSheet(view);
            return true;
        });
        mBinding.control.action.scale.setOnClickListener(view -> onScale());
        mBinding.control.action.speed.setOnClickListener(view -> onSpeed());
        mBinding.control.action.reset.setOnClickListener(view -> onReset());
        mBinding.control.action.replay.setOnClickListener(view -> onReplay());
        mBinding.control.action.parse.setOnClickListener(view -> onParse());
        mBinding.control.action.player.setOnClickListener(view -> onPlayer());
        // 调试(bug)：与上游 PlayerEngineDialog 的 ic_action_debug 一致，点击切换播放调试信息浮层
        mBinding.control.action.debug.setOnClickListener(view -> {
            toggleDebugView();
            view.setSelected(PlayerSetting.isDebug());
        });
        mBinding.control.action.debug.setSelected(PlayerSetting.isDebug());
        mBinding.control.action.decoder.setOnClickListener(view -> onDecoder());
        mBinding.control.action.ending.setOnClickListener(view -> onEnding());
        mBinding.control.action.repeat.setOnClickListener(view -> onRepeat());
        mBinding.control.action.edition.setOnClickListener(view -> onEdition());
        mBinding.control.action.chapter.setOnClickListener(view -> onChapter());
        mBinding.control.action.discMenu.setOnClickListener(this::onDiscMenu);
        mBinding.control.action.discMenu.setOnLongClickListener(this::onDiscMenuLong);
        mBinding.control.action.opening.setOnClickListener(view -> onOpening());
        mBinding.control.action.speed.setOnLongClickListener(view -> onSpeedLong());
        mBinding.control.action.ending.setOnLongClickListener(view -> onEndingReset());
        mBinding.control.action.opening.setOnLongClickListener(view -> onOpeningReset());
        mBinding.video.setOnTouchListener((view, event) -> mInput.onTouchEvent(event));
        mBinding.flag.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                if (mFlagAdapter.getItemCount() > 0) onItemClick(mFlagAdapter.get(position));
            }
        });
        mBinding.episode.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                if (child != null && mBinding.video != mFocus1) mFocus1 = child.itemView;
            }
        });
        mBinding.array.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                if (mEpisodeAdapter.getItemCount() > 20 && position > 1) mBinding.episode.setSelectedPosition((position - 2) * 20);
            }
        });
        setControlFocus();
    }

    /**
     * 控制条按钮聚焦反馈。
     *
     * <p>图标按钮（含播放/暂停）：放大 + 沿笔画辉光，图标染成品牌橙；
     * 播放键保留原始配色，只把光晕放大一档；
     * 片头、片尾只放大不发光，靠文字变橙表达焦点。
     */
    private void setControlFocus() {
        View[] glow = {
                mBinding.control.action.playPause,
                mBinding.control.action.prev,
                mBinding.control.action.next,
                mBinding.control.action.episodes,
                mBinding.control.action.danmaku,
                mBinding.control.action.text,
                mBinding.control.action.audio,
                mBinding.control.action.speed,
                mBinding.control.action.debug,
                mBinding.control.action.panelBtn};
        View[] scaleOnly = {
                mBinding.control.action.opening,
                mBinding.control.action.ending};
        mControlFocusEffect = ControlFocusEffect.attach(mBinding.control.getRoot(), glow, scaleOnly);
        // 播放键的图标比其它按钮大一圈，光晕也要相应放大，边缘过渡才明显
        ControlFocusEffect.setHaloScale(mBinding.control.action.playPause, 1.45f);
    }

    private void setRecyclerView() {
        mBinding.flag.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.flag.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.flag.setAdapter(mFlagAdapter = new FlagAdapter(this));
        mBinding.episode.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.episode.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.episode.setAdapter(mEpisodeAdapter = new EpisodeAdapter(this));
        mBinding.quality.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.quality.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.quality.setAdapter(mQualityAdapter = new QualityAdapter(this));
        mBinding.array.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.array.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.array.setAdapter(mArrayAdapter = new ArrayAdapter(this));
        mBinding.part.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.part.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.part.setAdapter(mPartAdapter = new PartAdapter(item -> mVod.search(item)));
        mBinding.quick.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.quick.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.quick.setAdapter(mQuickAdapter = new QuickAdapter(this));
    }

    private void setVideoView() {
        setSeekNextFocusDown(R.id.playPause);
        setActionFocusBoundary(mBinding.control.action.getRoot());
        PlayerEngineDialog.setText(mBinding.control.action.player);
        bindSeekCenterKey();
        mSheet = new VideoSheet(mBinding.video, mBinding.sheet, this::onSheetShow, this::onSheetDismiss);
        mPanelSheet = new PanelSheet(this, mBinding.control.action);
        mDanmakuSearchSheet = new DanmakuSearchSheet(this, this::hideSheet);
        mTrackSheet = new TrackSheet(this, this::hideSheet, this::pickSubtitleFile, this::showDanmakuSearchSheet);
        mEpisodeList = new SheetList(this);
        // 选集：选中行上方不足一屏时顶部对齐，避免弹窗顶部出现空行（倍速列表维持贴底）
        mEpisodeList.setAvoidEmptyRows(true);
        mSpeedList = new SheetList(this);
        syncPlayPause();
        syncDanmaku();
    }

    /** 焦点在进度条上时，确定键 = 播放/暂停（DefaultTimeBar 自己不处理确定键）；顺带把滑块染成橙色，并加聚焦发光描边。 */
    private void bindSeekCenterKey() {
        View timeBar = mBinding.control.seek.findViewById(androidx.media3.ui.R.id.exo_progress);
        if (timeBar == null) return;
        if (timeBar instanceof androidx.media3.ui.DefaultTimeBar bar) {
            bar.setScrubberColor(ResUtil.getColor(R.color.control_scrubber));
            // 聚焦态不加线框：由 LeanbackTimeBar 自行绘制「条身加粗发光 + 滑块放大」
            bar.setBackground(null);
        }
        timeBar.setOnKeyListener((view, keyCode, event) -> {
            if (!KeyUtil.isActionDown(event)) return false;
            boolean center = keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER;
            if (!center) return false;
            onPlayPause();
            return true;
        });
    }

    /**
     * 把当前集的弹幕分布交给进度条。
     *
     * <p>三种情况都传 null：关掉「弹幕加载」、关掉设置里的「进度条弹幕热力图」、
     * 或者在播放页按弹幕键把弹幕本身关掉 —— 没有弹幕就没有密度可看。
     */
    private void applyHeatmap() {
        LeanbackTimeBar bar = timeBar();
        if (bar == null) return;
        boolean danmaku = service() == null ? DanmakuSetting.isShow() : player().isDanmakuEnabled();
        boolean show = DanmakuSetting.isLoad() && DanmakuSetting.isHeatmap() && danmaku;
        bar.setHeatmap(show ? DanmakuHeat.get() : null);
    }

    @Nullable
    private LeanbackTimeBar timeBar() {
        View view = mBinding.control.seek.findViewById(androidx.media3.ui.R.id.exo_progress);
        return view instanceof LeanbackTimeBar bar ? bar : null;
    }

    /**
     * 播放/暂停按钮的图标：按「播放意图」（playWhenReady）而不是「真实播放中」来画。
     *
     * <p>用 isPlaying() 时，缓冲/卡顿/切集的那几百毫秒里状态是 false，图标会先翻成播放三角再翻回来 ——
     * 看起来就是「刷新不及时 / 显示状态错误」。playWhenReady 在按下暂停键的瞬间就变了，
     * 且缓冲期间保持 true，图标因此稳定。
     */
    private void syncPlayPause() {
        boolean playing = isPlayRequested();
        // 用双态 selector：未聚焦纯白，聚焦保持橙 + 奶油渐变（ImageView 会在换图时立即套用当前焦点态）。
        mBinding.control.action.playPause.setImageResource(playing ? R.drawable.selector_control_pause : R.drawable.selector_control_play);
    }

    /**
     * 弹幕按钮：
     * 开启态 = 图标勾（selected），关闭态 = 斜线；
     * 系统弹幕总开关关闭时整颗按钮变灰（alpha 0.4）且点击无效，但仍保留焦点，避免焦点链出现空洞。
     */
    private void syncDanmaku() {
        boolean load = DanmakuSetting.isLoad();
        boolean enabled = load && (service() == null ? DanmakuSetting.isShow() : player().isDanmakuEnabled());
        mBinding.control.action.danmaku.setVisibility(View.VISIBLE);
        mBinding.control.action.danmaku.setSelected(enabled);
        mBinding.control.action.danmaku.setAlpha(load ? 1f : 0.4f);
        // 热力图跟着弹幕一起开关：弹幕关掉就没必要再显示密度曲线
        applyHeatmap();
    }

    private void setPlaybackMode() {
        if (isWebPlaybackActive()) return;
        PlaybackAction.setPlaybackMode(player(), mBinding.control.action.player);
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(VideoViewModel.class);
        mVod = mViewModel.createPlaybackController(this);
        observeWhenServiceReady(mViewModel.getDetail(), this::onDetailObserved);
        observeWhenServiceReady(mViewModel.getSearch(), this::onSearchObserved);
        observeWhenServiceReady(mViewModel.getPreload(), this::onPreloadObserved);
        observeWhenServiceReady(mViewModel.getPlayback(), this::onPlaybackObserved);
    }

    private void onDetailObserved(VodDetailResult result) {
        ExternalPlayback.applyTitle(result.result(), getIntent());
        mVod.onDetailResult(result);
    }

    private void onPlaybackObserved(PlaybackResult<VodPlayRequest> result) {
        mVod.onPlaybackResult(result);
    }

    private void onPreloadObserved(PlaybackResult<VodPlayRequest> result) {
        mVod.onPreloadResult(result);
    }

    private void onSearchObserved(Result result) {
        mVod.onSearchResult(result);
    }

    @Override
    public String getVodKey() {
        return getKey();
    }

    @Override
    public String getVodId() {
        return getId();
    }

    @Override
    public void setVodId(String id) {
        String oldKey = getHistoryKey();
        boolean owned = isPlaybackCurrent();
        getIntent().putExtra("id", id);
        if (owned) player().updateKey(oldKey, getHistoryKey());
    }

    @Override
    public String getVodName() {
        return getName();
    }

    @Override
    public String getVodPic() {
        return getPic();
    }

    @Override
    public String getVodMark() {
        return getMark();
    }

    @Override
    public boolean isSiteChangeable() {
        return getSite().isChangeable();
    }

    @Override
    public boolean isHostFinishing() {
        return isFinishing() || isDestroyed();
    }

    @Override
    public boolean isPlayerEmpty() {
        return !isWebPlaybackActive() && player().isEmpty();
    }

    @Override
    public boolean hasPlaybackSession() {
        return hasActivePlaybackSession();
    }

    @Override
    public boolean isLivePlayback() {
        return service() != null && isOwner() && player().isLive();
    }

    @Override
    public boolean canTrackPlaybackProgress() {
        return isWebPlaybackActive() ? canTrackActivePlaybackProgress() : isPlaybackCurrent() && player().isVod() && !isIsoNavigationPlayback();
    }

    @Override
    public boolean isIsoNavigationPlayback() {
        return super.isIsoNavigationPlayback();
    }

    @Override
    public boolean canPreloadNext() {
        return isPlaybackCurrent() && !ExternalPlayback.returnsResult(getIntent()) && player().canPreloadNext();
    }

    @Override
    public boolean isPlayerPlaying() {
        return isPlaybackCurrent() && !isWebPlaybackActive() && player().isPlaying();
    }

    @Override
    public long getPlayerPosition() {
        return getActivePlaybackPosition();
    }

    @Override
    public long getPlayerDuration() {
        return getActivePlaybackDuration();
    }

    @Override
    public void usePushId(String id) {
        getIntent().putExtra("key", SiteApi.PUSH).putExtra("id", id);
    }

    @Override
    public void onPlaybackRequested() {
        mInput.cancelSeek();
        showProgress();
    }

    @Override
    public void prepareSource(Vod item) {
        mInput.cancelSeek();
        ExternalPlayback.clearOptions(getIntent());
        getIntent().putExtra("key", item.getSiteKey());
        getIntent().putExtra("id", item.getId());
        putPic(getIntent(), item.getPic());
        mBinding.scroll.scrollTo(0, 0);
        mClock.setCallback(null);
        updateNavigationKey();
        player().reset();
        player().stop();
    }

    @Override
    public void stopPlaybackForRefresh() {
        mInput.cancelSeek();
        if (!isBindingOwner()) return;
        stopWebPlayback();
        service().stopPlayback(getNavigationCallback());
        player().clear();
        mClock.setCallback(null);
    }

    @Override
    public void resetPlaybackForError(String msg) {
        PlaybackReset.afterError(player(), () -> mClock.setCallback(null));
        showError(msg);
    }

    @Override
    public void replay(long position) {
        if (replayWebPlayback(position)) return;
        player().replay(position);
    }

    @Override
    public void seekPlayback(long position) {
        mInput.cancelSeek();
        long current = getPlayerPosition();
        if (current >= 0) seekTo(position - current);
    }

    @Override
    protected long endingDurationMs() {
        return mHistory == null ? 0 : mHistory.getEnding();
    }

    @Override
    public void setPlaybackEnding(long endingMs) {
        if (!isWebPlaybackActive() && isPlaybackCurrent()) player().setEnding(endingMs);
    }

    @Override
    public boolean isPlaybackEndingManaged() {
        return !isWebPlaybackActive() && isPlaybackCurrent() && player().isEndingManaged();
    }

    @Override
    public void startPlayback(Result result, boolean useParse, long startPositionMs, MediaMetadata metadata) {
        mInput.cancelSeek();
        ExternalPlayback.apply(result, getIntent());
        startPositionMs = ExternalPlayback.takePosition(getIntent(), startPositionMs);
        if (startWebPlayback(mBinding.video, result)) return;
        startPlayer(getHistoryKey(), result, useParse, getSite().getTimeout(), startPositionMs, metadata);
    }

    @Override
    public boolean preloadPlayback(Result result, long startPositionMs, MediaMetadata metadata, VodPlayRequest request) {
        return !isWebPlayback(result) && player().preload(PlaySpec.from(result, getHistoryKey(), metadata).setEnding(endingDurationMs()), startPositionMs, request);
    }

    @Override
    public boolean seekToPreloaded(VodPlayRequest request) {
        return isBindingOwner() && player().seekToPreloaded(request);
    }

    @Override
    public void clearPreload() {
        if (service() != null && isOwner() && isBindingOwner()) player().clearPreload();
    }

    @Override
    public void loadDanmaku(Result result, History history, Episode episode) {
        mTmdbKeywordReady = false;
        mTmdbKeyword = null;
        mTmdbFetchedKey = null;
        mTmdbPosterName = TextUtils.isEmpty(history.getVodName()) ? getName() : history.getVodName();
        VodPlaybackMedia.searchDanmaku(result, history, episode, player()::setDanmaku, player()::addDanmaku, this::onTmdbDanmaku);
        // 弹幕接口长时间无响应时不阻塞抓取：4 秒后按「弹幕失败」处理，回退海报名称。
        App.post(mTmdbFallback, 4000);
    }

    /**
     * 弹幕搜索结束（成功或失败）。只有「弹幕接口（非片源自带）返回的弹幕」才用其中的片名，
     * 其余情况一律回退海报名称；TMDB 侧若按弹幕片名搜不到，还会用海报名再重试一次。
     */
    private void onTmdbDanmaku(@Nullable Danmaku danmaku) {
        if (mTmdbKeywordReady) return;
        String keyword = "";
        if (danmaku != null && danmaku.isRemote() && !danmaku.isEmpty()) keyword = danmaku.getMeta().getTitle();
        if (TextUtils.isEmpty(keyword)) keyword = mTmdbPosterName;
        mTmdbKeyword = keyword;
        mTmdbKeywordReady = true;
        App.removeCallbacks(mTmdbFallback);
        tryFetchTmdb();
    }

    private void onTmdbDanmakuTimeout() {
        if (!mTmdbKeywordReady) onTmdbDanmaku(null);
    }

    private void tryFetchTmdb() {
        if (!mTmdbPlaybackStarted || !mTmdbKeywordReady) return;
        if (TextUtils.isEmpty(mTmdbKeyword)) return;
        String key = getHistoryKey();
        if (TextUtils.equals(key, mTmdbFetchedKey)) return;
        mTmdbFetchedKey = key;
        mTmdbManual = false;
        // 结果本身由 TmdbApi 写入本地缓存并和这些别名关联，首页最近观看按海报名即可命中。
        TmdbApi.fetch(mTmdbKeyword, mTmdbPosterName, tmdbAliases(), mTmdbCallback);
    }

    /** 关联别名：海报名、当前站点片名、弹幕片名都指向同一份素材。 */
    private List<String> tmdbAliases() {
        List<String> aliases = new ArrayList<>();
        if (mHistory != null) aliases.add(mHistory.getVodName());
        aliases.add(mTmdbPosterName);
        aliases.add(getName());
        aliases.add(mTmdbKeyword);
        return aliases;
    }

    /**
     * 自动刮削的结果回调。失败时按「刮削失败提示」开关决定是否弹 Toast；
     * 手动重试（点「TMDB刮削」按钮）成功、失败都提示 —— 用户主动点了，总要给个回执。
     */
    private final TmdbApi.FetchCallback mTmdbCallback = new TmdbApi.FetchCallback() {

        @Override
        public void onSuccess(String keyword, TmdbArt art) {
            // 刮削成功即刻回填背景图（不加载 logo）：这一步是「进入页面后才有素材」的实时路径。
            applyDetailHero(art);
            onTmdbResult(true, keyword, art == null ? "" : art.getTitle(), "");
        }

        @Override
        public void onFailure(String keyword, String reason) {
            onTmdbResult(false, keyword, "", reason);
        }
    };

    private void onTmdbResult(boolean success, String keyword, String title, String reason) {
        boolean manual = mTmdbManual;
        mTmdbManual = false;
        if (success) {
            if (manual) Notify.show(getString(R.string.tmdb_retry_success, TextUtils.isEmpty(title) ? keyword : title));
            return;
        }
        if (!manual && !TmdbSetting.isErrorToast()) return;
        Notify.show(getString(R.string.tmdb_fetch_failed, reason));
    }

    /** 「TMDB刮削」按钮：跳过内部冷却与 miss 记录强制重抓，用于刮削失败后的手动重试。 */
    private void onTmdbRetry() {
        if (!TmdbSetting.isEnabled()) {
            Notify.show(R.string.tmdb_disabled_hint);
            return;
        }
        if (TextUtils.isEmpty(mTmdbKeyword)) mTmdbKeyword = mTmdbPosterName;
        if (TextUtils.isEmpty(mTmdbKeyword)) {
            Notify.show(R.string.tmdb_no_keyword);
            return;
        }
        mTmdbManual = true;
        // 清掉「本片已抓过」标记，否则手动重试会被去重逻辑直接挡掉。
        mTmdbFetchedKey = null;
        Notify.show(R.string.tmdb_retry_start);
        TmdbApi.retry(mTmdbKeyword, mTmdbPosterName, tmdbAliases(), mTmdbCallback);
    }

    // ------------------------------------------------------------ 详情页右上角背景图

    /**
     * 按片名加载详情页背景图。
     *
     * <p>只取 TMDB 记录里的背景图，<b>不加载片名 logo</b>：详情页的内容区已经排满文字与按钮，
     * 再叠一层 logo 会与标题抢视觉重心。片名命中不了就什么都不显示（保持纯背景色）。</p>
     */
    private void updateDetailHero(@Nullable String name) {
        if (TextUtils.isEmpty(name)) return;
        // 只改加载时机：控件尚未全部加载完成时不做任何 TmdbStore 查询 / 位图解码 / 羽化，
        // 仅记住最后一次请求的片名，等 markHeroReady() 再补上。其余（素材匹配、透明渐入、
        // 换片复位、抓取回填、超时兜底）全部沿用原逻辑。
        if (!mHeroReady) {
            mPendingHeroName = name;
            return;
        }
        applyDetailHero(TmdbStore.find(name));
    }

    /** 内容层首帧已上屏：该页所有控件加载完成，此刻才允许真正加载背景图。 */
    private void markHeroReady() {
        if (mHeroReady) return;
        mHeroReady = true;
        String name = mPendingHeroName;
        mPendingHeroName = null;
        if (!TextUtils.isEmpty(name)) updateDetailHero(name);
    }

    /** 当前详情页的片名：优先用历史记录里的海报名（TMDB 别名就是按它关联的），
     * 详情还没回来时退回 intent 里带的名字。 */
    private String detailName() {
        return mHistory != null && !TextUtils.isEmpty(mHistory.getVodName()) ? mHistory.getVodName() : getName();
    }

    /**
     * 应用一条 TMDB 素材里的背景图：尺寸与容器层级都与首页一致。
     *
     * <p>{@code videoHero} 是根布局的第一个子 View（{@code FeatheredImageView}），
     * 永远绘制在内容层之下，只作氛围背景，不覆盖播放器、文字、按钮与浮层；
     * 宽度取屏宽的 {@link #DETAIL_HERO_WIDTH_RATIO}、右侧贴边、高度按原图比例，
     * 最终不透明度为 {@link #DETAIL_HERO_ALPHA}（30%，即 70% 透明）。</p>
     *
     * <p><b>时序</b>：这里只负责「把素材装进控件并保持全透明」，真正露脸要等内容层
     * 首帧绘制完成（{@link #notifyContentRendered()}）后才由 {@link #fadeInDetailHero(boolean)}
     * 渐入 —— 打开页面时封面、标题、控制栏在同一帧里铺好，背景图不会提前亮起来把画面闪一下。</p>
     *
     * <p>位图由 {@code TmdbStore} 的内存缓存持有，这里只读不回收；同一条素材重复应用时直接跳过。</p>
     */
    private void applyDetailHero(@Nullable TmdbArt art) {
        String path = art != null && art.hasBackdrop() ? art.getBackdrop() : null;
        if (TextUtils.equals(path, mHeroArt) && (path == null || mHeroPrepared)) return;
        Bitmap bitmap = TextUtils.isEmpty(path) ? null : TmdbStore.bitmap(path);
        if (bitmap == null) path = null;
        mHeroArt = path;
        if (path == null) {
            // 没有关联背景图 / 位图解码失败：整层收起，界面回到「纯背景色」，不留半透明空壳。
            mHeroPrepared = false;
            mHeroShown = false;
            mBinding.videoHero.animate().cancel();
            mBinding.videoHero.setImageDrawable(null);
            mBinding.videoHero.setAlpha(0f);
            mBinding.videoHero.setVisibility(View.GONE);
            return;
        }
        mHeroPrepared = true;
        FeatheredImageView hero = mBinding.videoHero;
        hero.animate().cancel();
        // 已露过脸的直接回到终值（可能打断了一次进行中的渐入）；还没露脸的一律先全透明，
        // 位图与羽化都在看不见的时候算完，渐入只改 alpha。
        hero.setAlpha(mHeroShown ? DETAIL_HERO_ALPHA : 0f);
        hero.load(bitmap, DETAIL_HERO_WIDTH_RATIO);
        // 全屏播放时整屏交给画面，背景图不参与绘制；退出全屏时再放出来（见 exitFullscreen）。
        if (isFullscreen()) {
            hero.setVisibility(View.GONE);
            return;
        }
        hero.setVisibility(View.VISIBLE);
        // 内容已经画完了（例如刮削成功回填、换素材）就立刻渐入；否则等首帧信号。
        fadeInDetailHero(mContentDrawn);
    }

    /**
     * 一次性的「内容已渲染」信号：封面、标题、播放控制栏都已回填并真正画到屏幕上。
     *
     * <p>用挂在内容层上的 {@code OneShotPreDrawListener} 而不是 {@code post} / 固定延时：
     * pre-draw 只在所在视图树真的要绘制时才回调，比任何猜测都准；再 post 一帧，
     * 保证渐入发生在「控件已经上屏」之后，不会出现背景先亮、控件后到的闪烁。</p>
     */
    private void notifyContentRendered() {
        if (mContentDrawn) {
            markHeroReady();
            fadeInDetailHero(true);
            return;
        }
        if (mContentDrawnPending) return;
        mContentDrawnPending = true;
        OneShotPreDrawListener.add(mBinding.progressLayout, () -> App.post(() -> {
            mContentDrawnPending = false;
            mContentDrawn = true;
            // 控件都已上屏，这时才解码并装载背景；随后立刻按原有渐入逻辑露脸。
            markHeroReady();
            App.removeCallbacks(mHeroFallback);
            fadeInDetailHero(true);
        }));
    }

    /**
     * 让背景图渐入到最终透明度。
     *
     * @param animate true 走 {@link #DETAIL_HERO_FADE_DURATION} 的渐入；false 直接落到终值
     *                （控件加载失败 / 等待超时 / 退出全屏恢复时的兜底，不制造第二段动画）
     */
    private void fadeInDetailHero(boolean animate) {
        if (!mHeroPrepared || mHeroShown || isFullscreen()) return;
        mHeroShown = true;
        App.removeCallbacks(mHeroFallback);
        View hero = mBinding.videoHero;
        hero.animate().cancel();
        hero.setVisibility(View.VISIBLE);
        if (!animate) {
            hero.setAlpha(DETAIL_HERO_ALPHA);
            return;
        }
        hero.setAlpha(0f);
        hero.animate().alpha(DETAIL_HERO_ALPHA).setDuration(DETAIL_HERO_FADE_DURATION).setInterpolator(new DecelerateInterpolator()).start();
    }

    /** 退出全屏：把全屏期间收起的背景图放回来（已渐入过的直接给终值，没渐入过的补一次）。 */
    private void restoreDetailHero() {
        if (!mHeroPrepared) {
            mBinding.videoHero.setVisibility(View.GONE);
            return;
        }
        mBinding.videoHero.setVisibility(View.VISIBLE);
        mBinding.videoHero.setAlpha(mHeroShown ? DETAIL_HERO_ALPHA : 0f);
        if (!mHeroShown) fadeInDetailHero(mContentDrawn);
    }

    /** 换片：状态复位，让新片重新走一遍「等内容首帧 → 渐入」，并重新武装兜底计时。 */
    private void resetDetailHero() {
        mHeroPrepared = false;
        mHeroShown = false;
        mContentDrawn = false;
        mPendingHeroName = null;
        mHeroArt = null;
        mBinding.videoHero.animate().cancel();
        mBinding.videoHero.setImageDrawable(null);
        mBinding.videoHero.setAlpha(0f);
        mBinding.videoHero.setVisibility(View.GONE);
        App.removeCallbacks(mHeroFallback);
        App.post(mHeroFallback, DETAIL_HERO_READY_TIMEOUT);
    }

    @Override
    public void renderDetail(Vod item, History history) {
        mHistory = history;
        mBinding.progressLayout.showContent();
        renderFallbackName(item.getName());
        // 详情数据到达后按真实片名再对一次缓存：换集 / 别名不同也能命中同一份素材。
        updateDetailHero(TextUtils.isEmpty(history.getVodName()) ? item.getName() : history.getVodName());
        mBinding.video.requestFocus();
        App.removeCallbacks(mR4);
        setArtwork(item.getPic());
        checkKeepImg();
        setText(item);
        updateKeep();
        // 封面 / 标题 / 控制栏都回填完了：等这一帧真正绘制出来再让背景图渐入。
        notifyContentRendered();
    }

    @Override
    public void renderVodUpdate(Vod item) {
        if (!item.getId().isEmpty()) updateNavigationKey();
        renderVodMetadata(item.getName(), item.getPic());
        setText(item);
    }

    private void renderVodMetadata(String name, String pic) {
        if (name.isEmpty() && pic.isEmpty()) return;
        if (!name.isEmpty()) renderFallbackName(name);
        if (!name.isEmpty()) setPartAdapter();
        if (!name.isEmpty()) updateDetailHero(name);
        if (!pic.isEmpty()) setArtwork();
        updateKeep();
        notifyContentRendered();
    }

    @Override
    public void renderEmptyDetail() {
        showEmpty();
    }

    @Override
    public void renderFallbackName(String name) {
        getIntent().putExtra("name", name);
        mBinding.name.setText(name);
    }

    @Override
    public void renderFlags(List<Flag> items) {
        mBinding.flag.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        mFlagAdapter.addAll(items);
    }

    @Override
    public void renderEpisodes(List<Episode> items) {
        setEpisodeAdapter(items);
    }

    @Override
    public void renderFlagSelection(Flag item) {
        mBinding.flag.setSelectedPosition(mFlagAdapter.indexOf(item));
        notifyItemChanged(mBinding.flag, mFlagAdapter);
    }

    @Override
    public void renderEpisodeSelection(Episode item) {
        notifyItemChanged(mBinding.episode, mEpisodeAdapter);
        mBinding.episode.setSelectedPosition(mEpisodeAdapter.getPosition());
    }

    @Override
    public void renderReverseEpisodes(List<Episode> items, boolean scroll) {
        setEpisodeAdapter(items);
        if (scroll) mBinding.episode.setSelectedPosition(mEpisodeAdapter.getPosition());
    }

    @Override
    public void renderQuality(Result result, boolean visible) {
        mQualityAdapter.addAll(result);
        setQualityVisible(visible);
    }

    @Override
    public void renderQualityVisible(boolean visible) {
        setQualityVisible(visible);
    }

    @Override
    public void renderSources(List<Vod> items) {
        mQuickAdapter.addAll(items);
        mBinding.quick.setVisibility(mQuickAdapter.isEmpty() ? View.GONE : View.VISIBLE);
    }

    @Override
    public void renderHistory(History history) {
        mHistory = history;
        mBinding.control.action.opening.setText(openingText(history.getOpening()));
        mBinding.control.action.ending.setText(endingText(history.getEnding()));
        setScale(getScale());
        setPartAdapter();
    }

    @Override
    public void renderUseParse(boolean useParse) {
        setUseParse(useParse);
        mBinding.control.action.parse.setVisibility(isUseParse() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void renderArtwork(String url) {
        setArtwork(url);
    }

    @Override
    public void renderDescription(String desc) {
        mBinding.content.setTag(desc);
    }

    @Override
    public void renderPlaybackMetadata(MediaMetadata metadata) {
        if (isPlaybackCurrent()) player().setMetadata(metadata);
        // 左上角：片名与当前集数同一行，中间用「：」分隔（站点给的集数常带空格/全角空格，去掉避免「： 1」）
        mBinding.widget.title.setText(MediaItemFactory.getDisplayTitle(metadata));
        String episode = TextUtils.isEmpty(metadata.subtitle) ? "" : trimSpace(metadata.subtitle.toString());
        boolean hasSubtitle = !TextUtils.isEmpty(episode);
        mBinding.widget.subtitle.setText(hasSubtitle ? "：" + episode : "");
        mBinding.widget.subtitle.setVisibility(hasSubtitle ? View.VISIBLE : View.GONE);
        // 先定标题形态（单行 / 弹幕两行），再按最终可见性分配宽度
        renderDanmakuTitle();
        applyTitleWidth();
    }

    /**
     * 弹幕标题：当前选中弹幕来自弹幕接口（非片源自带）且已成功获取时，
     * 右上角改为两行结构 —— 主行「剧名 · 集数 分集标题」、副行「年份 · 类型 · 平台」，
     * 与弹幕搜索结果的条目同构；片源自带弹幕 / 本地弹幕 / 尚未选中时，仍用原来的「片名：集数」单行。
     */
    @Override
    protected void renderDanmakuTitle() {
        if (mBinding == null) return;
        Danmaku selected = selectedDanmaku();
        DanmakuTitle meta = selected == null ? null : selected.getMeta();
        boolean danmaku = meta != null && selected.isRemote();
        if (danmaku) {
            String secondary = meta.getSecondary();
            mBinding.widget.danmakuTitle.setText(meta.getPrimary());
            mBinding.widget.danmakuSubtitle.setText(secondary);
            mBinding.widget.danmakuSubtitle.setVisibility(TextUtils.isEmpty(secondary) ? View.GONE : View.VISIBLE);
        }
        mBinding.widget.danmakuRow.setVisibility(danmaku ? View.VISIBLE : View.GONE);
        mBinding.widget.titleRow.setVisibility(danmaku ? View.GONE : View.VISIBLE);
    }

    /** 当前选中的弹幕；尚未选中（含切集后清空）时返回 null。 */
    private Danmaku selectedDanmaku() {
        if (service() == null) return null;
        for (Danmaku item : player().getDanmakus()) if (item.isSelected()) return item;
        return null;
    }

    /**
     * 片名宽度按「整行可用宽度 − 集数宽度」分配。
     *
     * <p>用 {@code layout_weight=1} 时片名会吃掉整行剩余宽度，短片名也会把集数顶到最右边（紧挨右侧时钟）；
     * 全改成 wrap_content 又会在长片名时把集数挤出可视区。这里取实测宽度的较小值：
     * 短片名紧贴集数，长片名截断、集数始终完整可见。
     */
    private void applyTitleWidth() {
        TextView title = mBinding.widget.title;
        TextView subtitle = mBinding.widget.subtitle;
        View row = mBinding.widget.titleRow;
        row.post(() -> {
            int rowWidth = row.getWidth();
            if (rowWidth <= 0) return;
            int reserved = subtitle.getVisibility() == View.VISIBLE ? measureTextWidth(subtitle) : 0;
            int width = Math.max(0, Math.min(measureTextWidth(title), rowWidth - reserved));
            ViewGroup.LayoutParams params = title.getLayoutParams();
            if (params.width == width) return;
            params.width = width;
            title.setLayoutParams(params);
        });
    }

    /** 去掉首尾空白，含全角空格（U+3000）与不换行空格（U+00A0）—— trim() 只处理 <= U+0020。 */
    private String trimSpace(String text) {
        return text.replaceAll("^[\\s\\u3000\\u00A0]+|[\\s\\u3000\\u00A0]+$", "");
    }

    /** 文本的实测像素宽（含内边距，多留 1px 防止四舍五入后又触发截断）。 */
    private int measureTextWidth(TextView view) {
        CharSequence text = view.getText();
        int padding = view.getPaddingStart() + view.getPaddingEnd();
        if (TextUtils.isEmpty(text)) return padding;
        return (int) Math.ceil(view.getPaint().measureText(text, 0, text.length())) + padding + 1;
    }

    @Override
    public void onDetailFallbackScheduled() {
        App.post(mR4, 10000);
    }

    @Override
    public void onDetailFallbackCancelled() {
        App.removeCallbacks(mR4);
    }

    @Override
    public void onSearchStarted(String keyword) {
        mBinding.part.setTag(keyword);
        mQuickAdapter.clear();
    }

    @Override
    public void onSearchResult() {
        App.removeCallbacks(mR4);
    }

    @Override
    public void showDetailMessage(String msg) {
        Notify.show(msg);
    }

    @Override
    public void showSwitchLine(Flag flag) {
        Notify.show(getString(R.string.play_switch_flag, flag.getFlag()));
    }

    @Override
    public void showSwitchSource(Vod item) {
        Notify.show(getString(R.string.play_switch_site, item.getSiteName()));
    }

    @Override
    public void showNoNext(boolean reversed) {
        Notify.show(reversed ? R.string.error_play_prev : R.string.error_play_next);
    }

    @Override
    public void showNoPrev(boolean reversed) {
        Notify.show(reversed ? R.string.error_play_next : R.string.error_play_prev);
    }

    @Override
    public void finishVod() {
        finish();
    }

    private void checkCast() {
        if (isCast() && !isFullscreen()) enterFullscreen();
        else mBinding.progressLayout.showProgress();
    }

    private void checkId() {
        mVod.checkId();
    }

    private void showEmpty() {
        mBinding.progressLayout.showEmpty();
        // 详情加载失败 / 超时：控件不会再有「加载完成」这一步，背景图直接落到终值 ——
        // 有一层淡淡的氛围图，界面不至于空成一块黑板；不做渐入，避免和空态文字抢出场顺序。
        mContentDrawn = true;
        markHeroReady();
        fadeInDetailHero(false);
    }

    private void setText(Vod item) {
        mBinding.content.setTag(item.getContent());
        setText(mBinding.year, R.string.detail_year, item.getYear());
        setText(mBinding.area, R.string.detail_area, item.getArea());
        setText(mBinding.type, R.string.detail_type, item.getTypeName());
        setText(mBinding.site, R.string.detail_site, getSite().getName());
        setText(mBinding.director, R.string.detail_director, item.getDirector());
        setText(mBinding.actor, R.string.detail_actor, item.getActor());
        setText(mBinding.remark, 0, item.getRemarks());
    }

    private void setText(TextView view, int resId, String text) {
        if (TextUtils.isEmpty(text) && !TextUtils.isEmpty(view.getText())) return;
        view.setText(Sniffer.buildClickable(resId > 0 ? getString(resId, text) : text, this::clickableSpan), TextView.BufferType.SPANNABLE);
        view.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
        view.setLinkTextColor(MDColor.YELLOW_500);
        CustomMovement.bind(view);
    }

    private ClickableSpan clickableSpan(Result result) {
        return new ClickableSpan() {
            @Override
            public void onClick(@NonNull View view) {
                VodActivity.start(getActivity(), getKey(), result);
                setRedirect(true);
            }
        };
    }

    @Override
    public void onItemClick(Flag item) {
        mVod.selectFlag(item);
    }

    private void setEpisodeAdapter(List<Episode> items) {
        mEpisodes.clear();
        mEpisodes.addAll(items);
        mBinding.episode.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        // 控制条「选集」入口：单集没有选择意义，多集才显示
        mBinding.control.action.episodes.setVisibility(items.size() > 1 ? View.VISIBLE : View.GONE);
        mEpisodeAdapter.addAll(items);
        setArrayAdapter(items.size());
        setR2Callback();
    }

    @Override
    public void onItemClick(Episode item) {
        if (shouldEnterFullscreen(item)) return;
        mVod.selectEpisode(item);
    }

    private void setQualityVisible(boolean visible) {
        mBinding.quality.setVisibility(visible ? View.VISIBLE : View.GONE);
        setR2Callback();
    }

    @Override
    public void onItemClick(Result result) {
        mVod.selectQuality(result);
    }

    private void setArrayAdapter(int size) {
        List<String> items = new ArrayList<>();
        items.add(getString(R.string.play_reverse));
        items.add(getString(mHistory.getRevPlayText()));
        mBinding.array.setVisibility(size > 1 ? View.VISIBLE : View.GONE);
        if (mHistory.isRevSort()) for (int i = size; i > 0; i -= 20) items.add(i + "-" + Math.max(i - 19, 1));
        else for (int i = 0; i < size; i += 20) items.add((i + 1) + "-" + Math.min(i + 20, size));
        mArrayAdapter.addAll(items);
    }

    private int findFocusDown(int index) {
        List<Integer> orders = Arrays.asList(R.id.flag, R.id.quality, R.id.episode, R.id.array, R.id.part, R.id.quick);
        for (int i = 0; i < orders.size(); i++) if (i > index) if (isVisible(findViewById(orders.get(i)))) return orders.get(i);
        return 0;
    }

    private int findFocusUp(int index) {
        List<Integer> orders = Arrays.asList(R.id.flag, R.id.quality, R.id.episode, R.id.array, R.id.part, R.id.quick);
        for (int i = orders.size() - 1; i >= 0; i--) if (i < index) if (isVisible(findViewById(orders.get(i)))) return orders.get(i);
        return 0;
    }

    private void updateFocus() {
        mPartAdapter.setNextFocusUp(findFocusUp(4));
        mEpisodeAdapter.setNextFocusUp(findFocusUp(2));
        mFlagAdapter.setNextFocusDown(findFocusDown(0));
        mEpisodeAdapter.setNextFocusDown(findFocusDown(2));
        notifyItemChanged(mBinding.episode, mEpisodeAdapter);
        notifyItemChanged(mBinding.part, mPartAdapter);
        notifyItemChanged(mBinding.flag, mFlagAdapter);
    }

    @Override
    public void onRevSort() {
        mVod.setRevSort(!mHistory.isRevSort());
        mVod.reverseEpisode(false);
    }

    @Override
    public void onRevPlay(TextView view) {
        mVod.setRevPlay(!mHistory.isRevPlay());
        view.setText(mHistory.getRevPlayText());
        Notify.show(mHistory.getRevPlayHint());
    }

    private boolean shouldEnterFullscreen(Episode item) {
        boolean enter = !isFullscreen() && item.isSelected();
        if (enter) enterFullscreen();
        return enter;
    }

    private void enterFullscreen() {
        mFocus1 = getCurrentFocus();
        // 顶部已无品牌行，无需隐藏；背景图同属页面装饰，全屏播放时一并收起（正在渐入的话连动画一起掐掉）。
        mBinding.videoHero.animate().cancel();
        mBinding.videoHero.setVisibility(View.GONE);
        mBinding.video.requestFocus();
        mBinding.video.setForeground(null);
        mBinding.video.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
        mBinding.flag.setSelectedPosition(mFlagAdapter.getPosition());
        mInput.setFullscreen(true);
        setFullscreen(true);
        mFocus2 = null;
        // 进全屏时控制条若正显示，顶部信息条随之出现（它只在全屏展示）。
        if (isControlVisible()) showTop();
    }

    private void exitFullscreen() {
        restoreDetailHero();
        mBinding.video.setForeground(ResUtil.getDrawable(R.drawable.selector_video));
        mBinding.video.setLayoutParams(mFrameParams);
        getFocus1().requestFocus();
        mInput.setFullscreen(false);
        setFullscreen(false);
        mFocus2 = null;
        hideTop();
    }

    private void onContent() {
        if (mBinding.content.getTag() == null) return;
        ContentDialog.create().content(mBinding.content.getTag().toString()).show(this);
    }

    private void onKeep() {
        Keep keep = Keep.find(getHistoryKey());
        Notify.show(keep != null ? R.string.keep_del : R.string.keep_add);
        if (keep != null) keep.delete();
        else createKeep();
        checkKeepImg();
        RefreshEvent.keep();
    }

    private void onVideo() {
        if (!isFullscreen()) enterFullscreen();
    }

    private void onChange() {
        mVod.manualSwitchSource();
    }

    private void onRepeat() {
        player().setRepeatOne(!player().isRepeatOne());
        mBinding.control.action.repeat.setSelected(player().isRepeatOne());
    }

    @Override
    public void onRepeatModeChanged(int repeatMode) {
        mBinding.control.action.repeat.setSelected(player().isRepeatOne());
    }

    private void checkNext() {
        checkNext(true);
    }

    private void checkNext(boolean notify) {
        mVod.nextEpisode(notify);
    }

    private void checkPrev() {
        mVod.prevEpisode(true);
    }

    private void onNext(boolean notify) {
        Episode item = mEpisodeAdapter.getNext();
        if (!item.isSelected()) onItemClick(item);
        else if (notify) Notify.show(mHistory.isRevPlay() ? R.string.error_play_prev : R.string.error_play_next);
    }

    private void onPrev(boolean notify) {
        Episode item = mEpisodeAdapter.getPrev();
        if (!item.isSelected()) onItemClick(item);
        else if (notify) Notify.show(mHistory.isRevPlay() ? R.string.error_play_next : R.string.error_play_prev);
    }

    private void onScale() {
        setScale((getScale() + 1) % ResUtil.getStringArray(R.array.select_scale).length);
    }

    /** 倍速改为浮层菜单，不再弹倍速对话框。 */
    private void onSpeed() {
        onSpeedSheet();
    }

    private boolean onSpeedLong() {
        PlaybackAction.toggleSpeed(player(), mBinding.widget.message);
        return true;
    }

    private void onReset() {
        onRefresh();
    }

    private void onParse() {
        ParseDialog.create().show(this);
    }

    private void onReplay() {
        mVod.replay();
    }

    @Override
    protected boolean onRefresh() {
        mVod.refresh();
        return true;
    }

    private void onOpening() {
        long position = player().getPosition();
        long duration = player().getDuration();
        if (player().canSetOpening(position, duration)) setOpening(position);
    }

    /**
     * 片头下键：片头标记向片尾方向推进（+1s）。
     *
     * <p>基准对齐上游 FongMi/TV：未设置过时按 00:00 起算（{@code Math.max(0, opening) + 1000}），
     * 而不是以当前播放位置为锚点。锚在播放位置时，可调区间会被压在「已播放过的那一段」里，
     * 越不过当前位置；改为 00:00 起算后，微调范围覆盖整片时间轴。
     * 上限仍取总时长，避免出现超过片长的无效值。
     */
    private void onOpeningAdd() {
        long base = mHistory == null ? 0 : Math.max(0, mHistory.getOpening());
        long duration = player().getDuration();
        long next = base + 1000;
        setOpening(duration > 0 ? Math.min(next, duration) : next);
    }

    /**
     * 片头上键：片头标记向「更早」方向移动 1s（少跳一点片头）。
     *
     * <p>与片尾保持统一的方向语义：上下键移动时间轴上的标记点，下 = 更晚、上 = 更早。
     * 基准同为 00:00，故退到 00:00 即止。
     */
    private void onOpeningSub() {
        long base = mHistory == null ? 0 : Math.max(0, mHistory.getOpening());
        long duration = player().getDuration();
        long next = Math.max(0, base - 1000);
        setOpening(duration > 0 ? Math.min(next, duration) : next);
    }

    private boolean onOpeningReset() {
        setOpening(History.AUTO_SKIP);
        return true;
    }

    private void setOpening(long opening) {
        mVod.setOpening(opening);
        mBinding.control.action.opening.setText(openingText(opening));
    }

    private String openingText(long opening) {
        return opening <= 0 ? getString(R.string.play_op) : Util.timeMs(opening);
    }

    private void onEnding() {
        long position = player().getPosition();
        long duration = player().getDuration();
        if (player().canSetEnding(position, duration)) setEnding(position);
        else Notify.show(R.string.play_ed_range);
    }

    /**
     * 片尾下键：片尾点向「更晚」方向移动 1s（少跳一点片尾）。
     *
     * <p>基准对齐上游 FongMi/TV：未设置过时以「总时长」为基准（等价上游把片尾存成「尾长 0」，
     * 即片尾点落在结尾）。原来锚在「当前播放位置」，往下调一档就落到播放位置之后、
     * 往上调一档就落到播放位置之前并立刻触发跳集，可调范围实际被锁在 00:00~已播放时间里。
     * 改为从结尾起算后，上下键可把片尾点沿整片时间轴移动，且下键只往后挪，不会误跳。
     */
    private void onEndingAdd() {
        long duration = player().getDuration();
        long base = mHistory == null ? 0 : mHistory.getEnding();
        if (base <= 0) base = duration;
        // 时长未知且尚未设定：忽略按键，避免写成 0 反而把片尾清掉。
        if (base <= 0) return;
        long next = base + 1000;
        setEnding(duration > 0 ? Math.min(next, duration) : next);
    }

    /**
     * 片尾上键：片尾点向「更早」方向移动 1s（多跳一点片尾）。
     *
     * <p>基准同上（未设置时为总时长）：首次按下即把片尾点放在结尾前 1s，连续按可一路收到目标位置；
     * 若收到当前播放位置之前，下一帧 {@code onTimeChanged} 会按既有跳转逻辑跳到结尾 / 下一集。
     */
    private void onEndingSub() {
        long duration = player().getDuration();
        long base = mHistory == null ? 0 : mHistory.getEnding();
        if (base <= 0) base = duration;
        if (base <= 0) return;
        long next = Math.max(0, base - 1000);
        setEnding(duration > 0 ? Math.min(next, duration) : next);
    }

    private boolean onEndingReset() {
        setEnding(History.AUTO_SKIP);
        return true;
    }

    private void setEnding(long ending) {
        mVod.setEnding(ending);
        mBinding.control.action.ending.setText(endingText(ending));
    }

    private String endingText(long ending) {
        // ending 与 opening 同为「时间轴上的位置」：显示什么就跳到哪，不再做 duration − ending 的尾长换算。
        return ending <= 0 ? getString(R.string.play_ed) : Util.timeMs(ending);
    }

    private void onPlayer() {
        PlayerEngineDialog.show(this);
    }

    private void onDecoder() {
        DecoderSettingDialog.create().show(this);
    }

    private void onTrack(View view) {
        onTrackSheet(Integer.parseInt(view.getTag().toString()));
    }

    private void onEdition() {
        EditionDialog.create().player(player()).show(this);
    }

    private void onChapter() {
        ChapterDialog.create().show(this);
    }

    private void onDiscMenu(View view) {
        openDiscMenu();
        hideControl();
    }

    private boolean onDiscMenuLong(View view) {
        if (openDiscPopupMenu()) hideControl();
        return true;
    }

    private void onDanmaku() {
        onDanmakuSheet();
    }

    private void onToggle() {
        if (isControlVisible()) hideControl();
        else showControl(mBinding.control.action.playPause);
    }

    /**
     * 控制条上的播放/暂停按钮：只切换播放状态，控制条保持在屏。
     * 切换后重新评估自动隐藏：暂停 → 取消计时（常驻），恢复播放 → 重新计 10 秒。
     */
    private void onPlayPause() {
        if (isPlayingForControls()) onPaused();
        else onPlay();
        syncPlayPause();
        scheduleAutoHide();
    }

    /** 「播放设置」：在按钮正上方展开浮层，样式与动效同选集菜单。 */
    private void onControlPanel() {
        if (mSheet.isShowing()) {
            hideSheet();
            return;
        }
        int max = Math.max(0, mBinding.video.getWidth() - ResUtil.dp2px(16));
        mPanelSheet.bind(this::hideSheet);
        int width = mPanelSheet.measureWidth(max) + ResUtil.dp2px(16);
        mSheet.show(mBinding.control.action.panelBtn, mPanelSheet.getView(), width, mPanelSheet::focus);
    }

    /** 收起浮层并把焦点交还锚点按钮（此刻控制条仍在）。 */
    private void hideSheet() {
        if (mSheet == null || !mSheet.isShowing()) return;
        View anchor = mSheet.getAnchor();
        mSheet.hide();
        if (anchor != null && anchor.isShown()) anchor.requestFocus();
        scheduleAutoHide();
    }

    /** 浮层打开：进度条退出焦点链，并把浮层以外的控件整体锁掉，焦点只能在浮层内部活动。 */
    private void onSheetShow() {
        // 先钉住再锁焦点：setOutsideFocusable(false) 会让按钮失焦并触发一次全局焦点回调，
        // 若那时还没钉住，按钮会先起一段「收起」动画再被重新点亮，出现一次可见抖动。
        setControlPinned(mSheet == null ? null : mSheet.getAnchor());
        setSeekFocusable(false);
        setOutsideFocusable(false);
    }

    /** 浮层关闭后恢复外部的焦点能力，并重新激活自动隐藏计时（暂停态由 scheduleAutoHide 内部再判一次）。 */
    private void onSheetDismiss() {
        // 搜索页可能有在途请求：浮层收起时取消，结果留在内存里供再次打开复用。
        // 从「选择弹幕」replace 到搜索页不走这里，所以不会误杀刚发起的搜索。
        if (mDanmakuSearchSheet != null) mDanmakuSearchSheet.onDismiss();
        setSeekFocusable(true);
        setOutsideFocusable(true);
        // 延一帧再撤钉：hideSheet() 紧接着会把焦点还给锚点按钮，若这里同步撤钉，
        // 按钮会先「熄一下」再被焦点重新点亮。若期间又打开了新浮层，保留新的锚点。
        mBinding.control.getRoot().post(() -> {
            if (mSheet == null || !mSheet.isShowing()) setControlPinned(null);
        });
        scheduleAutoHide();
    }

    /**
     * 把「正开着浮层」这件事记在锚点按钮上。
     *
     * <p>用 {@code activated} 而不是 {@code selected}：控制条上弹幕开关、调试、循环等按钮已经用
     * {@code selected} 表达业务状态（{@code syncDanmaku()}、{@code setDebug()}…），
     * 两者正交，浮层关闭后业务状态原样保留。
     *
     * <p>{@code activated} 负责图标 / 文字的橙色（见各个 selector_control_*），
     * {@code Effect.setPinned()} 负责放大与辉光；同一时刻只钉一个按钮，其余按钮只按真实焦点走。
     */
    private void setControlPinned(View anchor) {
        if (mPinnedAnchor == anchor) return;
        if (mPinnedAnchor != null) mPinnedAnchor.setActivated(false);
        mPinnedAnchor = anchor;
        if (anchor != null) anchor.setActivated(true);
        if (mControlFocusEffect != null) mControlFocusEffect.setPinned(anchor);
    }

    /**
     * 浮层打开期间，浮层之外的控件一律不可聚焦：
     * 控制条按钮行整体 {@code FOCUS_BLOCK_DESCENDANTS}，画面表面本身也退出焦点链。
     * 这样方向键在浮层首/末行再按也不会把焦点带走，弹窗关闭前其它控件都拿不到焦点。
     */
    private void setOutsideFocusable(boolean focusable) {
        ViewGroup actionRoot = mBinding.control.action.getRoot();
        actionRoot.setDescendantFocusability(focusable ? ViewGroup.FOCUS_BEFORE_DESCENDANTS : ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        // 控制条的横向滚动容器自身可聚焦：浮层打开时若不一并禁掉，方向键在浮层首/末行
        // 会把焦点带出去（如倍速弹窗末行按下丢焦到控制条）。故连自身一起锁，关闭浮层再还原。
        actionRoot.setFocusable(focusable);
        actionRoot.setFocusableInTouchMode(focusable);
        mBinding.video.setFocusable(focusable);
        mBinding.video.setFocusableInTouchMode(focusable);
    }

    private void setSeekFocusable(boolean focusable) {
        View timeBar = mBinding.control.seek.findViewById(androidx.media3.ui.R.id.exo_progress);
        if (timeBar == null) return;
        timeBar.setFocusable(focusable);
        timeBar.setFocusableInTouchMode(focusable);
    }

    /**
     * 选集浮层：上集 / 下集长按打开。
     *
     * <p>列表倒序（最新一集在最上、第 1 集在最下）。浮层高度只由总集数决定：
     * 少于 6 集时自适应全部行，6 集及以上固定 6 行；不再根据「当前选中行上方的剩余行数」压缩高度
     * （否则正在播放最后一集时 selected=0，会被压成只剩 1 行）。选中行的停靠位置见
     * {@link SheetList#setAvoidEmptyRows(boolean)}：上方行数不足一屏时顶部对齐，避免空行。
     */
    private void onEpisodeSheet(View anchor) {
        if (mSheet.isShowing()) {
            hideSheet();
            return;
        }
        if (mEpisodes.isEmpty()) return;
        List<String> texts = new ArrayList<>();
        int selected = 0;
        for (int i = mEpisodes.size() - 1; i >= 0; i--) {
            Episode item = mEpisodes.get(i);
            if (item.isSelected()) selected = texts.size();
            texts.add(item.getDesc().concat(item.getName()));
        }
        int current = selected;
        // 第 4 个参数是「正在播放」行：该行前面会显示均衡器小图标，其余行预留同宽槽位保证文字对齐
        mEpisodeList.submit(texts, selected, selected, current, position -> {
            int index = mEpisodes.size() - 1 - position;
            hideSheet();
            if (index < 0 || index >= mEpisodes.size()) return;
            Episode item = mEpisodes.get(index);
            if (item.isSelected()) return;
            onItemClick(item);
        });
        // 高度只看总集数：<6 自适应，>=6 固定 6 行；与当前选中行无关
        int rows = Math.max(1, Math.min(6, texts.size()));
        mEpisodeList.setRows(rows);
        // 列条宽度以「单个 15sp 汉字」为步长量化：按最长行算出需要几个整字，
        // 3~15 字之间按内容自适应（既不留半个字的空白，也不会提前截断）；
        // 超过 15 字固定为 15 字宽，溢出部分交给条目的左右往复跑马灯展示。
        int chars = Math.max(1, (int) Math.ceil(measureSheetText(texts) / sheetCharWidth()));
        int textWidth = measureSheetChars(Math.max(3, Math.min(15, chars)));
        // 「正在播放」指示器是行内独立槽位（10dp 图标 + 6dp 间距），不参与文字测量，
        // 但必须从浮层宽度里额外预留：否则宽度被文字吃满，指示器会挤压 / 盖住标题。
        int width = textWidth + ResUtil.dp2px(SHEET_ROW_CHROME_DP + SHEET_PLAYING_SLOT_DP);
        mSheet.show(anchor == null ? mBinding.control.action.next : anchor, mEpisodeList.getView(), width, mEpisodeList::focus);
    }

    /** 倍速浮层：从下往上是 0.5 / 0.8 / 1.0 / 1.2 / 1.5 / 2.0 / 3.0 / 5.0，宽度自适应字符宽度。 */
    private void onSpeedSheet() {
        if (mSheet.isShowing()) {
            hideSheet();
            return;
        }
        List<String> texts = new ArrayList<>();
        for (int i = SPEEDS.length - 1; i >= 0; i--) texts.add(SPEEDS[i] + "x");
        float current = service() == null ? 1.0f : player().getSpeed();
        int selected = SPEEDS.length - 1 - nearestSpeedIndex(current);
        mSpeedList.submit(texts, selected, selected, position -> {
            hideSheet();
            if (service() == null) return;
            player().setSpeed(SPEEDS[SPEEDS.length - 1 - position]);
        });
        // 一屏只显示 5 行，其余滚动查看：1.0x 停在最底部，快速档位往上滚
        mSpeedList.setRows(SPEED_VISIBLE_ROWS);
        int width = Math.max(measureSheetText(texts), ResUtil.dp2px(72)) + ResUtil.dp2px(24 + 16);
        mSheet.show(mBinding.control.action.speed, mSpeedList.getView(), width, mSpeedList::focus);
    }

    /** 字幕 / 音轨 / 视轨浮层。 */
    private void onTrackSheet(int type) {
        if (mSheet.isShowing()) hideSheet();
        if (service() == null) return;
        View anchor = switch (type) {
            case C.TRACK_TYPE_AUDIO -> mBinding.control.action.audio;
            case C.TRACK_TYPE_VIDEO -> mBinding.control.action.panelBtn;
            default -> mBinding.control.action.text;
        };
        mTrackSheet.bindTrack(player(), type);
        mSheet.show(anchor, mTrackSheet.getView(), ResUtil.dp2px(TrackSheet.WIDTH_DP) + ResUtil.dp2px(16), mTrackSheet::focus);
    }

    /** 弹幕浮层（长按弹幕按钮）：只保留搜索与设置两个动作。 */
    private void onDanmakuSheet() {
        if (mSheet.isShowing()) {
            hideSheet();
            return;
        }
        if (service() == null) return;
        mTrackSheet.bindDanmaku(player());
        mSheet.show(mBinding.control.action.danmaku, mTrackSheet.getView(), ResUtil.dp2px(TrackSheet.WIDTH_DP) + ResUtil.dp2px(16), mTrackSheet::focus);
    }

    /** 「选择弹幕」页的放大镜：切到同一个 VideoSheet 宿主里的弹幕搜索页，并用片名立即搜索。 */
    private void showDanmakuSearchSheet() {
        openDanmakuSearchSheet(true);
    }

    /** 改完弹幕接口地址后的回场：保留用户输入的关键词，重新打开搜索页再搜一次。 */
    private void reopenDanmakuSearchSheet() {
        if (service() == null) return;
        // 接口设置对话框可能停留超过控制条的 10 秒自动隐藏时长；先让控制条回到屏上，
        // 否则浮层会拿一个已隐藏的锚点去算坐标而跑偏。showControl 内部会重排计时。
        showControl(mBinding.control.action.danmaku);
        openDanmakuSearchSheet(false);
    }

    /**
     * 弹幕搜索与「选择弹幕」共用一个 {@link VideoSheet}：已展开时只 {@code replace} 内容页，
     * 不收起、不重播展开动画，玻璃窗体与焦点锁原样保留；没展开时按常规锚点弹出。
     */
    private void openDanmakuSearchSheet(boolean autoSearch) {
        if (service() == null) return;
        mDanmakuSearchSheet.bind(player(), autoSearch);
        int width = DanmakuSearchSheet.widthPx();
        View content = mDanmakuSearchSheet.getView();
        if (mSheet.isShowing()) mSheet.replace(content, width, mDanmakuSearchSheet::focus);
        else mSheet.show(mBinding.control.action.danmaku, content, width, mDanmakuSearchSheet::focus);
        mDanmakuSearchSheet.research();
    }

    /**
     * 弹幕接口设置对话框（{@code DanmakuApiDialog.show(activity)}）以 Activity 为回调目标。
     * 齿轮点击时会先收起浮层，这里延一帧再打开搜索页，等对话框 dismiss 完成后再接管焦点。
     */
    @Override
    public void setDanmakuApi(String url) {
        if (mDanmakuSearchSheet == null) return;
        mDanmakuSearchSheet.setDanmakuApi(url);
        if (mDanmakuSearchSheet.hasKeyword()) App.post(this::reopenDanmakuSearchSheet);
    }

    /**
     * 弹幕快捷开关：只切当前播放器的弹幕显隐，不写全局设置。
     * 系统总开关关闭时按钮已置灰，此时点击（确认）提示去设置里开启，不产生任何开关动作。
     */
    private void onDanmakuToggle() {
        if (!DanmakuSetting.isLoad()) {
            Notify.show(R.string.danmaku_disabled_hint);
            return;
        }
        if (service() == null) return;
        player().setDanmakuEnabled(!player().isDanmakuEnabled());
        syncDanmaku();
        scheduleAutoHide();
    }

    private void pickSubtitleFile(int type) {
        if (service() == null) return;
        FileChooser.from(mSubtitleLauncher).show(new String[]{MimeTypes.APPLICATION_SUBRIP, MimeTypes.TEXT_SSA, MimeTypes.TEXT_VTT, MimeTypes.APPLICATION_TTML, "text/*", "application/octet-stream"});
        player().pause();
    }

    private void setSubtitleFile(Uri uri) {
        if (uri == null || service() == null) return;
        player().setSub(Sub.from(FileUtil.getDisplayName(uri), uri.toString()));
    }

    /** 用条目真实字号量文字宽度，菜单宽度跟着内容走。 */
    private void ensureSheetProbe() {
        if (mSheetProbe != null) return;
        mSheetProbe = new TextView(this);
        mSheetProbe.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
    }

    private int measureSheetText(List<String> texts) {
        ensureSheetProbe();
        float max = 0;
        for (String text : texts) max = Math.max(max, mSheetProbe.getPaint().measureText(text));
        return (int) Math.ceil(max);
    }

    /** 单个 15sp 汉字的像素宽（浮点，作为选集浮层宽度的量化步长）。 */
    private float sheetCharWidth() {
        ensureSheetProbe();
        return Math.max(1f, mSheetProbe.getPaint().measureText("汉"));
    }

    /** {@code count} 个 15sp 汉字的像素宽（列条的最小/最大宽度按汉字数定义）。 */
    private int measureSheetChars(int count) {
        return (int) Math.ceil(sheetCharWidth() * count);
    }

    private int nearestSpeedIndex(float speed) {
        int index = 0;
        float best = Float.MAX_VALUE;
        for (int i = 0; i < SPEEDS.length; i++) {
            float diff = Math.abs(SPEEDS[i] - speed);
            if (diff < best) {
                best = diff;
                index = i;
            }
        }
        return index;
    }

    private void showProgress() {
        mBinding.progress.getRoot().setVisibility(View.VISIBLE);
        App.post(mR3, 0);
        hideCenter();
        hideError();
    }

    private void hideProgress() {
        mBinding.progress.getRoot().setVisibility(View.GONE);
        App.removeCallbacks(mR3);
        Traffic.reset();
    }

    private void showError(String text) {
        PlaybackAction.hideSpeedHint(mBinding.widget.message);
        mBinding.widget.error.setVisibility(View.VISIBLE);
        mBinding.widget.text.setText(text);
        hideProgress();
    }

    private void hideError() {
        mBinding.widget.error.setVisibility(View.GONE);
        mBinding.widget.text.setText("");
    }

    private void showControl(View view) {
        mInput.cancelSeek();
        updateDiscMenuVisibility();
        syncPlayPause();
        syncDanmaku();
        boolean wasShown = mControlShown;
        mControlShown = true;
        View bar = mBinding.control.getRoot();
        if (!wasShown) {
            bar.animate().cancel();
            bar.setVisibility(View.VISIBLE);
            // 从屏幕底部边缘滑入：起始位置在自身高度之外（若正在滑出则从其当前位置接着滑回来）
            float from = bar.getTranslationY();
            bar.setTranslationY(from > 0f ? from : slideDistance(bar, ResUtil.dp2px(160)));
            bar.animate().translationY(0f).setDuration(SLIDE_IN_DURATION).setInterpolator(SLIDE_IN).start();
            showTop();
        }
        view.requestFocus();
        scheduleAutoHide();
    }

    private void hideControl() {
        // 浮层未关闭时控制条不响应隐藏：两个层级一起常驻。
        if (mSheet != null && mSheet.isShowing()) return;
        App.removeCallbacks(mR1);
        if (!mControlShown) return;
        mControlShown = false;
        View bar = mBinding.control.getRoot();
        bar.animate().cancel();
        bar.animate().translationY(slideDistance(bar, ResUtil.dp2px(160))).setDuration(SLIDE_OUT_DURATION).setInterpolator(SLIDE_OUT).withEndAction(() -> {
            bar.setVisibility(View.GONE);
            bar.setTranslationY(0f);
        }).start();
        hideTop();
    }

    /** 控制条是否处于「呼出」状态：以逻辑位为准，滑动动画期间不会误判。 */
    private boolean isControlVisible() {
        return mControlShown;
    }

    /** 滑出距离：优先用真实高度，首帧尚未布局时退回估算值。 */
    private float slideDistance(View view, int fallback) {
        return view.getHeight() > 0 ? view.getHeight() : fallback;
    }

    /**
     * 顶部信息条：全屏播放页在控制条呼出时，左上角「影片名称：+ 集数」、右上角时间。
     * 与进度条同样从天顶边缘滑入，插值先快后慢。
     */
    private void showTop() {
        if (!isFullscreen()) return;
        View top = mBinding.widget.top;
        top.animate().cancel();
        float from = top.getTranslationY();
        top.setTranslationY(from < 0f ? from : -slideDistance(top, ResUtil.dp2px(72)));
        top.setVisibility(View.VISIBLE);
        top.animate().translationY(0f).setDuration(SLIDE_IN_DURATION).setInterpolator(SLIDE_IN).start();
    }

    private void hideTop() {
        View top = mBinding.widget.top;
        if (!isVisible(top)) return;
        top.animate().cancel();
        top.animate().translationY(-slideDistance(top, ResUtil.dp2px(72))).setDuration(SLIDE_OUT_DURATION).setInterpolator(SLIDE_OUT).withEndAction(() -> {
            top.setVisibility(View.GONE);
            top.setTranslationY(0f);
        }).start();
    }

    private void hideCenter() {
        mBinding.widget.center.setVisibility(View.GONE);
    }

    private void setTraffic() {
        Traffic.setSpeed(mBinding.progress.traffic);
        App.post(mR3, 1000);
    }

    /**
     * 自动隐藏状态机的唯一入口：先清掉旧计时，再按当前条件决定是否重新计时。
     *
     * <p>四个前置条件，任一不满足就不计时（控制条常驻）：
     * <ol>
     *   <li>控制条可见 —— 不可见时谈不上隐藏；</li>
     *   <li>不在拖动进度条 —— 拖动期间不打断；</li>
     *   <li>窗口持有焦点 —— 任一弹窗 / 播放设置浮层打开时 activity 会失去窗口焦点，
     *      此时控制条与浮层一起常驻；弹窗关闭后由 {@link #onWindowFocusChanged(boolean)} 再次进入本方法重新计时；</li>
     *   <li>处于播放意图 —— 暂停态下呼出的控制条常驻，恢复播放后重新计时。</li>
     * </ol>
     * 所有「重新计时」的动作（按键、拖动结束、弹窗关闭、播放状态变化）都收敛到这里，避免多套计时互相打架。
     */
    private void scheduleAutoHide() {
        App.removeCallbacks(mR1);
        if (!isControlVisible()) return;
        if (isScrubbing()) return;
        if (mSheet != null && mSheet.isShowing()) return;
        if (!hasWindowFocus()) return;
        if (!isPlayRequested()) return;
        App.post(mR1, HIDE_DELAY);
    }

    /**
     * 播放意图：只有按过暂停键才为 false。
     * 缓冲、卡顿期间 {@code Player.isPlaying()} 也是 false，但播放意图仍是「播放」，
     * 所以这里读 {@code playWhenReady}，避免缓冲时被误判成暂停态而永不隐藏。
     */
    private boolean isPlayRequested() {
        if (isWebPlaybackActive()) return isWebPlaybackPlaying();
        return controller() != null && controller().getPlayWhenReady();
    }

    @Override
    protected void onScrubbingChanged(boolean scrubbing) {
        if (scrubbing) App.removeCallbacks(mR1);
        else scheduleAutoHide();
    }

    /**
     * 弹窗层级的统一识别点：任一 Dialog / PopupWindow 打开时 activity 会失去窗口焦点，
     * 关闭后重新获得。
     *
     * <p>失去焦点 → 冻结自动隐藏（控制条、播放设置浮层一起常驻，且控制条此时不响应任何操作）；
     * 重新获得 → 按 {@link #scheduleAutoHide()} 的条件重新激活 10 秒计时。
     * 这样所有「从控制条弹出的弹窗」都被同一套逻辑覆盖，不需要给每个对话框挂回调。
     */
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) scheduleAutoHide();
        else App.removeCallbacks(mR1);
    }

    private void setR2Callback() {
        App.post(mR2, 500);
    }

    private void setArtwork(String url) {
        mHistory.setVodPic(url);
        setArtwork();
    }

    private void setArtwork() {
        ImgUtil.load(this, mHistory.getVodPic(), mArtworkTarget);
    }

    private final CustomTarget<Drawable> mArtworkTarget = new CustomTarget<>() {
        @Override
        public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
            mBinding.player.setDefaultArtwork(resource);
        }

        @Override
        public void onLoadFailed(@Nullable Drawable errorDrawable) {
            mBinding.player.setDefaultArtwork(errorDrawable);
        }

        @Override
        public void onLoadCleared(@Nullable Drawable placeholder) {
            mBinding.player.setDefaultArtwork(null);
        }
    };

    private void setPartAdapter() {
        mPartAdapter.addAll(PartUtil.split(mHistory.getVodName()));
        mBinding.part.setVisibility(View.VISIBLE);
        setR2Callback();
    }

    private void saveHistory(boolean exit) {
        if (!isWebPlaybackActive() && !isPlaybackCurrent()) return;
        long position = getActivePlaybackPosition();
        long duration = getActivePlaybackDuration();
        if (mVod != null) mVod.saveHistory(exit, System.currentTimeMillis(), position, duration);
    }

    private void checkKeepImg() {
        mBinding.keep.setCompoundDrawablesWithIntrinsicBounds(Keep.find(getHistoryKey()) == null ? R.drawable.ic_detail_keep_off : R.drawable.ic_detail_keep_on, 0, 0, 0);
    }

    private void createKeep() {
        Keep keep = new Keep();
        keep.setKey(getHistoryKey());
        keep.setCid(VodConfig.getCid());
        keep.setVodPic(mHistory.getVodPic());
        keep.setVodName(mHistory.getVodName());
        keep.setSiteName(getSite().getName());
        keep.setCreateTime(System.currentTimeMillis());
        keep.save();
    }

    private void updateKeep() {
        Keep keep = Keep.find(getHistoryKey());
        if (keep != null) {
            keep.setVodName(mHistory.getVodName());
            keep.setVodPic(mHistory.getVodPic());
            keep.save();
        }
    }

    private final PlaybackService.NavigationCallback mNavigationCallback = new PlaybackService.NavigationCallback() {

        @Override
        public Boolean isKept() {
            return mHistory == null ? null : Keep.find(getHistoryKey()) != null;
        }

        @Override
        public boolean onKeep() {
            if (mHistory == null) return false;
            VideoActivity.this.onKeep();
            return true;
        }

        @Override
        public void onSaveProgress() {
            saveHistory(true);
        }

        @Override
        public boolean onPush(String url) {
            if (isFinishing() || isDestroyed()) return false;
            mVod.redirectPush(url);
            return true;
        }

        @Override
        public boolean isExternalPlaybackActive() {
            return isWebPlaybackActive();
        }

        @Override
        public void onPlay() {
            VideoActivity.this.onPlay();
        }

        @Override
        public void onPause() {
            VideoActivity.this.onPaused();
        }

        @Override
        public void onNext() {
            checkNext();
        }

        @Override
        public void onPrev() {
            checkPrev();
        }

        @Override
        public void onStop() {
            finish();
        }

        @Override
        public void onReplay() {
            VideoActivity.this.onReplay();
        }
    };

    @Override
    protected String getPlaybackKey() {
        return getHistoryKey();
    }

    @Override
    protected void onPrepare() {
        setPlaybackMode();
    }

    @Override
    protected void onDecodeChanged() {
        setPlaybackMode();
    }

    @Override
    protected void onTracksChanged() {
        setTrackVisible();
    }

    @Override
    protected void onMediaOptionsChanged() {
        setMediaOptionVisible();
    }

    @Override
    protected void onError(String msg) {
        mVod.playbackError(msg);
    }

    @Override
    protected void onReclaim() {
        mVod.refresh();
    }

    private void updateDiscMenuVisibility() {
        View button = mBinding.control.action.discMenu;
        boolean focused = button.hasFocus();
        boolean available = hasDiscMenu();
        button.setVisibility(available ? View.VISIBLE : View.GONE);
        if (!available && focused && mBinding.control.getRoot().getVisibility() == View.VISIBLE) mBinding.control.action.opening.requestFocus();
    }

    @Override
    protected void onStateChanged(int state) {
        switch (state) {
            case Player.STATE_BUFFERING:
                if (isDiscMenuTransition()) hideProgress();
                else showProgress();
                mClock.setCallback(null);
                break;
            case Player.STATE_READY:
                hideProgress();
                player().reset();
                mClock.setCallback(this);
                break;
            case Player.STATE_ENDED:
                hideProgress();
                if (isDiscMenuActive()) hideCenter();
                mClock.setCallback(null);
                if (isWebPlaybackActive()) onPlaybackEnded();
                break;
        }
    }

    @Override
    protected void onPreloadError(Object tag) {
        if (tag instanceof VodPlayRequest request) mVod.onPreloadError(request);
    }

    @Override
    protected void onPreloadTransition(Object tag, long oldPositionMs, long oldDurationMs) {
        mInput.cancelSeek();
        if (tag instanceof VodPlayRequest request) mVod.onPreloadTransition(request, oldPositionMs, oldDurationMs);
    }

    @Override
    protected void onPlaybackEnded() {
        if (ExternalPlayback.returnsResult(getIntent())) {
            externalPlaybackCompleted = true;
            finish();
            return;
        }
        mVod.playbackEnded();
    }

    @Override
    public void finish() {
        if (ExternalPlayback.returnsResult(getIntent())) ExternalPlayback.setResult(this, externalPlaybackCompleted ? getActivePlaybackDuration() : getActivePlaybackPosition(), externalPlaybackCompleted);
        super.finish();
    }

    @Override
    protected void onPlayingChanged(boolean isPlaying) {
        mVod.onPlayingChanged(isPlaying && isPlayerPlaying());
        syncPlayPause();
        // 播放 ↔ 暂停的切换会改变自动隐藏策略：暂停时取消计时、恢复播放时重新计 10 秒。
        scheduleAutoHide();
        // 屏幕中间只在拖动 / 快进快退时出现提示层；暂停不再弹任何中间控件，
        // 顶部信息条跟随控制条显隐，不随播放状态单独开关。
        if (isDiscMenuActive() || isPlaying) hideCenter();
        // 真正开始播放后才触发 TMDB 背景图 / logo 抓取（每部片一次，内部有缓存与去重）。
        if (isPlaying) {
            mTmdbPlaybackStarted = true;
            tryFetchTmdb();
        }
    }

    @Override
    public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
        if (!playWhenReady && isOwner()) mInput.cancelSeek();
    }

    @Override
    public void onPositionDiscontinuity(Player.PositionInfo oldPosition, Player.PositionInfo newPosition, int reason) {
        if (isPlaybackCurrent() && oldPosition.mediaItemIndex == newPosition.mediaItemIndex && (reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT)) mVod.onPlaybackSeek();
    }

    @Override
    public void onTimeChanged(long time) {
        if (!canTrackPlaybackProgress()) return;
        long position = getPlayerPosition();
        long duration = getPlayerDuration();
        if (position < 0) return;
        mVod.onTimeChanged(time, position, duration);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (isRedirect()) return;
        if (event.getType() == RefreshEvent.Type.DETAIL) mVod.requestDetail();
        else if (event.getType() == RefreshEvent.Type.PLAYER) mVod.refresh();
        else if (event.getType() == RefreshEvent.Type.KEEP) checkKeepImg();
        else if (event.getType() == RefreshEvent.Type.SUBTITLE) player().setSub(Sub.from(event.getPath()));
        else if (event.getType() == RefreshEvent.Type.DANMAKU) player().setDanmaku(Danmaku.from(event.getPath()));
    }

    @Override
    protected long startPositionMs() {
        return mVod == null ? C.TIME_UNSET : mVod.startPositionMs();
    }

    @Override
    protected boolean supportsWebSeek() {
        return true;
    }

    @Override
    protected void onWebProgressChanged(boolean visible) {
        if (visible) showProgress();
        else hideProgress();
    }

    @Override
    protected void onWebPlaybackChanged(boolean active) {
        PlaybackAction.setWebPlaybackMode(active, mBinding.control.action.player, mBinding.control.action.decoder, mBinding.control.action.reset, mBinding.control.action.replay, mBinding.control.action.repeat, mBinding.control.action.speed, mBinding.control.action.scale, mBinding.control.action.text, mBinding.control.action.audio, mBinding.control.action.video, mBinding.control.action.danmaku, mBinding.control.action.edition, mBinding.control.action.chapter, mBinding.control.action.opening, mBinding.control.action.ending);
        if (!active) {
            setPlaybackMode();
            setTrackVisible();
            setMediaOptionVisible();
            mBinding.control.action.danmaku.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onWebFullscreenChanged(boolean fullscreen) {
        if (fullscreen) {
            webFullscreen = !isFullscreen();
            if (webFullscreen) enterFullscreen();
        } else if (webFullscreen) {
            webFullscreen = false;
            if (isFullscreen()) exitFullscreen();
        }
    }

    private void setTrackVisible() {
        if (isWebPlaybackActive()) return;
        PlaybackAction.setTracks(player(), mBinding.control.action.text, mBinding.control.action.audio, mBinding.control.action.video);
    }

    private void setMediaOptionVisible() {
        if (isWebPlaybackActive()) return;
        PlaybackAction.setMediaOptions(player(), mBinding.control.action.edition, mBinding.control.action.chapter);
    }

    @Override
    public void onItemClick(Vod item) {
        mVod.selectSource(item);
    }

    @Override
    public void onParse(Parse item) {
        mVod.selectParse(item);
    }

    private void onPaused() {
        mInput.cancelSeek();
        if (pauseWebPlayback()) return;
        controller().pause();
    }

    private void onPlay() {
        mInput.cancelSeek();
        if (resumeWebPlayback()) return;
        if (mHistory != null && isEnded() && !isDiscMenuActive() && !isIsoPlaybackOngoing()) controller().seekTo(mVod.openingPositionMs());
        if (!player().isEmpty() && isIdle()) controller().prepare();
        if (isEnded() && isIsoPlaybackOngoing()) controller().setPlayWhenReady(true);
        else controller().play();
    }

    private boolean isPlayingForControls() {
        if (isWebPlaybackActive()) return isWebPlaybackPlaying();
        return player().isPlaying() || ((isDiscMenuActive() || (isEnded() && isIsoPlaybackOngoing())) && controller().getPlayWhenReady());
    }

    private boolean onSeekBack() {
        mInput.cancelSeek();
        controller().seekBack();
        return true;
    }

    private boolean onSeekForward() {
        mInput.cancelSeek();
        controller().seekForward();
        return true;
    }

    private boolean isFullscreen() {
        return fullscreen;
    }

    private void setFullscreen(boolean fullscreen) {
        this.fullscreen = fullscreen;
    }

    public boolean isUseParse() {
        return useParse;
    }

    public void setUseParse(boolean useParse) {
        this.useParse = useParse;
    }

    private View getFocus1() {
        return mFocus1 == null || mFocus1.getVisibility() != View.VISIBLE ? mBinding.video : mFocus1;
    }

    private View getFocus2() {
        return mFocus2 == null || !mFocus2.isAttachedToWindow() || mFocus2.getVisibility() != View.VISIBLE ? mBinding.control.action.playPause : mFocus2;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        // 浮层内左右键：按键不锁死，但焦点必须留在浮层里（见 handleSheetHorizontalKey）。
        if (mSheet != null && mSheet.isShowing() && handleSheetHorizontalKey(event)) return true;
        // 浮层内上下键：到边界后吃掉按键，避免焦点掉出弹层（字幕 / 音轨 / 弹幕列表末行）。
        if (mSheet != null && mSheet.isShowing() && handleSheetVerticalKey(event)) return true;
        if (keyCode == KeyEvent.KEYCODE_MENU && KeyUtil.isActionDown(event)) mInput.cancelSeek();
        boolean discMenuShortcut = keyCode == KeyEvent.KEYCODE_MEDIA_TOP_MENU || keyCode == KeyEvent.KEYCODE_TV_CONTENTS_MENU;
        boolean discMenuNavigation = keyCode != KeyEvent.KEYCODE_MENU && isFullscreen() && !isControlVisible();
        if ((discMenuShortcut || discMenuNavigation) && dispatchDiscMenuKey(event)) {
            mInput.cancelSeek();
            hideControl();
            return true;
        }
        if (isFullscreen() && KeyUtil.isMenuKey(event)) onToggle();
        if (isControlVisible()) scheduleAutoHide();
        if (isControlVisible()) mFocus2 = getCurrentFocus();
        if (isFullscreen() && !isControlVisible() && service() != null && mInput.onKeyEvent(event)) return true;
        if (KeyUtil.isMediaFastForward(event)) return onSeekForward();
        if (KeyUtil.isMediaRewind(event)) return onSeekBack();
        return super.dispatchKeyEvent(event);
    }

    /**
     * 浮层内的左右键：按键不锁死，但焦点锁在浮层里。
     *
     * <p>播放设置、弹幕等浮层里既有「同一排多个按钮」也有滑块：
     * <ul>
     *   <li>滑块（字体缩放 / 最大滚动行数）需要左右调值 → 原样放行；</li>
     *   <li>同一排的按钮 → 手动 focusSearch，排内左右移动；</li>
     *   <li>焦点已经跑到浮层外，或那一侧没有浮层内的邻居 → 吃掉按键，焦点不动、绝不外逸。</li>
     * </ul>
     * 之前对左右键一律 return true，导致「一排多个按钮」根本无法左右切换，这里改为只兜住越界。
     */
    /**
     * 浮层内的上下键：与左右键同一套边界策略。
     *
     * <p>字幕 / 音轨 / 弹幕列表用的是普通 RecyclerView，没有 {@code SheetList} 那层首尾保护；
     * 焦点在最后一个条目或最后一个控件时再按下，系统焦点搜索会返回浮层外的视图，表现为「焦点丢了」。
     * 这里只在「浮层内确实没有下一个可聚焦控件」时吃掉按键，正常换行仍然交给系统处理。
     */
    private boolean handleSheetVerticalKey(KeyEvent event) {
        int keyCode = event.getKeyCode();
        if (keyCode != KeyEvent.KEYCODE_DPAD_UP && keyCode != KeyEvent.KEYCODE_DPAD_DOWN) return false;
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
        View focused = getCurrentFocus();
        if (focused == null || !mSheet.contains(focused)) return false;
        int direction = keyCode == KeyEvent.KEYCODE_DPAD_UP ? View.FOCUS_UP : View.FOCUS_DOWN;
        View next = focused.focusSearch(direction);
        if (next != null && next != focused && mSheet.contains(next)) return false;
        // 浮层内没有下一个可聚焦控件：吃掉按键，焦点保持不动。
        return true;
    }

    private boolean handleSheetHorizontalKey(KeyEvent event) {
        int keyCode = event.getKeyCode();
        if (keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
        View focused = getCurrentFocus();
        if (focused == null || !mSheet.contains(focused)) {
            mSheet.requestFocusInside();
            return true;
        }
        if (focused instanceof com.google.android.material.slider.Slider) return false;
        // 输入框内左右键要先用来移动光标，到边界再由 CustomEditText 自己找焦点；
        // 若在这里一律交给 focusSearch，弹幕搜索框就没法编辑关键词了。
        if (focused instanceof android.widget.EditText) return false;
        if (event.getAction() != KeyEvent.ACTION_DOWN) return true;
        int direction = keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? View.FOCUS_LEFT : View.FOCUS_RIGHT;
        View next = focused.focusSearch(direction);
        if (next != null && next != focused && mSheet.contains(next)) next.requestFocus(direction);
        return true;
    }

    @Override
    public void onSeeking(long time) {
        mBinding.widget.center.setVisibility(View.VISIBLE);
        setSeekPreviewTime(mBinding.widget.duration, mBinding.widget.position, time);
        mBinding.widget.statusIcon.setImageResource(time > 0 ? R.drawable.ic_widget_forward : R.drawable.ic_widget_rewind);
        hideProgress();
    }

    @Override
    public void onSeekEnd(long time) {
        if (seekTo(time)) hideCenter();
    }

    @Override
    public void onSeekCancel() {
        hideCenter();
    }

    @Override
    public boolean onSpeedPressStart() {
        if (!player().isPlaying()) return false;
        return PlaybackAction.startSpeedPress(player(), mBinding.widget.message);
    }

    @Override
    public void onSpeedPressEnd() {
        PlaybackAction.endSpeedPress(player(), mBinding.widget.message);
    }

    @Override
    public void onKeyUp() {
        // 上键与其他方向键保持一致：只负责呼出控制条，焦点按统一的 getFocus2() 规则恢复。
        // 不再因为「当前位置可设片头/片尾」就把默认焦点投到对应按钮上。
        showControl(getFocus2());
    }

    @Override
    public void onKeyDown() {
        showControl(mBinding.control.action.playPause);
    }

    /**
     * 确定键（播放/暂停）响应规则：
     * <ul>
     *   <li>播放中 → 暂停：<b>唯一</b>会呼出控制条的路径（呼出后因处于暂停态而常驻）；</li>
     *   <li>暂停中 → 播放：只恢复播放，不呼出控制条，也不改动控制条的显示状态；</li>
     *   <li>播放器为空（解析失败等）→ 沿用原有的重试逻辑。</li>
     * </ul>
     */
    @Override
    public void onKeyCenter() {
        if (isPlayingForControls()) {
            onPaused();
            showControl(mBinding.control.action.playPause);
        } else if (!isWebPlaybackActive() && player().isEmpty()) {
            onRefresh();
        } else {
            onPlay();
        }
    }

    @Override
    public void onSingleTap() {
        if (isFullscreen()) onToggle();
    }

    @Override
    public void onDoubleTap() {
        if (isFullscreen()) onKeyCenter();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == 1001) PlaybackIntent.onExternalResult(data, service()::dispatchNext, controller()::seekTo);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mClock.stop().start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 设置里可能刚改过「弹幕加载 / 进度条弹幕热力图」，回到播放页立即按新开关重贴一次
        applyHeatmap();
    }

    @Override
    protected void onStop() {
        mInput.cancelSeek();
        super.onStop();
        saveHistory(false);
        if (PlayerSetting.isBackgroundOff()) mClock.stop();
        if (mSheet != null) mSheet.dismissNow();
        onSheetDismiss();
    }

    @Override
    protected void onBackInvoked() {
        if (mSheet != null && mSheet.isShowing()) {
            hideSheet();
        } else if (handleWebViewNavigation()) {
            return;
        } else if (!hasWindowFocus()) {
            // 其它弹窗尚未关闭：返回键交给弹窗自己处理，控制条不响应任何操作。
            return;
        } else if (isControlVisible()) {
            hideControl();
        } else if (handleDiscMenuBack()) {
            return;
        } else if (isVisible(mBinding.widget.center)) {
            mInput.cancelSeek();
            hideCenter();
        } else if (isFullscreen()) {
            exitFullscreen();
        } else {
            mViewModel.stopSearch();
            if (isTaskRoot()) startActivity(new Intent(this, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            super.onBackInvoked();
        }
    }

    @Override
    protected void onDestroy() {
        mInput.cancelSeek();
        mClock.release();
        saveHistory(true);
        DanmakuApi.cancel();
        RefreshEvent.keep();
        App.removeCallbacks(mR1, mR2, mR3, mR4);
        App.removeCallbacks(mTmdbFallback);
        App.removeCallbacks(mHeroFallback);
        mBinding.videoHero.animate().cancel();
        TmdbApi.removeOnFetched(mHeroRefresh);
        DanmakuHeat.removeListener(mHeatListener);
        if (mSheet != null) mSheet.dismissNow();
        // 兜底：onSheetDismiss 的延帧清钉可能还没跑到，别把 activated 残留在按钮上。
        setControlPinned(null);
        super.onDestroy();
    }
}
