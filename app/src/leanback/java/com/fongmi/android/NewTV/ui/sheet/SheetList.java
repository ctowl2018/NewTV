package com.fongmi.android.NewTV.ui.sheet;

import android.content.Context;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.NewTV.databinding.ViewSheetItemBinding;
import com.fongmi.android.NewTV.databinding.ViewSheetListBinding;
import com.fongmi.android.NewTV.ui.custom.SpaceItemDecoration;
import com.fongmi.android.NewTV.utils.ResUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 浮层里的单列列表内容（选集 / 倍速共用）。
 *
 * <p><b>滚动交互与「弹幕搜索列表」一致</b>：普通 {@link RecyclerView} + {@link LinearLayoutManager}，
 * 高度由 {@link #setRows(int)} 通过 maxHeight 限制。因此
 * <ol>
 *   <li>焦点框在可视区内逐项移动；</li>
 *   <li>走到可视区上/下边缘后，列表按框架默认行为整列滚动，焦点框停在边缘那一行不动，
 *       到头即停、不回弹（{@code overScrollMode=never}，无 item 动画）；</li>
 *   <li>CH+ / CH-（PAGE_UP / PAGE_DOWN）按整屏翻页，翻完仍是同一套「焦点框 ↔ 列表」联动。</li>
 * </ol>
 *
 * <p><b>初始定位</b>：菜单自下往上弹出，最底下一行离按钮最近，所以打开时把当前选中项
 * （倍速是 1.0x、选集是正在播的那一集）停靠在可视区最底部；内容一屏放得下时不做滚动。
 */
public final class SheetList {

    public static final int ITEM_HEIGHT_DP = 44;
    public static final int ITEM_SPACING_DP = 2;

    public interface OnClickListener {

        void onItemClick(int position);
    }

    private final ViewSheetListBinding binding;
    private final Adapter adapter;
    private int selected;

    public SheetList(Context context) {
        binding = ViewSheetListBinding.inflate(LayoutInflater.from(context));
        binding.sheetList.setItemAnimator(null);
        binding.sheetList.addItemDecoration(new SpaceItemDecoration(1, ITEM_SPACING_DP));
        binding.sheetList.setAdapter(adapter = new Adapter());
    }

    public View getView() {
        return binding.getRoot();
    }

    public void submit(List<String> texts, int selected, int activated, OnClickListener listener) {
        adapter.submit(texts, activated, listener);
        this.selected = Math.max(0, Math.min(selected, Math.max(0, texts.size() - 1)));
    }

    /**
     * 浮层展开后调用：先把选中项停靠到最底部，再把焦点交给它。
     *
     * <p>停靠必须等列表完成布局（{@code getHeight()} 有效）才计算，所以放在这里而不是 {@link #submit}：
     * submit 发生在浮层显示之前，此时高度还是 0。
     */
    public void focus() {
        dockToBottom();
        binding.sheetList.post(this::focusSelected);
    }

    private void focusSelected() {
        LinearLayoutManager layoutManager = layoutManager();
        View child = layoutManager == null ? null : layoutManager.findViewByPosition(selected);
        if (child != null && child.requestFocus()) return;
        // 目标行还没挂上来：交给 CustomRecyclerView 的滚动+延迟聚焦
        binding.sheetList.scrollToPosition(selected);
    }

    /** 可视行数上限：超过就在浮层内滚动；0 或负数表示不限制。 */
    public void setRows(int rows) {
        int height = rows <= 0 ? 0 : rows * ResUtil.dp2px(ITEM_HEIGHT_DP) + Math.max(0, rows - 1) * ResUtil.dp2px(ITEM_SPACING_DP);
        binding.sheetList.setMaxHeight(height);
        binding.sheetList.requestLayout();
    }

    public int getItemCount() {
        return adapter.getItemCount();
    }

    /** 把选中项停靠到可视区最底部；一屏放得下就保持原样。 */
    private void dockToBottom() {
        LinearLayoutManager layoutManager = layoutManager();
        if (layoutManager == null) return;
        int count = adapter.getItemCount();
        if (count <= 0) return;
        int row = ResUtil.dp2px(ITEM_HEIGHT_DP);
        int content = count * row + Math.max(0, count - 1) * ResUtil.dp2px(ITEM_SPACING_DP);
        int height = binding.sheetList.getHeight();
        if (height <= 0 || content <= height) return;
        layoutManager.scrollToPositionWithOffset(selected, Math.max(0, height - row));
    }

    private LinearLayoutManager layoutManager() {
        RecyclerView.LayoutManager manager = binding.sheetList.getLayoutManager();
        return manager instanceof LinearLayoutManager linear ? linear : null;
    }

    /** 可视区能放下几行：翻页步长。 */
    private int visibleRows() {
        int row = ResUtil.dp2px(ITEM_HEIGHT_DP) + ResUtil.dp2px(ITEM_SPACING_DP);
        int height = binding.sheetList.getHeight();
        if (row <= 0 || height <= 0) return 1;
        return Math.max(1, (height + ResUtil.dp2px(ITEM_SPACING_DP)) / row);
    }

    /**
     * 条目按键入口。
     *
     * <p><b>边界收束</b>：首行再按上、末行再按时把按键吃掉，焦点停在本行，绝不外逸。
     * 否则方向键在浮层首/末行会把焦点带出弹层（如倍速弹窗往下翻到最后一行后按下会丢焦到控制条）。
     * 其余按键交给 {@link #onKey} 做 CH+/CH- 整屏翻页。
     */
    private boolean onItemKey(RecyclerView.ViewHolder holder, int keyCode, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
        int count = adapter.getItemCount();
        int position = holder.getBindingAdapterPosition();
        if (count <= 0 || position == RecyclerView.NO_POSITION) return false;
        if (position == 0 && keyCode == KeyEvent.KEYCODE_DPAD_UP) return true;
        if (position == count - 1 && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) return true;
        return onKey(keyCode, event);
    }

    /** CH+ / CH-（PAGE_UP / PAGE_DOWN）整屏翻页。 */
    private boolean onKey(int keyCode, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
        int count = adapter.getItemCount();
        if (count <= 0) return false;
        switch (keyCode) {
            case KeyEvent.KEYCODE_CHANNEL_UP:
            case KeyEvent.KEYCODE_PAGE_UP:
                return page(-visibleRows(), count);
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
            case KeyEvent.KEYCODE_PAGE_DOWN:
                return page(visibleRows(), count);
            default:
                return false;
        }
    }

    private boolean page(int delta, int count) {
        int target = Math.min(count - 1, Math.max(0, selected + delta));
        if (target != selected) {
            selected = target;
            // CustomRecyclerView 重写了 scrollToPosition：滚完会把焦点交给目标行
            binding.sheetList.scrollToPosition(target);
        }
        return true;
    }

    private final class Adapter extends RecyclerView.Adapter<Adapter.ViewHolder> {

        private final List<String> items = new ArrayList<>();
        private int activated = -1;
        private OnClickListener listener;

        void submit(List<String> texts, int activated, OnClickListener listener) {
            items.clear();
            items.addAll(texts);
            this.activated = activated;
            this.listener = listener;
            notifyDataSetChanged();
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ViewHolder holder = new ViewHolder(ViewSheetItemBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
            // 按键落在被聚焦的条目上，所以监听装在条目文字上（挂在列表容器上收不到）
            holder.binding.sheetItemText.setOnKeyListener((view, keyCode, event) -> onItemKey(holder, keyCode, event));
            return holder;
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.binding.sheetItemText.setActivated(position == activated);
            holder.binding.sheetItemText.setText(items.get(position));
            holder.binding.sheetItemText.setOnClickListener(view -> {
                if (listener != null) listener.onItemClick(holder.getBindingAdapterPosition());
            });
        }

        static final class ViewHolder extends RecyclerView.ViewHolder {

            private final ViewSheetItemBinding binding;

            ViewHolder(ViewSheetItemBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
