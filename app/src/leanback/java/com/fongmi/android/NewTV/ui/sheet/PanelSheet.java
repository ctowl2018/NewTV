package com.fongmi.android.NewTV.ui.sheet;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import com.fongmi.android.NewTV.databinding.ViewControlVodActionBinding;
import com.fongmi.android.NewTV.databinding.ViewSheetPanelBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * 「播放设置」浮层的内容。
 *
 * <p>面板本身不实现播放逻辑：每个入口只转发到控制条 hidden 容器里同名按钮的
 * {@code performClick()}，对话框、状态同步、错误提示全部沿用原有实现。
 *
 * <p>入口的可见性跟随同名按钮（无版本 / 无章节 / 无碟片菜单时不出现），
 * 且每次打开都重串焦点链，避免焦点落到已隐藏的入口上。
 */
public final class PanelSheet {

    public interface OnAction {

        void onAction();
    }

    private final Activity activity;
    private final ViewControlVodActionBinding action;
    private final ViewSheetPanelBinding binding;

    public PanelSheet(Activity activity, ViewControlVodActionBinding action) {
        this.activity = activity;
        this.action = action;
        this.binding = ViewSheetPanelBinding.inflate(activity.getLayoutInflater());
    }

    public View getView() {
        return binding.getRoot();
    }

    /** 内容自适应宽度（含浮层左右 8dp 内边距由 VideoSheet 负责，这里只算内容本身）。 */
    public int measureWidth(int maxWidth) {
        View view = binding.getRoot();
        view.measure(View.MeasureSpec.makeMeasureSpec(maxWidth, View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        return view.getMeasuredWidth();
    }

    public void bind(OnAction onAction) {
        syncLabels();
        syncVisible(binding.panelDecoder, action.decoder);
        syncVisible(binding.panelScale, action.scale);
        syncVisible(binding.panelVideo, action.video);
        syncVisible(binding.panelEdition, action.edition);
        syncVisible(binding.panelChapter, action.chapter);
        syncVisible(binding.panelDiscMenu, action.discMenu);
        syncVisible(binding.panelParse, action.parse);
        relink();
        // 画面比例：不关面板，原地循环切换，便于连续对比
        binding.panelScale.setOnClickListener(view -> {
            if (action.scale != null) action.scale.performClick();
            syncLabels();
        });
        binding.panelDecoder.setOnClickListener(view -> forward(action.decoder, onAction));
        binding.panelVideo.setOnClickListener(view -> forward(action.video, onAction));
        binding.panelEdition.setOnClickListener(view -> forward(action.edition, onAction));
        binding.panelChapter.setOnClickListener(view -> forward(action.chapter, onAction));
        binding.panelDiscMenu.setOnClickListener(view -> forward(action.discMenu, onAction));
        binding.panelParse.setOnClickListener(view -> forward(action.parse, onAction));
    }

    /** 其余入口先收面板再触发，否则浮层会盖在随后弹出的对话框上面。 */
    private void forward(View target, OnAction onAction) {
        if (onAction != null) onAction.onAction();
        if (target != null) target.performClick();
    }

    private void syncLabels() {
        if (action.scale == null) return;
        // 只显示画面比例的状态值，不再带「画面比例 ·」前缀
        binding.panelScale.setText(action.scale.getText());
    }

    private void syncVisible(View panelView, View source) {
        panelView.setVisibility(source != null && source.getVisibility() == View.VISIBLE ? View.VISIBLE : View.GONE);
    }

    private void relink() {
        List<View> rows = visibleChildren(binding.sheetRows);
        for (int i = 0; i < rows.size(); i++) {
            View view = rows.get(i);
            view.setNextFocusLeftId(view.getId());
            view.setNextFocusRightId(view.getId());
            view.setNextFocusUpId(i == 0 ? view.getId() : rows.get(i - 1).getId());
            view.setNextFocusDownId(i == rows.size() - 1 ? view.getId() : rows.get(i + 1).getId());
        }
        wireScripts(rows);
    }

    /**
     * 脚本按钮行（panelScripts）仅在 MPV / EXO 引擎下出现，且内容在挂载后才填充。
     * 把它接在单列列表最后一项之下：末行 ↓ → 脚本首按钮，脚本末按钮 ↓ 封口，
     * 保证整块浮层都能走位到，不会在某个入口断掉。
     * 脚本按钮自身的左/右边界由 {@link PlaybackButtonsView#updateFocusNeighbors()} 负责。
     */
    private void wireScripts(List<View> rows) {
        if (binding.panelScripts.getVisibility() != View.VISIBLE || binding.panelScripts.getChildCount() == 0) return;
        // 排内左右闭环：脚本按钮之间左右可切换，首尾封口，不会把焦点带回控制条
        binding.panelScripts.trapHorizontalFocus();
        View first = binding.panelScripts.getChildAt(0);
        View last = binding.panelScripts.getChildAt(binding.panelScripts.getChildCount() - 1);
        View lastRow = rows.isEmpty() ? null : rows.get(rows.size() - 1);
        if (lastRow == null) {
            first.setNextFocusUpId(first.getId());
            last.setNextFocusUpId(last.getId());
        } else {
            lastRow.setNextFocusDownId(first.getId());
            first.setNextFocusUpId(lastRow.getId());
            last.setNextFocusUpId(lastRow.getId());
        }
        last.setNextFocusDownId(last.getId());
    }

    /** 浮层打开后把焦点交给第一个可见入口（左上角），避免焦点留在被锁定的控制条上、导致控件拿不到焦点。 */
    public void focus() {
        relink();
        View first = firstVisible(binding.sheetRows);
        if (first == null && binding.panelScripts.getVisibility() == View.VISIBLE && binding.panelScripts.getChildCount() > 0) {
            first = binding.panelScripts.getChildAt(0);
        }
        if (first != null) first.requestFocus();
    }

    private View firstVisible(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child.getVisibility() == View.VISIBLE) return child;
        }
        return null;
    }

    private List<View> visibleChildren(ViewGroup group) {
        List<View> views = new ArrayList<>();
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child.getVisibility() == View.VISIBLE) views.add(child);
        }
        return views;
    }
}
