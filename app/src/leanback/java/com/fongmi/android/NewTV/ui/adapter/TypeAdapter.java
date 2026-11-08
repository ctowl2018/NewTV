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

    /** 「最近观看」项：固定在类型栏最左侧（原「首页」改名），点击回到最近观看。 */
    private Class home() {
        Class type = new Class();
        type.setTypeName(ResUtil.getString(R.string.home_recent));
        type.setTypeId(Class.TYPE_HOME);
        return type;
    }

    /**
     * 「首页」项：固定在类型栏第 2 位，是应用启动的默认页。
     *
     * <p>与真实分类同排在导航里，但不是站点分类；它由 {@code RecommendFragment}
     * 承载，数据来自 {@code homeContent()}。这里只负责生成用于胶囊显示与分页定位的 Class。
     */
    public static Class recommend() {
        Class type = new Class();
        type.setTypeName(ResUtil.getString(R.string.home_recommend));
        type.setTypeId(Class.TYPE_RECOMMEND);
        return type;
    }

    /**
     * 覆盖类型列表。
     *
     * <p>前两枚固定胶囊无条件插入：未配置任何站源时 {@code items} 为空，
     * 导航栏仍要保留「最近观看 / 首页」，样式与位置和有站源时保持一致。
     * 真实分类从第 2 位开始。
     */
    public void addAll(List<Class> items) {
        mItems.clear();
        mItems.add(home());
        mItems.add(recommend());
        if (items != null) mItems.addAll(items);
        notifyDataSetChanged();
    }

    /** 前置固定胶囊的数量：宿主把分页下标换算成胶囊下标（或反向）时统一使用。 */
    public int offset() {
        int offset = 0;
        for (Class item : mItems) {
            if (!item.isBuiltIn()) break;
            offset++;
        }
        return offset;
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

    /** 真实分类列表（不含两枚前置固定胶囊），供分页与导航栏重建使用。 */
    public List<Class> getTypes() {
        List<Class> types = new ArrayList<>();
        for (Class item : mItems) if (!item.isBuiltIn()) types.add(item);
        return types;
    }

    public int indexOf(Class item) {
        return mItems.indexOf(item);
    }

    /** 是否包含「最近观看」项（任何情况下都位于第 0 位，与是否有站源无关）。 */
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
        // 「最近观看」不提供长按刷新；「首页」保留（回调里刷新首页数据）。
        holder.binding.text.setListener(item.isHome() ? null : () -> mListener.onRefresh(item));
        holder.binding.getRoot().setOnClickListener(v -> mListener.onItemClick(item));
    }

    private int getIcon(Class item) {
        if (item.isBuiltIn()) return 0;
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
