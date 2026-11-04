package com.fongmi.android.NewTV.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.format.Formatter;
import android.view.View;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.NewTV.R;
import com.fongmi.android.NewTV.api.TmdbApi;
import com.fongmi.android.NewTV.api.TmdbStore;
import com.fongmi.android.NewTV.databinding.ActivitySettingTmdbBinding;
import com.fongmi.android.NewTV.impl.TmdbListener;
import com.fongmi.android.NewTV.setting.Setting;
import com.fongmi.android.NewTV.setting.TmdbSetting;
import com.fongmi.android.NewTV.ui.base.BaseActivity;
import com.fongmi.android.NewTV.ui.dialog.TmdbConfigDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** 设置 → TMDB 配置：API 地址 / 密钥、抓取开关、缓存统计与清理。 */
public class SettingTmdbActivity extends BaseActivity implements TmdbListener {

    private ActivitySettingTmdbBinding mBinding;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SettingTmdbActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivitySettingTmdbBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mBinding.tmdbApi.requestFocus();
        updateText();
    }

    @Override
    protected void initEvent() {
        mBinding.tmdbApi.setOnClickListener(view -> TmdbConfigDialog.show(this, TmdbConfigDialog.MODE_API));
        mBinding.tmdbKey.setOnClickListener(view -> TmdbConfigDialog.show(this, TmdbConfigDialog.MODE_KEY));
        mBinding.tmdbEnable.setOnClickListener(this::toggleEnable);
        mBinding.tmdbFocus.setOnClickListener(this::toggleFocus);
        mBinding.tmdbClear.setOnClickListener(this::onClear);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 后台可能又抓到了新图，回到本页时重新统计。
        updateText();
    }

    private void toggleEnable(View view) {
        TmdbSetting.putEnabled(!TmdbSetting.isEnabled());
        updateText();
    }

    private void toggleFocus(View view) {
        TmdbSetting.putFocusFetch(!TmdbSetting.isFocusFetch());
        updateText();
    }

    private void onClear(View view) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.tmdb_clear)
                .setMessage(R.string.tmdb_clear_confirm)
                .setPositiveButton(R.string.dialog_positive, (dialog, which) -> {
                    TmdbApi.clearCache();
                    updateText();
                })
                .setNegativeButton(R.string.dialog_negative, null)
                .show();
    }

    private void updateText() {
        mBinding.tmdbApiText.setText(TmdbSetting.getApiUrl());
        mBinding.tmdbKeyText.setText(TmdbSetting.getApiKey());
        mBinding.tmdbEnableText.setText(Setting.getSwitch(TmdbSetting.isEnabled()));
        mBinding.tmdbFocusText.setText(Setting.getSwitch(TmdbSetting.isFocusFetch()));
        TmdbStore.Stats stats = TmdbStore.stats();
        mBinding.tmdbCacheText.setText(getString(R.string.tmdb_cache_info, stats.backdrops,
                Formatter.formatFileSize(this, stats.bytes)));
    }

    @Override
    public void setTmdbApi(String url) {
        TmdbSetting.putApiUrl(url);
        updateText();
    }

    @Override
    public void setTmdbKey(String key) {
        TmdbSetting.putApiKey(key);
        updateText();
    }
}
