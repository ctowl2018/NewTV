package com.fongmi.android.NewTV.api.config;

import android.text.TextUtils;

import com.fongmi.android.NewTV.App;
import com.fongmi.android.NewTV.api.loader.BaseLoader;
import com.fongmi.android.NewTV.api.node.NodeRuntime;
import com.fongmi.android.NewTV.bean.Config;
import com.fongmi.android.NewTV.bean.Depot;
import com.fongmi.android.NewTV.bean.Parse;
import com.fongmi.android.NewTV.bean.Rule;
import com.fongmi.android.NewTV.bean.Site;
import com.fongmi.android.NewTV.event.ConfigEvent;
import com.fongmi.android.NewTV.event.RefreshEvent;
import com.fongmi.android.NewTV.impl.Callback;
import com.fongmi.android.NewTV.utils.UrlUtil;
import com.github.catvod.bean.Doh;
import com.github.catvod.bean.Header;
import com.github.catvod.bean.Proxy;
import com.github.catvod.runtime.NodeRuntimeApi;
import com.github.catvod.utils.Decoder;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Prefers;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class VodConfig extends BaseConfig {

    private static final String TAG = VodConfig.class.getSimpleName();

    /** 不再内置任何默认点播源；留空时全新安装需用户在设置页自行添加。 */
    private static final String TEMP_VOD_URL = "";
    private static final String TEMP_VOD_NAME = "";

    private Site home;
    private Parse parse;
    private List<Doh> doh;
    private List<Rule> rules;
    private List<Site> sites;
    private List<String> ads;
    private List<String> flags;
    private List<Parse> parses;

    public static VodConfig get() {
        return Loader.INSTANCE;
    }

    public static int getCid() {
        return get().getConfig().getId();
    }

    public static String getUrl() {
        return get().getConfig().getUrl();
    }

    public static String getDesc() {
        return get().getConfig().getDesc();
    }

    public static int getHomeIndex() {
        return get().getSites().indexOf(get().getHome());
    }

    public static boolean hasParse() {
        return !get().getParses().isEmpty();
    }

    public static void load(Config config, Callback callback) {
        get().clear().config(config).load(callback);
    }

    public VodConfig init() {
        return config(defaultVod());
    }

    /**
     * 当前点播配置。项目不再内置默认点播源，配置表为空时直接返回空配置，
     * 由用户在设置页添加；{@link #TEMP_VOD_URL} 留空时不写入任何兜底记录。
     */
    private static Config defaultVod() {
        Config config = Config.vod();
        if (!config.isEmpty()) return config;
        if (TEMP_VOD_URL.isEmpty()) return config;
        // 只自动写入一次：用户在界面上删掉后不再自动恢复。
        if (Prefers.getBoolean("temp_vod_seeded")) return config;
        Prefers.put("temp_vod_seeded", true);
        return Config.create(VOD, TEMP_VOD_URL, TEMP_VOD_NAME);
    }

    public VodConfig config(Config config) {
        this.config = config;
        return this;
    }

    public VodConfig clear() {
        ads = null;
        doh = null;
        home = null;
        parse = null;
        sites = null;
        flags = null;
        rules = null;
        parses = null;
        BaseLoader.get().clear();
        RuleConfig.get().invalidate();
        return this;
    }

    @Override
    protected String getTag() {
        return TAG;
    }

    @Override
    protected Config defaultConfig() {
        return defaultVod();
    }

    @Override
    protected void cancelLoad() {
        NodeRuntime.get().cancelLoad();
    }

    @Override
    protected void postEvent() {
        super.postEvent();
        ConfigEvent.vod();
    }

    @Override
    protected void load(Config config) throws Throwable {
        boolean node = NodeRuntime.isConfig(config.getUrl());
        NodeRuntime runtime = NodeRuntime.get();
        if (!node) runtime.clear();
        NodeRuntimeApi.LoadedConfig loaded = null;
        try {
            if (node) loaded = runtime.loadConfig(config.getUrl());
            String json = node ? loaded.json() : Decoder.getJson(UrlUtil.convert(config.getUrl()), TAG);
            runtime.prune(loaded, Config.getAll(VOD).stream().map(Config::getUrl).toList(), config.getUrl());
            checkJson(config, Json.parse(json).getAsJsonObject(), loaded);
        } catch (Throwable e) {
            if (node) runtime.fail(loaded);
            throw e;
        }
    }

    @Override
    protected boolean isLoaded() {
        return !getSites().isEmpty();
    }

    private void checkJson(Config config, JsonObject object, NodeRuntimeApi.LoadedConfig loaded) throws Throwable {
        if (object.has("msg")) throw new Exception(object.get("msg").getAsString());
        if (object.has("urls")) {
            List<Depot> items = Depot.arrayFrom(object.getAsJsonArray("urls").toString());
            if (items.isEmpty()) throw new Exception("Depot urls is empty");
            if (loaded != null) NodeRuntime.get().accept(loaded);
            parseDepot(config, items);
            return;
        }
        parseConfig(config, object);
        if (loaded != null) NodeRuntime.get().accept(loaded);
    }

    private void parseDepot(Config config, List<Depot> items) throws Throwable {
        List<Config> configs = new ArrayList<>();
        for (Depot item : items) configs.add(Config.find(item, VOD));
        load(this.config = configs.get(0));
        Config.delete(config.getUrl());
    }

    private void parseConfig(Config config, JsonObject object) {
        initList(object);
        initSite(config, object);
        initParse(config, object);
        config.setLogo(Json.safeString(object, "logo"));
        config.setAssrt(Json.safeString(object, "assrt"));
        config.setNotice(Json.safeString(object, "notice"));
        config.setDanmaku(Json.safeString(object, "danmaku"));
    }

    private void initList(JsonObject object) {
        setHeaders(Header.arrayFrom(fetchArray(object, "headers")));
        setProxy(Proxy.arrayFrom(fetchArray(object, "proxy")));
        setRules(Rule.arrayFrom(fetchArray(object, "rules")));
        setDoh(Doh.arrayFrom(fetchArray(object, "doh")));
        setFlags(Json.safeListString(object, "flags"));
        setHosts(Json.safeListString(object, "hosts"));
        setAds(Json.safeListString(object, "ads"));
    }

    private void initSite(Config config, JsonObject object) {
        String spider = Json.safeString(object, "spider");
        BaseLoader.get().parseJar(spider, true);
        setSites(Json.safeListElement(object, "sites").stream().map(e -> Site.objectFrom(e, spider)).distinct().collect(Collectors.toCollection(ArrayList::new)));
        Map<String, Site> items = Site.findAll().stream().collect(Collectors.toMap(Site::getKey, Function.identity()));
        getSites().forEach(site -> site.sync(items.get(site.getKey())));
        setHome(config, getSites().isEmpty() ? new Site() : getSites().stream().filter(item -> item.getKey().equals(config.getHome())).findFirst().orElse(getSites().get(0)), false);
    }

    private void initParse(Config config, JsonObject object) {
        setParses(Json.safeListElement(object, "parses").stream().map(Parse::objectFrom).distinct().collect(Collectors.toCollection(ArrayList::new)));
        setParse(config, getParses().isEmpty() ? new Parse() : getParses().stream().filter(item -> item.getName().equals(config.getParse())).findFirst().orElse(getParses().get(0)), false);
    }

    public List<Site> getSites() {
        return sites == null ? Collections.emptyList() : sites;
    }

    private void setSites(List<Site> sites) {
        this.sites = sites;
    }

    public List<Parse> getParses() {
        return parses == null ? Collections.emptyList() : parses;
    }

    private void setParses(List<Parse> parses) {
        if (!parses.isEmpty()) parses.add(0, Parse.god());
        this.parses = parses;
    }

    public List<Doh> getDoh() {
        List<Doh> items = Doh.get(App.get());
        if (doh == null) return items;
        items.removeAll(doh);
        items.addAll(doh);
        return items;
    }

    private void setDoh(List<Doh> doh) {
        this.doh = doh;
    }

    public List<Rule> getRules() {
        return rules == null ? Collections.emptyList() : rules;
    }

    private void setRules(List<Rule> rules) {
        this.rules = rules;
        RuleConfig.get().invalidate();
    }

    public List<Parse> getParses(int type) {
        return getParses().stream().filter(item -> item.getType() == type).toList();
    }

    public List<Parse> getParses(int type, String flag) {
        List<Parse> items = getParses(type);
        List<Parse> filter = items.stream().filter(item -> item.getExt().getFlag().contains(flag)).toList();
        return filter.isEmpty() ? items : filter;
    }

    public List<String> getFlags() {
        return flags == null ? Collections.emptyList() : flags;
    }

    private void setFlags(List<String> flags) {
        this.flags = flags;
    }

    public List<String> getAds() {
        return ads == null ? Collections.emptyList() : ads;
    }

    private void setAds(List<String> ads) {
        this.ads = ads;
        RuleConfig.get().invalidate();
    }

    public Parse getParse() {
        return parse == null ? new Parse() : parse;
    }

    public void setParse(Parse parse) {
        setParse(getConfig(), parse, true);
    }

    public Site getHome() {
        return home == null ? new Site() : home;
    }

    public void setHome(Site site) {
        setHome(getConfig(), site, true);
        RefreshEvent.home();
    }

    public Parse getParse(String name) {
        return getParses().stream().filter(item -> item.getName().equals(name)).findFirst().orElse(new Parse());
    }

    public Site getSite(String key) {
        return getSites().stream().filter(item -> item.getKey().equals(key)).findFirst().orElse(new Site());
    }

    private void setParse(Config config, Parse parse, boolean save) {
        this.parse = parse;
        this.parse.setSelected(true);
        config.setParse(parse.getName());
        getParses().forEach(item -> item.setSelected(parse));
        if (save) config.save();
    }

    private void setHome(Config config, Site site, boolean save) {
        home = site;
        home.setSelected(true);
        config.setHome(home.getKey());
        if (save) config.save();
        getSites().forEach(item -> item.setSelected(home));
    }

    private static class Loader {
        static volatile VodConfig INSTANCE = new VodConfig();
    }
}
