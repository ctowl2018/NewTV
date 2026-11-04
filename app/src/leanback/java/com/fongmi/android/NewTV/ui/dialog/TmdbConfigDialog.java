package com.fongmi.android.NewTV.ui.dialog;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.NewTV.R;
import com.fongmi.android.NewTV.databinding.DialogSingleInputBinding;
import com.fongmi.android.NewTV.impl.TmdbListener;
import com.fongmi.android.NewTV.setting.TmdbSetting;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** 编辑 TMDB 的 API 地址或 API 密钥，复用设置页的单行输入弹窗。 */
public class TmdbConfigDialog extends BaseAlertDialog {

    public static final int MODE_API = 0;
    public static final int MODE_KEY = 1;
    private static final String ARG_MODE = "tmdb_mode";

    private DialogSingleInputBinding binding;
    private int mode = MODE_API;

    public static void show(FragmentActivity activity, int mode) {
        TmdbConfigDialog dialog = new TmdbConfigDialog();
        Bundle args = new Bundle();
        args.putInt(ARG_MODE, mode);
        dialog.setArguments(args);
        dialog.show(activity.getSupportFragmentManager(), null);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) mode = args.getInt(ARG_MODE, MODE_API);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogSingleInputBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        boolean api = mode == MODE_API;
        String value = api ? TmdbSetting.getApiUrl() : TmdbSetting.getApiKey();
        binding.text.setHint(api ? R.string.tmdb_api : R.string.tmdb_key);
        binding.text.setText(value);
        binding.text.setSelection(TextUtils.isEmpty(value) ? 0 : value.length());
        // 手机输入入口与本项无关，隐藏后按钮排布更整齐。
        binding.phone.setVisibility(View.GONE);
    }

    @Override
    protected void initEvent() {
        binding.positive.setOnClickListener(this::onPositive);
        binding.negative.setOnClickListener(view -> dismiss());
        binding.text.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) binding.positive.performClick();
            return true;
        });
    }

    private void onPositive(View view) {
        String value = binding.text.getText().toString().trim();
        TmdbListener listener = (TmdbListener) requireActivity();
        if (mode == MODE_API) listener.setTmdbApi(value);
        else listener.setTmdbKey(value);
        dismiss();
    }

    @Override
    public void onStart() {
        super.onStart();
        setWidth(0.55f);
    }
}
