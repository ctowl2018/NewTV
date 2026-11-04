package com.fongmi.android.NewTV.ui.presenter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.leanback.widget.Presenter;

import com.bumptech.glide.Glide;
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

    private void setLayoutSize() {
        int space = ResUtil.dp2px(48) + ResUtil.dp2px(16 * (BASE_COLUMN - 1));
        int base = ResUtil.getScreenWidth() - space;
        width = (int) (base / (float) BASE_COLUMN * BASE_SCALE);
        height = (int) (width / 0.75f);
    }

    /**
     * 行内容宽度：4.5 个海报 + 4 段横向间距。
     *
     * <p>只收窄右边 —— 行网格仍从原来那条左边线起排，第 5 张海报露出半张，
     * 提示右侧还有内容；页面其它元素的位置不受影响。
     */
    public int getRowWidth(int spacing) {
        return Math.round(4.5f * width + 4 * spacing);
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
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull Presenter.ViewHolder viewHolder, Object object) {
        History item = (History) object;
        boolean same = item.getVodName().equals(item.getVodRemarks());
        ViewHolder holder = (ViewHolder) viewHolder;
        setClickListener(holder.view, item);
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