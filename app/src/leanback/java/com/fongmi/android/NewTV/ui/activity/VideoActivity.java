package com.fongmi.android.NewTV.ui.activity;

import android.animation.TimeInterpolator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
import com.fongmi.android.NewTV.bean.Vod;
import com.fongmi.android.NewTV.databinding.ActivityVideoBinding;
import com.fongmi.android.NewTV.db.AppDatabase;
import com.fongmi.android.NewTV.event.RefreshEvent;
import com.fongmi.android.NewTV.impl.CustomTarget;
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
import com.fongmi.android.NewTV.ui.adapter.ArrayAdapter;
import com.fongmi.android.NewTV.ui.adapter.EpisodeAdapter;
import com.fongmi.android.NewTV.ui.adapter.FlagAdapter;
import com.fongmi.android.NewTV.ui.adapter.PartAdapter;
import com.fongmi.android.NewTV.ui.adapter.QualityAdapter;
import com.fongmi.android.NewTV.ui.adapter.QuickAdapter;
import com.fongmi.android.NewTV.ui.custom.VodInput;
import com.fongmi.android.NewTV.ui.custom.CustomMovement;
import com.fongmi.android.NewTV.ui.helper.ControlFocusEffect;
import com.fongmi.android.NewTV.ui.helper.ToolbarHeader;
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

public class VideoActivity extends PlaybackActivity implements VodPlaybackHost, VodInput.Listener, ParseDialog.Listener, ArrayAdapter.OnClickListener, FlagAdapter.OnClickListener, EpisodeAdapter.OnClickListener, QualityAdapter.OnClickListener, QuickAdapter.OnClickListener, Clock.Callback {

    /** 控制条弹出后的自动隐藏延时：10 秒（原为 5 秒）。 */
    private static final long HIDE_DELAY = 10_000L;
    /** 控制条 / 顶部信息条的滑入、滑出动画：入先快后慢（减速），出先慢后快（加速）。 */
    private static final long SLIDE_IN_DURATION = 240L;
    private static final long SLIDE_OUT_DURATION = 200L;
    private static final TimeInterpolator SLIDE_IN = new PathInterpolator(0.05f, 0.7f, 0.1f, 1f);
    private static final TimeInterpolator SLIDE_OUT = new PathInterpolator(0.3f, 0f, 0.8f, 0.15f);
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
    private ToolbarHeader mHeader;
    private VideoSheet mSheet;
    private PanelSheet mPanelSheet;
    private TrackSheet mTrackSheet;
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
    private final Runnable mTmdbFallback = this::onTmdbDanmakuTimeout;

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
        updateNavigationKey();
        checkId();
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        mHeader = ToolbarHeader.bind(this, mBinding.toolbar);
        // 播放详情页属于需要顶部渐变遮罩的四个页面之一
        mHeader.setScrimVisible(true);
        mFrameParams = mBinding.video.getLayoutParams();
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
        checkCast();
        checkId();
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    protected void initEvent() {
        mBinding.keep.setOnClickListener(view -> onKeep());
        mBinding.video.setOnClickListener(view -> onVideo());
        mBinding.change.setOnClickListener(view -> onChange());
        mBinding.content.setOnClickListener(view -> onContent());
        mBinding.control.action.text.setOnClickListener(this::onTrack);
        mBinding.control.action.audio.setOnClickListener(this::onTrack);
        mBinding.control.action.video.setOnClickListener(this::onTrack);
        // 片尾的方向与片头相反：片头 ↑ 加 / ↓ 减；片尾 ↑ 减 / ↓ 加（把「越靠近结尾」对应到向上）
        mBinding.control.action.ending.setUpListener(this::onEndingSub);
        mBinding.control.action.ending.setDownListener(this::onEndingAdd);
        mBinding.control.action.opening.setUpListener(this::onOpeningAdd);
        mBinding.control.action.opening.setDownListener(this::onOpeningSub);
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
        ControlFocusEffect.attach(mBinding.control.getRoot(), glow, scaleOnly);
        // 播放键的图标比其它按钮大一圈，光晕也要相应放大，边缘过渡才明显
        ControlFocusEffect.setHaloScale(mBinding.control.action.playPause, 1.45f);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mHeader != null) mHeader.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mHeader != null) mHeader.onPause();
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
        mTrackSheet = new TrackSheet(this, this::hideSheet, this::pickSubtitleFile);
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
        List<String> aliases = new ArrayList<>();
        if (mHistory != null) aliases.add(mHistory.getVodName());
        aliases.add(mTmdbPosterName);
        aliases.add(getName());
        aliases.add(mTmdbKeyword);
        // 结果本身由 TmdbApi 写入本地缓存并和这些别名关联，首页最近观看按海报名即可命中。
        TmdbApi.fetch(mTmdbKeyword, mTmdbPosterName, aliases);
    }

    @Override
    public void renderDetail(Vod item, History history) {
        mHistory = history;
        mBinding.progressLayout.showContent();
        renderFallbackName(item.getName());
        mBinding.video.requestFocus();
        App.removeCallbacks(mR4);
        setArtwork(item.getPic());
        checkKeepImg();
        setText(item);
        updateKeep();
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
        if (!pic.isEmpty()) setArtwork();
        updateKeep();
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
        // 全屏时隐藏顶部 logo 栏：整屏交给画面，返回键退出全屏后再恢复。
        mBinding.toolbar.getRoot().setVisibility(View.GONE);
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
        mBinding.toolbar.getRoot().setVisibility(View.VISIBLE);
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

    private void onOpeningAdd() {
        setOpening(Math.max(0, Math.max(0, mHistory.getOpening()) + 1000));
    }

    private void onOpeningSub() {
        setOpening(Math.max(0, Math.max(0, mHistory.getOpening()) - 1000));
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
        if (player().canSetEnding(position, duration)) setEnding(duration - position);
    }

    private void onEndingAdd() {
        setEnding(Math.max(0, Math.max(0, mHistory.getEnding()) + 1000));
    }

    private void onEndingSub() {
        setEnding(Math.max(0, Math.max(0, mHistory.getEnding()) - 1000));
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
        setSeekFocusable(false);
        setOutsideFocusable(false);
    }

    /** 浮层关闭后恢复外部的焦点能力，并重新激活自动隐藏计时（暂停态由 scheduleAutoHide 内部再判一次）。 */
    private void onSheetDismiss() {
        setSeekFocusable(true);
        setOutsideFocusable(true);
        scheduleAutoHide();
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
        // 列条宽度按「列表里最长的一行」自适应（与当前选中行无关）：最少 3 个汉字、最多 15 个汉字，
        // 超过 15 个汉字的部分交给条目 marquee 左右滚动；指示器槽位额外加宽，不占文字的字符宽度。
        int textWidth = Math.min(Math.max(measureSheetText(texts), measureSheetChars(3)), measureSheetChars(15));
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
    private int measureSheetText(List<String> texts) {
        if (mSheetProbe == null) {
            mSheetProbe = new TextView(this);
            mSheetProbe.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        }
        float max = 0;
        for (String text : texts) max = Math.max(max, mSheetProbe.getPaint().measureText(text));
        return (int) Math.ceil(max);
    }

    /** {@code count} 个 15sp 汉字的像素宽（列条的最小/最大宽度按汉字数定义）。 */
    private int measureSheetChars(int count) {
        if (mSheetProbe == null) {
            mSheetProbe = new TextView(this);
            mSheetProbe.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        }
        return (int) Math.ceil(mSheetProbe.getPaint().measureText("汉") * count);
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
        return mFocus2 == null || !mFocus2.isAttachedToWindow() || mFocus2.getVisibility() != View.VISIBLE || mFocus2 == mBinding.control.action.opening || mFocus2 == mBinding.control.action.ending ? mBinding.control.action.playPause : mFocus2;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        // 浮层内左右键：按键不锁死，但焦点必须留在浮层里（见 handleSheetHorizontalKey）。
        if (mSheet != null && mSheet.isShowing() && handleSheetHorizontalKey(event)) return true;
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
    private boolean handleSheetHorizontalKey(KeyEvent event) {
        int keyCode = event.getKeyCode();
        if (keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
        View focused = getCurrentFocus();
        if (focused == null || !mSheet.contains(focused)) {
            mSheet.requestFocusInside();
            return true;
        }
        if (focused instanceof com.google.android.material.slider.Slider) return false;
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
        long position = player().getPosition();
        long duration = player().getDuration();
        if (player().canSetOpening(position, duration)) showControl(mBinding.control.action.opening);
        else if (player().canSetEnding(position, duration)) showControl(mBinding.control.action.ending);
        else showControl(getFocus2());
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
        if (mSheet != null) mSheet.dismissNow();
        super.onDestroy();
    }
}
