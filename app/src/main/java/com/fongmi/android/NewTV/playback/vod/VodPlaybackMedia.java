package com.fongmi.android.NewTV.playback.vod;

import androidx.media3.common.MediaMetadata;

import com.fongmi.android.NewTV.api.DanmakuApi;
import com.fongmi.android.NewTV.api.config.VodConfig;
import com.fongmi.android.NewTV.bean.Danmaku;
import com.fongmi.android.NewTV.bean.Episode;
import com.fongmi.android.NewTV.bean.History;
import com.fongmi.android.NewTV.bean.Result;
import com.fongmi.android.NewTV.player.media.MediaItemFactory;
import com.fongmi.android.NewTV.setting.DanmakuSetting;

import java.util.List;
import java.util.function.Consumer;

public final class VodPlaybackMedia {

    private VodPlaybackMedia() {
    }

    public static MediaMetadata metadata(History history, Episode episode) {
        String title = history.getVodName();
        String name = episode.getName();
        if (name.equals(title)) name = "";
        return MediaItemFactory.buildMetadata(title, name, history.getVodPic());
    }

    /**
     * 搜索弹幕并装进播放器；同时把「是否拿到弹幕接口的结果」告知调用方，
     * 供 TMDB 抓取决定用弹幕片名还是海报名称做关键字。
     *
     * @param onDone 成功时回传命中的弹幕（未命中为 null），失败 / 弹幕关闭时同样回传 null
     */
    public static void searchDanmaku(Result result, History history, Episode episode,
                                     Consumer<Danmaku> set, Consumer<Danmaku> add, Consumer<Danmaku> onDone) {
        if (!DanmakuApi.canSearch() || VodConfig.get().getSite(result.getKey()).getDanmaku() == 0) {
            onDone.accept(null);
            return;
        }
        DanmakuApi.search(history.getVodName(), episode.getName(), (List<Danmaku> items) -> {
            Danmaku found = items == null ? null : items.stream().findFirst().orElse(null);
            if (found != null) {
                if (DanmakuSetting.isSpiderFirst() && !result.getDanmaku().isEmpty()) add.accept(found);
                else set.accept(found);
            }
            onDone.accept(found);
        }, error -> onDone.accept(null));
    }
}
