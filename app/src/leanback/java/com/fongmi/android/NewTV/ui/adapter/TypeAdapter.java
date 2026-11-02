package com.fongmi.android.NewTV.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.NewTV.R;
import com.fongmi.android.NewTV.bean.Cache;
import com.fongmi.android.NewTV.bean.Class;
import com.fongmi.android.NewTV.databinding.AdapterTypeBinding;
import com.fongmi.android.NewTV.utils.ResUtil;

import java.util.ArrayList;
import java.util.List;

public class TypeAdapter extends RecyclerView.Adapter<TypeAdapter.ViewHolder> {

    private final OnClickListener mListener;
    private final List<Class> mItems;

    public TypeAdapter(OnClickListener listener) {
        mListener = listener;
        mItems = new ArrayList<>();
    }

    /** 首页项：固定在类型栏最左侧，点击返回首页。 */
    private Class home() {
        Class type = new Class();
        type.setTypeName(ResUtil.getString(R.string.home_vod));
        type.setTypeId("home");
        return type;
    }

    public void addAll(List<Class> items) {
        mItems.clear();
        if (!items.isEmpty()) mItems.add(home());
        mItems.addAll(items);
        notifyDataSetChanged();
    }

    public void clear() {
        mItems.clear();
        notifyDataSetChanged();
    }

    public void setSelected(int position) {
        if (position < 0 || position >= mItems.size()) return;
        for (Class item : mItems) item.setSelected(false);
        mItems.get(position).setSelected(true);
        notifyItemRangeChanged(0, mItems.size());
    }

    public Class get(int position) {
        return mItems.get(position);
    }

    /** 真实分类列表（不含「首页」哨兵项），供其它页面重建导航栏使用。 */
    public List<Class> getTypes() {
        List<Class> types = new ArrayList<>();
        for (Class item : mItems) if (!item.isHome()) types.add(item);
        return types;
    }

    public int indexOf(Class item) {
        return mItems.indexOf(item);
    }

    /** 是否包含首页项（类型列表非空时才会插入）。 */
    public boolean hasHome() {
        return !mItems.isEmpty() && mItems.get(0).isHome();
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterTypeBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Class item = mItems.get(position);
        holder.binding.text.setText(item.getTypeName());
        holder.binding.text.setSelected(item.isSelected());
        holder.binding.text.setCompoundDrawablePadding(ResUtil.dp2px(4));
        holder.binding.text.setCompoundDrawablesWithIntrinsicBounds(0, 0, getIcon(item), 0);
        holder.binding.text.setListener(item.isHome() ? null : () -> mListener.onRefresh(item));
        holder.binding.getRoot().setOnClickListener(v -> mListener.onItemClick(item));
    }

    private int getIcon(Class item) {
        if (item.isHome()) return 0;
        return Cache.get(item).isEmpty() ? 0 : item.getFilter() ? R.drawable.ic_vod_filter_off : R.drawable.ic_vod_filter_on;
    }

    public interface OnClickListener {

        void onItemClick(Class item);

        void onRefresh(Class item);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterTypeBinding binding;

        ViewHolder(@NonNull AdapterTypeBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
