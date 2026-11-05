package com.fongmi.android.NewTV.ui.presenter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.leanback.widget.Presenter;

import com.bumptech.glide.Glide;
import com.fongmi.android.NewTV.R;
import com.fongmi.android.NewTV.bean.History;
import com.fongmi.android.NewTV.databinding.AdapterVodBinding;
import com.fongmi.android.NewTV.utils.ImgUtil;
import com.fongmi.android.NewTV.utils.ResUtil;

public class HistoryPresenter extends Presenter {

    private final OnClickListener listener;
    private int width, height;
    private boolean delete;

    public HistoryPresenter(OnClickListener listener) {
        this.listener = listener;
        setLayoutSize();
    }

    public interface OnClickListener {

        void onItemClick(History item);

        void onItemDelete(History item);

        boolean onLongClick();
    }

    /**
     * 历史海报的基准列数：尺寸永远按「每行 6 列」推算，再固定缩到 {@link #BASE_SCALE}，
     * 因此「设置 → 每行海报数」调整列数时，最近观看的海报大小不再跟着变。
     */
    private static final int BASE_COLUMN = 6;
    private static final float BASE_SCALE = 0.7f;
    /** 历史卡片上文字的字号（sp）：海报缩到 70% 后，12sp 与图片尺寸相称。 */
    private static final float TEXT_SIZE_SP = 12f;
    /** 片名容器的内边距（dp）：左右保持 6dp，上下由 6dp 收到 3dp 以贴合 12sp 文字。 */
    private static final int NAME_PADDING_H_DP = 6;
    private static final int NAME_PADDING_V_DP = 3;

    private void setLayoutSize() {
        int space = ResUtil.dp2px(48) + ResUtil.dp2px(16 * (BASE_COLUMN - 1));
        int base = ResUtil.getScreenWidth() - space;
        width = (int) (base / (float) BASE_COLUMN * BASE_SCALE);
        height = (int) (width / 0.75f);
    }

    public boolean isDelete() {
        return delete;
    }

    public void setDelete(boolean delete) {
        this.delete = delete;
    }

    private void setClickListener(View root, History item) {
        root.setOnLongClickListener(view -> listener.onLongClick());
        root.setOnClickListener(view -> {
            if (isDelete()) listener.onItemDelete(item);
            else listener.onItemClick(item);
        });
    }

    @NonNull
    @Override
    public Presenter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent) {
        ViewHolder holder = new ViewHolder(AdapterVodBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        holder.binding.getRoot().getLayoutParams().width = width;
        holder.binding.image.getLayoutParams().height = height;
        applyCompactText(holder.binding);
        return holder;
    }

    /**
     * 卡片文字统一 12sp，并同步收缩片名容器的高度。
     *
     * <p>只改运行时属性，不动 {@code adapter_vod.xml}：该布局还被收藏页（KeepAdapter）
     * 和手机端（HistoryAdapter）共用，它们保持原先 16sp 的规格。</p>
     *
     * <p>高度收缩靠内边距而不是写死高度：上下 6dp → 3dp、关掉字体额外留白，
     * 片名块正好包住一行 12sp 文字（31dp 左右收到 21dp 左右）；系统字体放大时也不会被裁切。</p>
     */
    private void applyCompactText(AdapterVodBinding binding) {
        binding.name.setTextSize(TEXT_SIZE_SP);
        binding.remark.setTextSize(TEXT_SIZE_SP);
        binding.name.setIncludeFontPadding(false);
        int h = ResUtil.dp2px(NAME_PADDING_H_DP);
        int v = ResUtil.dp2px(NAME_PADDING_V_DP);
        binding.name.setPadding(h, v, h, v);
    }

    @Override
    public void onBindViewHolder(@NonNull Presenter.ViewHolder viewHolder, Object object) {
        History item = (History) object;
        boolean same = item.getVodName().equals(item.getVodRemarks());
        ViewHolder holder = (ViewHolder) viewHolder;
        setClickListener(holder.view, item);
        // 用带 key 的 tag 挂上影片信息：首页全局焦点监听据此把海报与 TMDB 素材关联起来。
        holder.view.setTag(R.id.tmdb_history, item);
        holder.binding.name.setText(item.getVodName());
        // 首页历史卡片不再显示顶部站源标题（该视图仍保留在 adapter_vod.xml 里，收藏页 / 手机端继续用）
        holder.binding.site.setVisibility(View.GONE);
        holder.binding.remark.setText(item.getVodRemarks());
        holder.binding.delete.setVisibility(!delete ? View.GONE : View.VISIBLE);
        holder.binding.remark.setVisibility(delete || same ? View.GONE : View.VISIBLE);
        ImgUtil.load(item.getVodName(), item.getVodPic(), holder.binding.image);
    }

    @Override
    public void onUnbindViewHolder(@NonNull Presenter.ViewHolder viewHolder) {
        ViewHolder holder = (ViewHolder) viewHolder;
        Glide.with(holder.binding.image).clear(holder.binding.image);
    }

    public static class ViewHolder extends Presenter.ViewHolder {

        private final AdapterVodBinding binding;

        public ViewHolder(@NonNull AdapterVodBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}