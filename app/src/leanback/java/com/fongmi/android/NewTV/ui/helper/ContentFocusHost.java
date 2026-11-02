package com.fongmi.android.NewTV.ui.helper;

/**
 * 内容区（{@code FolderFragment}/{@code TypeFragment}）与宿主 Activity 之间的焦点桥。
 *
 * <p>首页与分类页共用同一套内容区实现，第一排海报按上键时要由宿主决定
 * 焦点回到哪一枚分类胶囊，因此在这里做统一抽象。
 */
public interface ContentFocusHost {

    /** 第一排海报按上键：把焦点收回当前分类胶囊。 */
    boolean onFirstRowUp();
}
