package com.fongmi.android.NewTV.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.NewTV.bean.Danmaku;
import com.fongmi.android.NewTV.databinding.AdapterDanmakuBinding;
import com.fongmi.android.NewTV.ui.custom.PingPongMarqueeTextView;

import java.util.ArrayList;
import java.util.List;

public class DanmakuAdapter extends RecyclerView.Adapter<DanmakuAdapter.ViewHolder> {

    private final OnClickListener listener;
    private final List<Danmaku> mItems;

    public DanmakuAdapter(OnClickListener listener) {
        this.listener = listener;
        this.mItems = new ArrayList<>();
    }

    public interface OnClickListener {

        void onItemClick(Danmaku item);
    }

    public void clear() {
        int size = mItems.size();
        mItems.clear();
        notifyItemRangeRemoved(0, size);
    }

    public DanmakuAdapter addAll(List<Danmaku> items) {
        if (items == null || items.isEmpty()) return this;
        mItems.addAll(items);
        notifyItemRangeInserted(0, mItems.size());
        return this;
    }

    public int getSelected() {
        for (int i = 0; i < mItems.size(); i++) if (mItems.get(i).isSelected()) return i;
        return 0;
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterDanmakuBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Danmaku item = mItems.get(position);
        holder.binding.text.setText(item.getName());
        holder.binding.text.setSelected(item.isSelected());
        // 业务 selected 仍表达「当前生效的弹幕源」，但跑马灯只看焦点行，两者互不干扰。
        holder.setMarquee(holder.itemView.isFocused());
    }

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        private final AdapterDanmakuBinding binding;

        public ViewHolder(@NonNull AdapterDanmakuBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            itemView.setOnClickListener(this);
            // 只让焦点所在行滚；本布局的根 View 就是那个跑马灯 TextView。
            itemView.setOnFocusChangeListener((view, hasFocus) -> setMarquee(hasFocus));
        }

        /** 用 View 作静态类型再判：mobile 的条目布局是普通 TextView，会自然跳过。 */
        private void setMarquee(boolean running) {
            View text = binding.text;
            if (text instanceof PingPongMarqueeTextView marquee) marquee.setMarqueeRunning(running);
        }

        @Override
        public void onClick(View view) {
            listener.onItemClick(mItems.get(getLayoutPosition()));
        }
    }
}
