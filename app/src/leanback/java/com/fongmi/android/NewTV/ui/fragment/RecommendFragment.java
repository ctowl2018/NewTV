package com.fongmi.android.NewTV.ui.fragment;

import com.fongmi.android.NewTV.ui.adapter.TypeAdapter;

/**
 * 「首页」独立页（应用启动默认页）。
 *
 * <p>从原首页里拆出来的第二个导航页。它<b>完整继承分类页框架</b>：
 * {@link FolderFragment} 的容器 / 子 Fragment 栈 / 二级文件夹返回，以及
 * {@link TypeFragment} 的网格排版、进度与空态、上下行焦点、选中项复位都直接复用，
 * 只把数据源从 {@code categoryContent} 换成 {@code homeContent}。</p>
 *
 * <p>实现上不新建一套布局：构造参数里放一枚 typeId={@code recommend} 的
 * {@link com.fongmi.android.NewTV.bean.Class}，TypeFragment 据此进入 recommend 模式，
 * 同时在页面顶部保留「首页」标题行。由于继承自 FolderFragment，
 * 它自动满足宿主使用的 {@link com.fongmi.android.NewTV.ui.helper.PageFocus} 契约。</p>
 */
public class RecommendFragment extends FolderFragment {

    public static RecommendFragment newInstance(String key) {
        RecommendFragment fragment = new RecommendFragment();
        fragment.setArguments(args(key, TypeAdapter.recommend()));
        return fragment;
    }
}
