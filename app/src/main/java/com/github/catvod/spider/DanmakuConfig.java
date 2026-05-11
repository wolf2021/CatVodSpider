package com.github.catvod.spider;

import org.json.JSONObject;
import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 弹幕配置实体类
 */
public class DanmakuConfig {
    /**
     * 弹幕API地址
     */
    public Set<String> apiUrls;
    /**
     * 可维护的弹幕API源列表，保留启停、别名和最近测试状态。
     */
    public List<DanmakuApiSource> apiSources;
    /**
     * 弹幕搜索框宽度比例
     */
    public float lpWidth;
    /**
     * 弹幕搜索框高度比例
     */
    public float lpHeight;
    /**
     * 弹幕搜索框透明度
     */
    public float lpAlpha;
    /**
     * 自动推送弹幕开关
     */
    public boolean autoPushEnabled;
    /**
     * 弹幕搜索框样式
     */
    public String danmakuStyle;
    /**
     * 静默模式，开启时不再弹出任何提示信息
     */
    public boolean silentMode;
    /**
     * 弹幕时间偏移，单位毫秒。正数延后，负数提前。
     */
    public int danmakuTimeOffsetMs;

    public DanmakuConfig() {
        // 设置默认值
        apiUrls = new LinkedHashSet<>();
        apiSources = new ArrayList<>();
        lpWidth = 0.9f;
        lpHeight = 0.85f;
        lpAlpha = 0.9f;
        autoPushEnabled = false;
        danmakuStyle = "模板一";
        silentMode = true;
        danmakuTimeOffsetMs = 0;
    }

    public void updateFromJson(JSONObject json) {
        if (json == null) return;
        if (json.has("apiUrl")) {
            addApiUrls(parseJsonApiEntries(json.opt("apiUrl")));
        }
        if (json.has("apiUrls")) {
            addApiUrls(parseJsonApiEntries(json.opt("apiUrls")));
        }
        if (json.has("lpWidth")) {
            setLpWidth((float) json.optDouble("lpWidth", lpWidth));
        }
        if (json.has("lpHeight")) {
            setLpHeight((float) json.optDouble("lpHeight", lpHeight));
        }
        if (json.has("lpAlpha")) {
            setLpAlpha((float) json.optDouble("lpAlpha", lpAlpha));
        }
        if (json.has("autoPushEnabled")) {
            setAutoPushEnabled(json.optBoolean("autoPushEnabled", autoPushEnabled));
        }
        if (json.has("danmakuStyle")) {
            setDanmakuStyle(json.optString("danmakuStyle", danmakuStyle));
        }
        if (json.has("silentMode")) {
            setSilentMode(json.optBoolean("silentMode", silentMode));
        }
        if (json.has("danmakuTimeOffsetMs")) {
            setDanmakuTimeOffsetMs(json.optInt("danmakuTimeOffsetMs", danmakuTimeOffsetMs));
        } else if (json.has("danmakuOffsetMs")) {
            setDanmakuTimeOffsetMs(json.optInt("danmakuOffsetMs", danmakuTimeOffsetMs));
        } else if (json.has("danmakuOffset")) {
            setDanmakuTimeOffsetMs((int) Math.round(json.optDouble("danmakuOffset", danmakuTimeOffsetMs / 1000.0) * 1000));
        }
    }

    public Set<String> getApiUrls() {
        normalize();
        return apiUrls;
    }

    public List<String> getApiUrlEntries() {
        normalize();
        List<String> entries = new ArrayList<>();
        for (DanmakuApiSource source : apiSources) {
            if (source != null) entries.add(source.toConfigEntry());
        }
        return entries;
    }

    public void setApiUrls(Set<String> apiUrls) {
        setApiUrls((Collection<String>) apiUrls);
    }

    public void setApiUrls(Collection<String> urls) {
        normalize();
        List<DanmakuApiSource> oldSources = new ArrayList<>(apiSources);
        apiSources.clear();
        apiUrls.clear();
        addApiUrls(urls, oldSources);
        normalize();
    }

    public void addApiUrls(Collection<String> urls) {
        normalize();
        addApiUrls(urls, new ArrayList<>(apiSources));
        normalize();
    }

    private void addApiUrls(Collection<String> urls, List<DanmakuApiSource> oldSources) {
        if (urls == null) return;
        for (String rawEntry : urls) {
            DanmakuApiSource.ParsedEntry entry = DanmakuApiSource.parseEntry(rawEntry);
            String url = entry.url;
            if (!isValidApiUrl(url) || apiUrls.contains(url)) continue;

            DanmakuApiSource oldSource = findSource(oldSources, url);
            DanmakuApiSource source = oldSource != null ? oldSource : new DanmakuApiSource(url, true);
            source.url = url;
            if (entry.hasName || source.name == null) {
                source.name = entry.name;
            }
            apiSources.add(source);
            apiUrls.add(url);
        }
    }

    public List<DanmakuApiSource> getApiSources() {
        normalize();
        return apiSources;
    }

    public List<DanmakuApiSource> getEnabledApiSources() {
        normalize();
        List<DanmakuApiSource> result = new ArrayList<>();
        for (DanmakuApiSource source : apiSources) {
            if (source != null && source.isUsable()) result.add(source);
        }
        return result;
    }

    public Set<String> getEnabledApiUrls() {
        Set<String> urls = new LinkedHashSet<>();
        for (DanmakuApiSource source : getEnabledApiSources()) {
            urls.add(source.url);
        }
        return urls;
    }

    public DanmakuApiSource findApiSource(String rawUrl) {
        normalize();
        return findSource(apiSources, DanmakuApiSource.parseEntry(rawUrl).url);
    }

    public void setApiSourceName(String rawUrl, String name) {
        DanmakuApiSource source = findApiSource(rawUrl);
        if (source != null) source.name = DanmakuApiSource.normalizeName(name);
    }

    public void setApiSourceEnabled(String rawUrl, boolean enabled) {
        DanmakuApiSource source = findApiSource(rawUrl);
        if (source != null) source.enabled = enabled;
    }

    public void removeApiSource(String rawUrl) {
        normalize();
        String url = DanmakuApiSource.parseEntry(rawUrl).url;
        for (int i = apiSources.size() - 1; i >= 0; i--) {
            DanmakuApiSource source = apiSources.get(i);
            if (source != null && url.equals(source.url)) {
                apiSources.remove(i);
                break;
            }
        }
        normalize();
    }

    public void recordApiSourceResult(String rawUrl, boolean success, long latencyMs, String error) {
        DanmakuApiSource source = findApiSource(rawUrl);
        if (source == null) return;
        if (success) source.markSuccess(latencyMs);
        else source.markFailure(latencyMs, error);
    }

    public void normalize() {
        if (apiUrls == null) apiUrls = new LinkedHashSet<>();
        if (apiSources == null) apiSources = new ArrayList<>();
        if (danmakuStyle == null) danmakuStyle = "模板一";

        List<DanmakuApiSource> oldSources = new ArrayList<>(apiSources);
        if (apiSources.isEmpty() && !apiUrls.isEmpty()) {
            Set<String> legacyUrls = new LinkedHashSet<>(apiUrls);
            apiSources = new ArrayList<>();
            apiUrls.clear();
            addApiUrls(legacyUrls, oldSources);
        }

        List<DanmakuApiSource> validSources = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (DanmakuApiSource source : apiSources) {
            if (source == null) continue;
            DanmakuApiSource.ParsedEntry entry = DanmakuApiSource.parseEntry(source.url);
            source.url = entry.url;
            if (entry.hasName) source.name = entry.name;
            source.name = DanmakuApiSource.normalizeName(source.name);
            if (!isValidApiUrl(source.url) || seen.contains(source.url)) continue;
            if (source.lastLatencyMs == 0 && source.lastTestTimeMs == 0 && source.lastSuccessTimeMs == 0) {
                source.lastLatencyMs = -1;
            }
            if (source.lastError == null) source.lastError = "";
            validSources.add(source);
            seen.add(source.url);
        }

        apiSources = validSources;
        rebuildApiUrlIndex();
    }

    private void rebuildApiUrlIndex() {
        apiUrls.clear();
        for (int i = 0; i < apiSources.size(); i++) {
            DanmakuApiSource source = apiSources.get(i);
            apiUrls.add(source.url);
        }
    }

    private static boolean isValidApiUrl(String url) {
        return url != null && (url.startsWith("http://") || url.startsWith("https://"));
    }

    private static DanmakuApiSource findSource(List<DanmakuApiSource> sources, String url) {
        if (sources == null || url == null) return null;
        for (DanmakuApiSource source : sources) {
            if (source != null && url.equals(source.url)) return source;
        }
        return null;
    }

    private static List<String> parseJsonApiEntries(Object value) {
        List<String> entries = new ArrayList<>();
        if (value == null) return entries;

        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                String entry = array.optString(i);
                if (entry != null) entries.add(entry);
            }
            return entries;
        }

        String text = String.valueOf(value);
        if (text == null) return entries;
        String[] parts = text.split(",");
        for (String part : parts) {
            if (part != null) entries.add(part.trim());
        }
        return entries;
    }

    public float getLpWidth() {
        return lpWidth;
    }

    public void setLpWidth(float lpWidth) {
        this.lpWidth = lpWidth;
    }

    public float getLpHeight() {
        return lpHeight;
    }

    public void setLpHeight(float lpHeight) {
        this.lpHeight = lpHeight;
    }

    public float getLpAlpha() {
        return lpAlpha;
    }

    public void setLpAlpha(float lpAlpha) {
        this.lpAlpha = lpAlpha;
    }

    public boolean isAutoPushEnabled() {
        return autoPushEnabled;
    }

    public void setAutoPushEnabled(boolean autoPushEnabled) {
        this.autoPushEnabled = autoPushEnabled;
    }

    public String getDanmakuStyle() {
        return danmakuStyle;
    }

    public void setDanmakuStyle(String danmakuStyle) {
        this.danmakuStyle = danmakuStyle;
    }

    public boolean isSilentMode() {
        return silentMode;
    }

    public void setSilentMode(boolean silentMode) {
        this.silentMode = silentMode;
    }

    public int getDanmakuTimeOffsetMs() {
        if (danmakuTimeOffsetMs > 600000) return 600000;
        if (danmakuTimeOffsetMs < -600000) return -600000;
        return danmakuTimeOffsetMs;
    }

    public void setDanmakuTimeOffsetMs(int danmakuTimeOffsetMs) {
        if (danmakuTimeOffsetMs > 600000) danmakuTimeOffsetMs = 600000;
        if (danmakuTimeOffsetMs < -600000) danmakuTimeOffsetMs = -600000;
        this.danmakuTimeOffsetMs = danmakuTimeOffsetMs;
    }
}
