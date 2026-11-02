package com.fongmi.android.NewTV.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentTransaction;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.NewTV.R;
import com.fongmi.android.NewTV.bean.Cache;
import com.fongmi.android.NewTV.bean.Class;
import com.fongmi.android.NewTV.bean.Filter;
import com.fongmi.android.NewTV.databinding.FragmentFolderBinding;
import com.fongmi.android.NewTV.ui.activity.VodActivity;
import com.fongmi.android.NewTV.ui.base.BaseFragment;
import com.fongmi.android.NewTV.ui.helper.ContentFocusHost;

import java.util.HashMap;
import java.util.Optional;

public class FolderFragment extends BaseFragment {

    private FragmentFolderBinding mBinding;
    private Class mType;

    public static FolderFragment newInstance(String key, Class type) {
        Bundle args = new Bundle();
        args.putString("key", key);
        args.putParcelable("type", type);
        FolderFragment fragment = new FolderFragment();
        fragment.setArguments(args);
        return fragment;
    }

    private String getKey() {
        return getArguments().getString("key");
    }

    public Class getType() {
        return getArguments().getParcelable("type");
    }

    private TypeFragment getChild() {
        return (TypeFragment) getChildFragmentManager().findFragmentById(R.id.container);
    }

    /** 关闭所在页面的筛选条：只有分类页需要，首页内嵌分页时静默跳过。 */
    private void closeHostFilter() {
        if (getActivity() instanceof VodActivity) ((VodActivity) getActivity()).closeFilter();
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentFolderBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        mType = getType();
        getChildFragmentManager().beginTransaction().replace(R.id.container, TypeFragment.newInstance(getKey(), mType.getTypeId(), mType.getStyle(), getExtend(), mType.isFolder())).commit();
    }

    private HashMap<String, String> getExtend() {
        HashMap<String, String> extend = new HashMap<>();
        for (Filter filter : Cache.get(mType)) if (filter.getInit() != null) extend.put(filter.getKey(), filter.getInit());
        return extend;
    }

    public void openFolder(String typeId, HashMap<String, String> extend) {
        TypeFragment next = TypeFragment.newInstance(getKey(), typeId, mType.getStyle(), new HashMap<>(extend), mType.isFolder());
        FragmentTransaction ft = getChildFragmentManager().beginTransaction();
        closeHostFilter();
        Optional.ofNullable(getChild()).ifPresent(ft::hide);
        ft.add(R.id.container, next);
        ft.addToBackStack(null);
        ft.commit();
    }

    public void toggleFilter(boolean visible) {
        Optional.ofNullable(getChild()).ifPresent(f -> f.toggleFilter(visible));
    }

    /** 第一排海报按上键：交给宿主把焦点收回当前分类胶囊。 */
    public boolean onFirstRowUp() {
        if (getActivity() instanceof ContentFocusHost) return ((ContentFocusHost) getActivity()).onFirstRowUp();
        return false;
    }

    /** 胶囊按 ↓：进入当前分类第一张海报。 */
    public boolean focusFirstPoster() {
        TypeFragment child = getChild();
        if (child != null) return child.focusFirstPoster();
        // 子 Fragment 的 commit 尚未执行完：吃掉这次按键，下一帧再补一次焦点。
        if (getView() != null) getView().post(() -> Optional.ofNullable(getChild()).ifPresent(TypeFragment::focusFirstPoster));
        return true;
    }

    /** 横向切换分类：选中项复位到左上角第一张海报，但不抢焦点。 */
    public void resetToFirstPoster() {
        Optional.ofNullable(getChild()).ifPresent(TypeFragment::resetToFirstPoster);
    }

    public void onRefresh() {
        Optional.ofNullable(getChild()).ifPresent(TypeFragment::onRefresh);
    }

    public boolean moveToTop() {
        return Optional.ofNullable(getChild()).map(TypeFragment::moveToTop).orElse(false);
    }

    public boolean canBack() {
        return getChildFragmentManager().getBackStackEntryCount() > 0;
    }

    public void goBack() {
        getChildFragmentManager().popBackStack();
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (mBinding != null && !isVisibleToUser) Optional.ofNullable(getChild()).ifPresent(f -> f.setUserVisibleHint(false));
    }
}
