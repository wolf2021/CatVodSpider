package com.github.catvod.spider;

import android.text.TextUtils;

import com.github.catvod.spider.entity.DanmakuItem;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class DanmakuManager {

    public static String lastAutoDanmakuUrl = "";  // 上次自动推送的弹幕URL
    public static String lastManualDanmakuUrl = ""; // 上次手动选择的弹幕URL
    public static String lastDanmakuUrl = ""; // 上次弹幕URL
    public static ConcurrentMap<Integer, DanmakuItem> lastDanmakuItemMap = new ConcurrentHashMap<>();
    public static ConcurrentMap<String, DanmakuItem> lastDanmakuUrlItemMap = new ConcurrentHashMap<>();
    public static ConcurrentMap<String, DanmakuItem> seriesDanmakuCache = new ConcurrentHashMap<>();
    public static int lastDanmakuId = -1;          // 上次的弹幕ID
    public static boolean hasAutoSearched = false; // 是否已自动搜索过
    public static String lastProcessedTitle = "";  // 上次处理的标题
    public static String currentVideoSignature = "";  // 当前视频的唯一标识（基于标题提取）
    public static long lastVideoDetectedTime = 0;     // 上次检测到视频的时间

    public static void recordDanmakuUrl(DanmakuItem danmakuItem, boolean isAuto) {
        if (isAuto) {
            lastAutoDanmakuUrl = danmakuItem.getDanmakuUrl();
            DanmakuSpider.log("记录自动弹幕URL: " + danmakuItem.getDanmakuUrl());
        } else {
            lastManualDanmakuUrl = danmakuItem.getDanmakuUrl();
            DanmakuSpider.log("记录手动弹幕URL: " + danmakuItem.getDanmakuUrl());
        }
        lastDanmakuUrl = danmakuItem.getDanmakuUrl();
        lastDanmakuId = danmakuItem.getEpId();
        if (danmakuItem.getEpId() != null) {
            lastDanmakuItemMap.put(danmakuItem.getEpId(), danmakuItem);
        }
        if (danmakuItem.getDanmakuUrl() != null) {
            lastDanmakuUrlItemMap.put(danmakuItem.getDanmakuUrl(), danmakuItem);
        }
        cacheDanmakuItem(danmakuItem);

        // 记录视频检测时间
        lastVideoDetectedTime = System.currentTimeMillis();
//        DanmakuSpider.log("✅ 更新视频检测时间: " + lastVideoDetectedTime);

        // 设置已搜索过，这样换集时就会尝试递增
        if (lastDanmakuId > 0) {
            hasAutoSearched = true;
//            DanmakuSpider.log("✅ 设置 hasAutoSearched = true (ID: " + lastDanmakuId + ")");
        }
    }

    public static DanmakuItem getNextDanmakuItem(int currentEpisodeNum, int newEpisodeNum) {
        int baseId = lastDanmakuId;
        int nextId = baseId + (newEpisodeNum - currentEpisodeNum);
        DanmakuSpider.log("📝 获取下一个弹幕URL: " + baseId + " -> " + nextId);

        if (nextId <= 0) {
            return null;
        }

        DanmakuItem nextDanmakuItem = lastDanmakuItemMap.get(nextId);
        if (nextDanmakuItem != null) {
            DanmakuSpider.log("✅ 获取到下一个弹幕弹幕信息: " + nextDanmakuItem.toString());
            return nextDanmakuItem;
        }

        DanmakuSpider.log("🧮 ID位移缓存未命中: " + baseId + " -> " + nextId);
        return null;
    }

    public static void cacheDanmakuItems(List<DanmakuItem> items) {
        if (items == null) return;
        for (DanmakuItem item : items) {
            cacheDanmakuItem(item);
        }
    }

    public static void cacheDanmakuItem(DanmakuItem item) {
        if (item == null || item.getEpId() == null || TextUtils.isEmpty(item.getApiBase())) return;
        String titleKey = normalizeSeriesKey(getItemSeriesName(item));
        if (TextUtils.isEmpty(titleKey)) return;
        seriesDanmakuCache.put(titleKey + "#" + item.getApiBase() + "#" + item.getEpId(), item);
    }

    public static List<DanmakuItem> getSeriesCacheSnapshot() {
        return new ArrayList<>(seriesDanmakuCache.values());
    }

    public static String getItemSeriesName(DanmakuItem item) {
        if (item == null) return "";
        if (!TextUtils.isEmpty(item.getAnimeTitle())) return item.getAnimeTitle();
        return item.getTitle();
    }

    public static String normalizeSeriesKey(String title) {
        if (TextUtils.isEmpty(title)) return "";
        return title
                .replaceAll("(?i)\\s*from\\s+.*$", "")
                .replaceAll("\\s*\\(\\d{4}\\)\\s*", "")
                .replaceAll("[\\s\\p{Punct}，。；：、【】《》“”‘’（）]+", "")
                .toLowerCase();
    }

    public static DanmakuItem getLastDanmakuItem() {
        if (lastDanmakuId > 0) {
            DanmakuItem item = lastDanmakuItemMap.get(lastDanmakuId);
            if (item != null) return item;
        }

        if (lastDanmakuUrl != null && !lastDanmakuUrl.isEmpty()) {
            for (DanmakuItem item : lastDanmakuItemMap.values()) {
                if (item != null && lastDanmakuUrl.equals(item.getDanmakuUrl())) {
                    return item;
                }
            }
        }

        return null;
    }

    public static void resetAutoSearch() {
        hasAutoSearched = false;
        lastProcessedTitle = "";
        currentVideoSignature = "";
        lastVideoDetectedTime = 0;
        lastDanmakuId = -1;
        lastAutoDanmakuUrl = "";
        lastManualDanmakuUrl = "";
        lastDanmakuUrl = "";
        lastDanmakuItemMap.clear();
        lastDanmakuUrlItemMap.clear();
        seriesDanmakuCache.clear();
    }
}
