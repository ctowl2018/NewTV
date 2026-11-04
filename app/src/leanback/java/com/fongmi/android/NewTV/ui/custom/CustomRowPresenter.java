package com.fongmi.android.NewTV.ui.custom;

import android.annotation.SuppressLint;
import android.view.ViewGroup;

import androidx.leanback.widget.FocusHighlight;
import androidx.leanback.widget.HorizontalGridView;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.RowPresenter;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.NewTV.utils.ResUtil;

import java.util.ArrayList;

public class CustomRowPresenter extends ListRowPresenter {

    private final int spacing;
    private final int strategy;
    /** > 0 时把行内网格固定成这个宽度（px）：只收窄右边，左边起点不变。 */
    private int rowWidth;
    private RecyclerView.RecycledViewPool recycledViewPool;
    private ArrayList<Presenter> presenterMapper;

    public CustomRowPresenter(int spacing) {
        this(spacing, FocusHighlight.ZOOM_FACTOR_SMALL);
    }

    @SuppressLint("RestrictedApi")
    public CustomRowPresenter(int spacing, int focusZoomFactor) {
        this(spacing, focusZoomFactor, HorizontalGridView.FOCUS_SCROLL_ITEM);
    }

    public CustomRowPresenter(int spacing, int focusZoomFactor, int strategy) {
        super(focusZoomFactor);
        this.spacing = spacing;
        this.strategy = strategy;
        setShadowEnabled(false);
        setSelectEffectEnabled(false);
        setKeepChildForeground(false);
    }

    /**
     * 固定行内容宽度（px）。用于首页「最近观看」：宽度按 4.5 个海报算，只收右边界，
     * 行起点仍贴着列表的左内边距，其它行的宽度与位置一概不受影响。
     */
    public CustomRowPresenter setRowWidth(int rowWidth) {
        this.rowWidth = rowWidth;
        return this;
    }

    @Override
    @SuppressLint("RestrictedApi")
    protected void initializeRowViewHolder(RowPresenter.ViewHolder holder) {
        super.initializeRowViewHolder(holder);
        ViewHolder vh = (ViewHolder) holder;
        HorizontalGridView grid = vh.getGridView();
        grid.setFocusScrollStrategy(strategy);
        grid.setHorizontalSpacing(ResUtil.dp2px(spacing));
        if (rowWidth > 0) {
            ViewGroup.LayoutParams params = grid.getLayoutParams();
            if (params != null) {
                params.width = rowWidth;
                grid.setLayoutParams(params);
            }
        }
        if (recycledViewPool == null) recycledViewPool = grid.getRecycledViewPool();
        else grid.setRecycledViewPool(recycledViewPool);
        ItemBridgeAdapter adapter = vh.getBridgeAdapter();
        if (presenterMapper == null) presenterMapper = adapter.getPresenterMapper();
        else adapter.setPresenterMapper(presenterMapper);
    }
}
