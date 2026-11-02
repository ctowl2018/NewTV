package com.fongmi.android.NewTV.ui.custom;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.textview.MaterialTextView;

/**
 * 类型导航胶囊。
 *
 * <p>早期这里拦截了上键去「刷新当前分类」，导致焦点无法离开导航栏向上走；
 * 现在上键交由宿主的按键拦截处理（统一把焦点送到搜索按钮），
 * 本类只保留一个空的回调接口以兼容既有绑定代码。
 */
public class CustomTypeView extends MaterialTextView {

    private Listener listener;

    public CustomTypeView(@NonNull Context context) {
        super(context);
    }

    public CustomTypeView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public Listener getListener() {
        return listener;
    }

    public interface Listener {

        void onRefresh();
    }
}
