package com.github.catvod.spider;

import android.text.TextUtils;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class DanmakuXmlCache {

    private static final long TTL_MS = 10 * 60 * 1000L;
    private static final int MAX_ENTRIES = 3;
    private static final int MAX_BODY_CHARS = 6 * 1024 * 1024;
    private static final int MAX_TOTAL_CHARS = 12 * 1024 * 1024;
    private static final Map<String, Entry> rawCache = new LinkedHashMap<String, Entry>(MAX_ENTRIES, 0.75f, true);
    private static final Map<String, Entry> offsetCache = new LinkedHashMap<String, Entry>(MAX_ENTRIES, 0.75f, true);

    public static String getRaw(String url) {
        return get(rawCache, normalizeRawKey(url));
    }

    public static void putRaw(String url, String body) {
        put(rawCache, normalizeRawKey(url), body);
    }

    public static String getOffset(String url, int offsetMs) {
        return get(offsetCache, normalizeOffsetKey(url, offsetMs));
    }

    public static void putOffset(String url, int offsetMs, String body) {
        put(offsetCache, normalizeOffsetKey(url, offsetMs), body);
    }

    public static String fetchRaw(String url, int timeoutMs, int maxRetries, int retryDelayMs) {
        String cached = getRaw(url);
        if (!TextUtils.isEmpty(cached)) return cached;
        String body = NetworkUtils.robustHttpGet(url, timeoutMs, maxRetries, retryDelayMs);
        putRaw(url, body);
        return body;
    }

    public static void clear() {
        synchronized (DanmakuXmlCache.class) {
            rawCache.clear();
            offsetCache.clear();
        }
    }

    private static String get(Map<String, Entry> cache, String key) {
        if (TextUtils.isEmpty(key)) return "";
        synchronized (DanmakuXmlCache.class) {
            Entry entry = cache.get(key);
            if (entry == null) return "";
            if (System.currentTimeMillis() - entry.createdAtMs > TTL_MS) {
                cache.remove(key);
                return "";
            }
            return entry.body;
        }
    }

    private static void put(Map<String, Entry> cache, String key, String body) {
        if (TextUtils.isEmpty(key) || TextUtils.isEmpty(body) || body.length() > MAX_BODY_CHARS) return;
        synchronized (DanmakuXmlCache.class) {
            cache.put(key, new Entry(body));
            trim(cache);
        }
    }

    private static void trim(Map<String, Entry> cache) {
        Iterator<Map.Entry<String, Entry>> iterator = cache.entrySet().iterator();
        int totalChars = 0;
        for (Entry entry : cache.values()) {
            totalChars += entry.body.length();
        }
        while ((cache.size() > MAX_ENTRIES || totalChars > MAX_TOTAL_CHARS) && iterator.hasNext()) {
            Entry removed = iterator.next().getValue();
            totalChars -= removed.body.length();
            iterator.remove();
        }
    }

    private static String normalizeRawKey(String url) {
        return url == null ? "" : url.trim();
    }

    private static String normalizeOffsetKey(String url, int offsetMs) {
        return normalizeRawKey(url) + "#offset=" + offsetMs;
    }

    private static class Entry {
        final String body;
        final long createdAtMs;

        Entry(String body) {
            this.body = body;
            this.createdAtMs = System.currentTimeMillis();
        }
    }
}
