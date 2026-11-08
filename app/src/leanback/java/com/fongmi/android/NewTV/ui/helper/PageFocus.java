package com.fongmi.android.NewTV.ui.helper;

/**
 * 「内容分页」的统一焦点契约。
 *
 * <p>分类页（{@code FolderFragment}）与「首页」页（{@code RecommendFragment}）
 * 都实现本接口，宿主 Activity 不必区分页面类型即可完成：</p>
 * <ul>
 *   <li>胶囊 ↓ → {@link #focusFirstPoster()}；</li>
 *   <li>横向切换 / 返回该页 → {@link #resetToFirstPoster()}（只复位选中项，不抢焦点）；</li>
 *   <li>长按刷新 → {@link #onRefresh()}。</li>
 * </ul>
 *
 * <p>它与 {@link ContentFocusHost} 是反方向：后者是内容区把「第一排 ↑」交给宿主，
 * 前者是宿主把「胶囊 ↓」交给内容区。两者合起来构成完整的胶囊 ↔ 海报焦点闭环。</p>
 */
public interface PageFocus {

    /** 焦点进入本页第一排（左上角第一个可聚焦项）。 */
    boolean focusFirstPoster();

    /** 选中项复位到左上角第一张，但不抢焦点（用于切页 / 返回时初始化位置）。 */
    void resetToFirstPoster();

    /** 刷新本页数据。 */
    void onRefresh();
}
