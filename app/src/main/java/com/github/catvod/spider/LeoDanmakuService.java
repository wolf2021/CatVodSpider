package com.github.catvod.spider;

import android.app.Activity;
import android.os.Looper;
import android.text.TextUtils;

import com.github.catvod.spider.entity.DanmakuItem;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class LeoDanmakuService {

    // 线程池
    private static final ExecutorService searchExecutor = Executors.newFixedThreadPool(8);
    private static final ExecutorService seriesPrecacheExecutor = Executors.newSingleThreadExecutor();
    // 优化防重复推送：针对每个URL记录推送时间
    private static final Map<String, Long> lastPushTimes = new ConcurrentHashMap<>();
    private static final Map<String, Long> seriesPrecacheTimes = new ConcurrentHashMap<>();
    private static final long PUSH_MIN_INTERVAL = 3000; // 3秒内不重复推送
    private static final long SERIES_PRECACHE_INTERVAL = 5 * 60 * 1000;
    private static final int AUTO_SEARCH_TIMEOUT_MS = 30000;
    private static final int MANUAL_SEARCH_TIMEOUT_MS = 30000;
    private static final int AUTO_SEARCH_HTTP_TIMEOUT_MS = 30000;
    private static final int MANUAL_SEARCH_HTTP_TIMEOUT_MS = 10000;
    private static final int AUTO_SEARCH_MAX_THREADS = 16;
    private static final int AUTO_EMPTY_POLL_MS = 1500;
    private static final int MANUAL_EMPTY_POLL_MS = 8000;
    private static final double AUTO_SOURCE_CONFIDENCE_THRESHOLD = 0.85;

    // 新增：搜索结果封装类
    public static class SearchResult {
        public boolean found = false;
        public double similarity = 0.0;
        public DanmakuItem item = null;
        public String matchedName = null; // 实际参与相似度计算的名称

        public SearchResult(boolean found, double similarity, DanmakuItem item) {
            this.found = found;
            this.similarity = similarity;
            this.item = item;
        }

        public SearchResult(boolean found, double similarity, DanmakuItem item, String matchedName) {
            this.found = found;
            this.similarity = similarity;
            this.item = item;
            this.matchedName = matchedName;
        }
    }

    public static class ApiSourceTestResult {
        public boolean success;
        public long latencyMs;
        public String message;

        public ApiSourceTestResult(boolean success, long latencyMs, String message) {
            this.success = success;
            this.latencyMs = latencyMs;
            this.message = message;
        }
    }

    private static class AutoSourceSearchResult {
        String apiSourceName;
        String apiBase;
        List<DanmakuItem> items;

        AutoSourceSearchResult(String apiSourceName, String apiBase, List<DanmakuItem> items) {
            this.apiSourceName = apiSourceName;
            this.apiBase = apiBase;
            this.items = items;
        }
    }

    // 执行搜索
    public static List<DanmakuItem> searchDanmaku(EpisodeInfo episodeInfo, Activity activity) {
        return searchDanmaku(episodeInfo, activity, false);
    }

    // 执行搜索（带是否使用集数标识）
    public static List<DanmakuItem> searchDanmaku(EpisodeInfo episodeInfo, Activity activity, boolean useEpisodeNum) {
        if (episodeInfo == null || episodeInfo.getEpisodeNames() == null || episodeInfo.getEpisodeNames().isEmpty()) return new ArrayList<>();
        String keyword = episodeInfo.getEpisodeNames().get(0);

        final List<DanmakuItem> globalResults = Collections.synchronizedList(new ArrayList<DanmakuItem>());
        final List<java.util.concurrent.Future<List<DanmakuItem>>> submittedTasks = new ArrayList<>();
        ExecutorService executor = searchExecutor;
        boolean shutdownExecutor = false;

        try {
            DanmakuConfig config = DanmakuConfigManager.getConfig(activity);
            List<DanmakuApiSource> targets = config.getEnabledApiSources();
            if (targets.isEmpty()) {
                DanmakuSpider.log("没有启用的API地址");
                Utils.safeShowToast(activity, "没有启用的API地址");
                return globalResults;
            }

            if (useEpisodeNum) {
                int threadCount = Math.max(1, Math.min(targets.size(), AUTO_SEARCH_MAX_THREADS));
                executor = Executors.newFixedThreadPool(threadCount);
                shutdownExecutor = true;
            }

            ExecutorCompletionService<List<DanmakuItem>> completionService =
                    new ExecutorCompletionService<>(executor);
            int pendingTasks = 0;

            for (final DanmakuApiSource source : targets) {
                java.util.concurrent.Future<List<DanmakuItem>> future = completionService.submit(new Callable<List<DanmakuItem>>() {
                    @Override
                    public List<DanmakuItem> call() throws Exception {
                        List<DanmakuItem> items = doSearch(source.url, episodeInfo, useEpisodeNum);
                        attachApiSourceName(items, source.name);
                        return items;
                    }
                });
                submittedTasks.add(future);
                pendingTasks++;
            }

            // 超时控制
            long endTime = System.currentTimeMillis() + (useEpisodeNum ? AUTO_SEARCH_TIMEOUT_MS : MANUAL_SEARCH_TIMEOUT_MS);

            while (pendingTasks > 0) {
                long timeLeft = endTime - System.currentTimeMillis();
                if (timeLeft <= 0) break;

                try {
                    long wait = globalResults.isEmpty()
                            ? (useEpisodeNum ? AUTO_EMPTY_POLL_MS : MANUAL_EMPTY_POLL_MS)
                            : 50;
                    if (wait > timeLeft) wait = timeLeft;

                    java.util.concurrent.Future<List<DanmakuItem>> future =
                            completionService.poll(wait, TimeUnit.MILLISECONDS);
                    if (future != null) {
                        List<DanmakuItem> res = future.get();
                        pendingTasks--;

                        if (res != null && !res.isEmpty()) {
                            filterByKeyword(res, keyword);

                            if (!res.isEmpty()) {
                                DanmakuSpider.log("找到弹幕结果: " + res.size() + " 个");
                                globalResults.addAll(res);
                            }
                        }
                    } else {
                        if (useEpisodeNum && !globalResults.isEmpty()) break;
                    }
                } catch (Exception e) {
                    pendingTasks--;
                }
            }
        } catch (Exception e) {
            DanmakuSpider.log("搜索异常: " + e.getMessage());
        } finally {
            if (useEpisodeNum) {
                for (java.util.concurrent.Future<List<DanmakuItem>> future : submittedTasks) {
                    if (future != null && !future.isDone()) future.cancel(true);
                }
            }
            if (shutdownExecutor && executor != null) {
                executor.shutdownNow();
            }
        }

        updateLastDanmakuItems(globalResults);

        return globalResults;
    }

    private static void filterByKeyword(List<DanmakuItem> items, String keyword) {
        if (items == null || TextUtils.isEmpty(keyword)) return;
        Iterator<DanmakuItem> it = items.iterator();
        while (it.hasNext()) {
            DanmakuItem item = it.next();
            if (item == null || TextUtils.isEmpty(item.title)) {
                it.remove();
                continue;
            }
            if (!item.title.contains(keyword) && !keyword.contains(item.title)) {
                String kClean = keyword.replaceAll("\\s+", "");
                String tClean = item.title.replaceAll("\\s+", "");
                if (!tClean.contains(kClean) && !kClean.contains(tClean)) {
                    it.remove();
                }
            }
        }
    }

    private static void updateLastDanmakuItems(List<DanmakuItem> items) {
        ConcurrentHashMap<Integer, DanmakuItem> resultMap = new ConcurrentHashMap<>();
        ConcurrentHashMap<String, DanmakuItem> urlResultMap = new ConcurrentHashMap<>();
        if (items != null) {
            for (DanmakuItem item : items) {
                if (item == null) continue;
                if (item.getEpId() != null) resultMap.put(item.getEpId(), item);
                if (!TextUtils.isEmpty(item.getDanmakuUrl())) urlResultMap.put(item.getDanmakuUrl(), item);
            }
        }
        DanmakuManager.lastDanmakuItemMap = resultMap;
        DanmakuManager.lastDanmakuUrlItemMap = urlResultMap;
        DanmakuManager.cacheDanmakuItems(items);
    }

    private static void attachApiSourceName(List<DanmakuItem> items, String apiSourceName) {
        if (items == null) return;
        String name = DanmakuApiSource.normalizeName(apiSourceName);
        for (DanmakuItem item : items) {
            if (item != null) item.apiSourceName = name;
        }
    }

    // 执行搜索
    private static List<DanmakuItem> doSearch(String apiBase, EpisodeInfo episodeInfo) {
        return doSearch(apiBase, episodeInfo, false);
    }

    // 执行搜索（带是否使用集数标识）
    private static List<DanmakuItem> doSearch(String apiBase, EpisodeInfo episodeInfo, boolean useEpisodeNum) {
        List<DanmakuItem> list = new ArrayList<>();
        try {
            String keyword = episodeInfo.getEpisodeNames().get(0);
            // 尝试多种API路径
            String searchUrl = apiBase + "/api/v2/search/episodes?anime=" +
                URLEncoder.encode(keyword, "UTF-8");
            // 如果 useEpisodeNum 为 true 且 episodeNum 不为空，拼接 episode 参数
            if (useEpisodeNum && !TextUtils.isEmpty(episodeInfo.getEpisodeNum())) {
                searchUrl += "&episode=" + URLEncoder.encode(episodeInfo.getEpisodeNum(), "UTF-8");
            }
            DanmakuSpider.log("搜索URL: " + searchUrl);

            String json = getSearchResponse(searchUrl, useEpisodeNum);

            // 回退到旧API
            if (TextUtils.isEmpty(json)) {
                searchUrl = apiBase + "/search/episodes?anime=" +
                    URLEncoder.encode(keyword, "UTF-8");
                // 如果 useEpisodeNum 为 true 且 episodeNum 不为空，拼接 episode 参数
                if (useEpisodeNum && !TextUtils.isEmpty(episodeInfo.getEpisodeNum())) {
                    searchUrl += "&episode=" + URLEncoder.encode(episodeInfo.getEpisodeNum(), "UTF-8");
                }
                DanmakuSpider.log("回退搜索URL: " + searchUrl);
                json = getSearchResponse(searchUrl, useEpisodeNum);
            }

            if (TextUtils.isEmpty(json)) {
                DanmakuSpider.log("搜索响应为空");
                return list;
            }

            // 解析JSON
            JSONArray array = null;
            JSONObject rootOpt = null;

            if (json.trim().startsWith("[")) {
                array = new JSONArray(json);
            } else {
                rootOpt = new JSONObject(json);
                if (rootOpt.has("episodes")) array = rootOpt.optJSONArray("episodes");
                else if (rootOpt.has("animes")) array = rootOpt.optJSONArray("animes");
            }

            if (array == null) {
                DanmakuSpider.log("未找到episodes/animes数组");
                return list;
            }

            // 判断数据结构
            boolean isAnimeList = false;
            if (array.length() > 0) {
                JSONObject first = array.optJSONObject(0);
                if (first != null && first.has("episodes") && !first.has("episodeId")) {
                    isAnimeList = true;
                }
                if (rootOpt != null && rootOpt.has("animes")) {
                    isAnimeList = true;
                }
            }

            if (isAnimeList) {
                // 嵌套结构
                for (int i = 0; i < array.length(); i++) {
                    JSONObject anime = array.optJSONObject(i);
                    String animeTitle = anime.optString("animeTitle");
                    if (TextUtils.isEmpty(animeTitle)) animeTitle = anime.optString("title");

                    JSONArray eps = anime.optJSONArray("episodes");
                    if (eps != null) {
                        for (int j = 0; j < eps.length(); j++) {
                            JSONObject ep = eps.optJSONObject(j);
                            processEpisode(ep, anime, animeTitle, apiBase, eps.length(), list);
                        }
                    }
                }
            } else {
                // 扁平结构
                for (int i = 0; i < array.length(); i++) {
                    JSONObject ep = array.optJSONObject(i);
                    processEpisode(ep, null, null, apiBase, array.length(), list);
                }
            }
        } catch (Exception e) {
            DanmakuSpider.log("搜索解析错误: " + e.getMessage());
            e.printStackTrace();
        }

        return list;
    }

    private static String getSearchResponse(String searchUrl, boolean isAuto) {
        int timeoutMs = isAuto ? AUTO_SEARCH_HTTP_TIMEOUT_MS : MANUAL_SEARCH_HTTP_TIMEOUT_MS;
        NetworkUtils.HttpResult result = NetworkUtils.httpGet(searchUrl, timeoutMs);
        if (result.isOk()) return result.body;
        if (result.code > 0) {
            DanmakuSpider.log("HTTP " + result.code + ": " + searchUrl);
        } else {
            DanmakuSpider.log("网络请求失败: " + searchUrl + " - " + result.error);
        }
        return "";
    }

    public static ApiSourceTestResult testApiSource(String apiBase) {
        String base = DanmakuApiSource.normalizeUrl(apiBase);
        if (TextUtils.isEmpty(base) || !(base.startsWith("http://") || base.startsWith("https://"))) {
            return new ApiSourceTestResult(false, 0, "地址无效");
        }

        try {
            String keyword = URLEncoder.encode("test", "UTF-8");
            String url = base + "/api/v2/search/episodes?anime=" + keyword;
            NetworkUtils.HttpResult result = NetworkUtils.httpGet(url, 8000);
            if (!result.isOk()) {
                url = base + "/search/episodes?anime=" + keyword;
                result = NetworkUtils.httpGet(url, 8000);
            }

            if (result.isOk()) {
                return new ApiSourceTestResult(true, result.latencyMs, "HTTP " + result.code + "，" + result.latencyMs + "ms");
            }

            String message = !TextUtils.isEmpty(result.error) ? result.error : "HTTP " + result.code;
            return new ApiSourceTestResult(false, result.latencyMs, message);
        } catch (Exception e) {
            return new ApiSourceTestResult(false, 0, e.getMessage() != null ? e.getMessage() : e.getClass().getName());
        }
    }

    // 处理单集数据
    private static void processEpisode(JSONObject ep, String forcedTitle, String apiBase, List<DanmakuItem> list) {
        processEpisode(ep, null, forcedTitle, apiBase, 0, list);
    }

    private static void processEpisode(JSONObject ep, JSONObject anime, String forcedTitle, String apiBase, int episodeCount, List<DanmakuItem> list) {
        if (ep == null) return;
        String animeTitle = forcedTitle;
        if (TextUtils.isEmpty(animeTitle) && anime != null) animeTitle = firstOptString(anime, "animeTitle", "title", "name");
        if (TextUtils.isEmpty(animeTitle)) animeTitle = firstOptString(ep, "animeTitle", "title", "name");

        String epTitle = firstOptString(ep, "episodeTitle", "epTitle", "name");

        int epId = ep.optInt("episodeId", ep.optInt("epId", ep.optInt("id")));

        if (TextUtils.isEmpty(animeTitle)) {
            return;
        }

        DanmakuItem item = new DanmakuItem();
        item.title = animeTitle;
        item.epTitle = epTitle;
        item.epId = epId;
        item.apiBase = apiBase;
        item.animeType = firstOptString(anime, ep, "type", "animeType", "mediaType", "category");
        item.typeDescription = firstOptString(anime, ep, "typeDescription", "typeName", "categoryName", "typename");
        item.episodeCount = firstPositive(episodeCount,
                optInt(anime, "episodeCount", "episodesCount", "episodeTotal", "episodesTotal", "total"),
                optInt(ep, "episodeCount", "episodesCount", "episodeTotal", "episodesTotal", "total"));
        String explicitFrom = firstOptString(anime, ep, "from", "source", "site", "provider", "platform");

        String[] parts = animeTitle.split("(?i)from"); // 使用不区分大小写的正则表达式
        if (parts.length > 1) {
            String fromPart = parts[1].trim();
            if (!fromPart.isEmpty()) { // 额外检查分割后的部分是否为空
                item.from = fromPart;
                item.animeTitle = parts[0].trim();
            } else {
                item.from = explicitFrom;
                item.animeTitle = parts[0].trim();
            }
        } else {
            item.from = explicitFrom;
            item.animeTitle = animeTitle;
        }


        // 清理标题
        String temp = epTitle.replace(animeTitle, "");
        temp = temp.replaceAll("【.*?】", "").replaceAll("\\[.*?\\]", "").trim();
        if (temp.startsWith("-") || temp.startsWith("_")) {
            temp = temp.substring(1).trim();
        }

        item.shortTitle = temp;
        if (TextUtils.isEmpty(item.shortTitle)) {
            item.shortTitle = epTitle;
        }

        list.add(item);
    }

    private static String firstOptString(JSONObject object, String... keys) {
        if (object == null || keys == null) return "";
        for (String key : keys) {
            String value = object.optString(key);
            if (!TextUtils.isEmpty(value) && !"null".equalsIgnoreCase(value)) return value;
        }
        return "";
    }

    private static String firstOptString(JSONObject first, JSONObject second, String... keys) {
        String value = firstOptString(first, keys);
        return !TextUtils.isEmpty(value) ? value : firstOptString(second, keys);
    }

    private static int optInt(JSONObject object, String... keys) {
        if (object == null || keys == null) return 0;
        for (String key : keys) {
            if (!object.has(key)) continue;
            int value = object.optInt(key, 0);
            if (value > 0) return value;
        }
        return 0;
    }

    private static int firstPositive(int... values) {
        if (values == null) return 0;
        for (int value : values) {
            if (value > 0) return value;
        }
        return 0;
    }

    // 自动搜索 - 已修改为返回 SearchResult
    public static SearchResult autoSearch(String searchKeyword, EpisodeInfo episodeInfo, Activity activity) {
        if (TextUtils.isEmpty(searchKeyword)) {
            return new SearchResult(false, 0, null);
        }

        if (episodeInfo == null) {
            return new SearchResult(false, 0, null);
        }

        EpisodeInfo queryInfo = copyEpisodeInfoForKeyword(episodeInfo, searchKeyword);
        return autoSearchFirstMatchedSource(searchKeyword, queryInfo, activity);
    }

    private static SearchResult autoSearchFirstMatchedSource(String searchKeyword, EpisodeInfo queryInfo, Activity activity) {
        List<DanmakuItem> finishedResults = new ArrayList<>();
        List<java.util.concurrent.Future<AutoSourceSearchResult>> submittedTasks = new ArrayList<>();
        ExecutorService executor = null;

        try {
            DanmakuConfig config = DanmakuConfigManager.getConfig(activity);
            List<DanmakuApiSource> sources = config != null ? config.getEnabledApiSources() : new ArrayList<DanmakuApiSource>();
            if (sources.isEmpty()) {
                DanmakuSpider.log("没有启用的API地址");
                Utils.safeShowToast(activity, "没有启用的API地址");
                return new SearchResult(false, 0, null);
            }

            int threadCount = Math.max(1, Math.min(sources.size(), AUTO_SEARCH_MAX_THREADS));
            executor = Executors.newFixedThreadPool(threadCount);
            ExecutorCompletionService<AutoSourceSearchResult> completionService =
                    new ExecutorCompletionService<>(executor);

            int pendingTasks = 0;
            for (final DanmakuApiSource source : sources) {
                java.util.concurrent.Future<AutoSourceSearchResult> future =
                        completionService.submit(new Callable<AutoSourceSearchResult>() {
                            @Override
                            public AutoSourceSearchResult call() throws Exception {
                                List<DanmakuItem> items = doSearch(source.url, queryInfo, true);
                                attachApiSourceName(items, source.name);
                                return new AutoSourceSearchResult(source.name, source.url, items);
                            }
                        });
                submittedTasks.add(future);
                pendingTasks++;
            }

            SearchResult bestFallback = null;
            AutoSourceSearchResult bestFallbackSource = null;
            long endTime = System.currentTimeMillis() + AUTO_SEARCH_TIMEOUT_MS;
            while (pendingTasks > 0) {
                long timeLeft = endTime - System.currentTimeMillis();
                if (timeLeft <= 0) break;

                java.util.concurrent.Future<AutoSourceSearchResult> future =
                        completionService.poll(timeLeft, TimeUnit.MILLISECONDS);
                if (future == null) break;
                pendingTasks--;

                AutoSourceSearchResult sourceResult;
                try {
                    sourceResult = future.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    DanmakuSpider.log("自动搜索源异常: " + e.getMessage());
                    continue;
                }

                List<DanmakuItem> items = sourceResult != null ? sourceResult.items : null;
                if (items == null || items.isEmpty()) continue;

                filterByKeyword(items, searchKeyword);
                if (items.isEmpty()) continue;

                finishedResults.addAll(items);
                String sourceName = sourceResult.apiSourceName;
                String scopeName = TextUtils.isEmpty(sourceName) ? "自动搜索" : "自动搜索-" + sourceName;
                SearchResult currentResult = pickBestAutoMatch(searchKeyword, queryInfo, items, scopeName);
                if (!currentResult.found) continue;

                if (currentResult.similarity >= AUTO_SOURCE_CONFIDENCE_THRESHOLD) {
                    DanmakuSpider.log("✅ 自动匹配命中可靠结果，取消其他API源 (相似度: " + currentResult.similarity + ")");
                    scheduleSeriesItemsPrecache(sourceResult, queryInfo);
                    updateLastDanmakuItems(finishedResults);
                    return currentResult;
                }

                if (bestFallback == null || currentResult.similarity > bestFallback.similarity) {
                    bestFallback = currentResult;
                    bestFallbackSource = sourceResult;
                }
            }

            if (bestFallback != null) {
                DanmakuSpider.log("🏁 自动搜索未等到可靠命中，返回当前最佳候选 (相似度: " + bestFallback.similarity + ")");
                scheduleSeriesItemsPrecache(bestFallbackSource, queryInfo);
                updateLastDanmakuItems(finishedResults);
                return bestFallback;
            }
            updateLastDanmakuItems(finishedResults);
        } catch (Exception e) {
            DanmakuSpider.log("自动搜索异常: " + e.getMessage());
        } finally {
            for (java.util.concurrent.Future<AutoSourceSearchResult> future : submittedTasks) {
                if (future != null && !future.isDone()) future.cancel(true);
            }
            if (executor != null) executor.shutdownNow();
        }

        DanmakuSpider.log("自动搜索未找到任何结果 for keyword: " + searchKeyword);
        return new SearchResult(false, 0, null);
    }

    private static void scheduleSeriesItemsPrecache(final AutoSourceSearchResult sourceResult, EpisodeInfo queryInfo) {
        if (sourceResult == null || queryInfo == null || TextUtils.isEmpty(sourceResult.apiBase)) {
            return;
        }
        final String keyword = queryInfo.getEpisodeNames() != null && !queryInfo.getEpisodeNames().isEmpty()
                ? queryInfo.getEpisodeNames().get(0)
                : "";
        String seriesKey = DanmakuManager.normalizeSeriesKey(keyword);
        if (TextUtils.isEmpty(seriesKey)) seriesKey = keyword;
        final String cacheKey = sourceResult.apiBase + "#" + seriesKey;
        long now = System.currentTimeMillis();
        Long last = seriesPrecacheTimes.get(cacheKey);
        if (last != null && now - last < SERIES_PRECACHE_INTERVAL) {
            return;
        }
        seriesPrecacheTimes.put(cacheKey, now);

        final EpisodeInfo cacheQueryInfo = copyEpisodeInfoForKeyword(queryInfo, keyword);
        seriesPrecacheExecutor.execute(new Runnable() {
            @Override
            public void run() {
                loadSeriesItemsForCache(sourceResult, cacheQueryInfo, keyword);
            }
        });
        DanmakuSpider.log("📚 已安排同系列后台预缓存: " + sourceResult.apiSourceName);
    }

    private static void loadSeriesItemsForCache(AutoSourceSearchResult sourceResult, EpisodeInfo queryInfo, String keyword) {
        try {
            List<DanmakuItem> seriesItems = doSearch(sourceResult.apiBase, queryInfo, false);
            attachApiSourceName(seriesItems, sourceResult.apiSourceName);
            if (seriesItems == null || seriesItems.isEmpty()) {
                DanmakuSpider.log("📚 同系列预缓存无结果: " + sourceResult.apiSourceName);
                return;
            }
            filterByKeyword(seriesItems, keyword);
            if (seriesItems.isEmpty()) {
                DanmakuSpider.log("📚 同系列预缓存过滤后无结果: " + sourceResult.apiSourceName);
                return;
            }
            DanmakuManager.cacheDanmakuItems(seriesItems);
            DanmakuSpider.log("📚 同系列预缓存完成: " + sourceResult.apiSourceName + "，共" + seriesItems.size() + "条");
        } catch (Exception e) {
            DanmakuSpider.log("📚 同系列预缓存异常: " + e.getMessage());
        }
    }

    private static SearchResult pickBestAutoMatch(String searchKeyword, EpisodeInfo episodeInfo, List<DanmakuItem> results, String scopeName) {
        if (results == null || results.isEmpty()) {
            DanmakuSpider.log(scopeName + " 未找到任何结果 for keyword: " + searchKeyword);
            return new SearchResult(false, 0, null);
        }

        List<DanmakuItem> matchedItems = new ArrayList<>();
        DanmakuItem preferredSourceItem = getPreferredSourceItemForSearch(searchKeyword, episodeInfo);
        boolean hasEpisodeNum = !TextUtils.isEmpty(episodeInfo.getEpisodeNum());
        String requiredSeason = normalizeNumberText(episodeInfo.getEpisodeSeasonNum());
        String episodeInfoCompareText = buildEpisodeInfoCompareText(episodeInfo);
        String requiredDate = episodeInfo.getEpisodeDateCode();
        if (TextUtils.isEmpty(requiredDate)) {
            requiredDate = DanmakuUtils.extractEpisodeDateCode(episodeInfoCompareText);
        }
        String requiredPart = episodeInfo.getEpisodePartSuffix();
        if (TextUtils.isEmpty(requiredPart)) {
            requiredPart = extractEpisodePartSuffix(episodeInfoCompareText);
        }
        String episodeRequirement = hasEpisodeNum ? episodeInfo.getEpisodeNum() : "无，启用电影/综艺兜底";
        if (preferredSourceItem != null) {
            DanmakuSpider.log("🧭 自动续集匹配锁定上一集来源: " + DanmakuManager.getDisplaySource(preferredSourceItem));
        }
        DanmakuSpider.log("📥 " + scopeName + " 开始筛选，原始结果数: " + results.size() + "，集数要求: " + episodeRequirement);
        DanmakuSpider.log("🧩 " + scopeName + " 筛选要求: year=" + safe(episodeInfo.getEpisodeYear())
                + ", season=" + safe(requiredSeason)
                + ", date=" + safe(requiredDate)
                + ", part=" + safe(requiredPart));
        int noEpisodeTypeMismatchCount = 0;
        List<String> noEpisodeTypeMismatchSamples = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            DanmakuItem item = results.get(i);

            boolean isMatch = true;

            // 检查年份匹配
            if (!TextUtils.isEmpty(episodeInfo.getEpisodeYear())) {
                String itemYearText = buildItemCompareText(item);
                if (!itemYearText.contains(episodeInfo.getEpisodeYear())) {
                    if (!hasEpisodeNum && !containsAnyYear(itemYearText)) {
                        DanmakuSpider.log("  ℹ️ 候选无年份信息，保留无集数兜底候选: " + item.title);
                    } else {
                        DanmakuSpider.log("  ❌ 年份不匹配: " + item.title + " (要求年份: " + episodeInfo.getEpisodeYear() + ")");
                        isMatch = false;
                    }
                }
            }

            if (isMatch && !TextUtils.isEmpty(requiredDate)) {
                String itemDate = DanmakuUtils.extractEpisodeDateCode(buildItemCompareText(item));
                if (!TextUtils.isEmpty(itemDate) && !requiredDate.equals(itemDate)) {
                    DanmakuSpider.log("  ❌ 日期不匹配: " + item.title + " - " + item.epTitle
                            + " (要求: " + requiredDate + "，候选: " + itemDate + ")");
                    isMatch = false;
                }
            }

            if (isMatch && !TextUtils.isEmpty(requiredSeason)) {
                String itemSeason = extractSeasonNumber(buildItemCompareText(item));
                if (!TextUtils.isEmpty(itemSeason) && !requiredSeason.equals(itemSeason)) {
                    DanmakuSpider.log("  ❌ 季数不匹配: " + item.title + " (要求季数: " + requiredSeason + "，候选季数: " + itemSeason + ")");
                    isMatch = false;
                }
            }

            // 如果年份匹配成功或没有年份信息，检查集数匹配
            if (isMatch && hasEpisodeNum) {
                String episodeNum = episodeInfo.getEpisodeNum();
                try {
                    int epNum = Integer.parseInt(episodeNum);
                    if (epNum <= 0 || (epNum > 9999 && !isLikelyDateEpisode(episodeNum))) { // 防止过大的集数值
                        isMatch = false;
                    } else {
                        // 定义多种可能的集数格式
                        String[] formats = {
                                String.format("第%d集", epNum),
                                String.format("_%02d", epNum), // 补零格式，如 _01
                                String.format("_%d", epNum),   // 不补零格式，如 _1
                                String.format("第%d期", epNum),
                                String.format("第%02d集", epNum), // 补零格式，如 第01集
                                String.format("第%02d期", epNum),  // 补零格式，如 第01期
                                String.format("_%02d", epNum), // 补零格式，如 _01
                                String.format("_%dd", epNum)  // _1
                        };

                        boolean matchFound = false;
                        String itemEpisodeText = buildItemEpisodeText(item);
                        for (String format : formats) {
                            if (itemEpisodeText.contains(format)) {
                                matchFound = true;
                                break;
                            }
                        }
                        if (!matchFound) {
                            matchFound = itemEpisodeText.matches("(?i).*(?:^|[\\s._\\-\\[\\]【】(（])(?:ep|e)\\s*0*" + epNum + "(?:$|[\\s._\\-\\]】)）]).*")
                                    || itemEpisodeText.matches(".*(?:^|[\\s._\\-\\[\\]【】(（])0*" + epNum + "(?:$|[\\s._\\-\\]】)）]).*");
                        }
                        if (!matchFound) {
                            DanmakuSpider.log("  ❌ 集数不匹配: " + item.epTitle + " (要求集数: " + episodeNum + ")");
                            isMatch = false;
                        }
                    }
                } catch (NumberFormatException e) {
                    DanmakuSpider.log("集数格式错误: " + episodeNum);
                    isMatch = false;
                }
            }

            if (isMatch && !hasEpisodeNum && !isNoEpisodeAutoCandidate(item, results)) {
                noEpisodeTypeMismatchCount++;
                if (noEpisodeTypeMismatchSamples.size() < 3) {
                    noEpisodeTypeMismatchSamples.add(item.title + " - " + item.epTitle);
                }
                isMatch = false;
            }

            if (isMatch && !TextUtils.isEmpty(requiredPart)) {
                String itemPart = extractEpisodePartSuffix(buildItemCompareText(item));
                if (!TextUtils.isEmpty(itemPart) && !requiredPart.equals(itemPart)) {
                    DanmakuSpider.log("  ❌ 分段不匹配: " + item.title + " - " + item.epTitle
                            + " (要求: " + requiredPart + "，候选: " + itemPart + ")");
                    isMatch = false;
                }
            }

            if (isMatch && preferredSourceItem != null && !DanmakuManager.isSameDanmakuSource(item, preferredSourceItem)) {
                DanmakuSpider.log("  ❌ 来源不一致: " + item.title + " - "
                        + DanmakuManager.getDisplaySource(item) + " (上一集: "
                        + DanmakuManager.getDisplaySource(preferredSourceItem) + ")");
                isMatch = false;
            }

            if (isMatch) {
                String itemCompareText = buildItemCompareText(item);
                DanmakuSpider.log("  ✅ 匹配成功: " + item.title + " - " + item.epTitle
                        + " (date=" + DanmakuUtils.extractEpisodeDateCode(itemCompareText)
                        + ", part=" + extractEpisodePartSuffix(itemCompareText) + ")");
                matchedItems.add(item);
            }
        }
        if (!hasEpisodeNum && noEpisodeTypeMismatchCount > 0) {
            String sampleText = noEpisodeTypeMismatchSamples.isEmpty()
                    ? ""
                    : "，示例: " + TextUtils.join(" | ", noEpisodeTypeMismatchSamples)
                    + (noEpisodeTypeMismatchCount > noEpisodeTypeMismatchSamples.size() ? " 等" : "");
            DanmakuSpider.log("  ❌ 无集数兜底候选类型不匹配: " + noEpisodeTypeMismatchCount + " 条" + sampleText);
        }
        DanmakuSpider.log("📤 " + scopeName + " 筛选完成，匹配结果数: " + matchedItems.size());


        DanmakuItem selectedItem = null;
        double bestSimilarity = -1.0;
        int bestSourceScore = -1;
        String bestMatchedName = null; // 记录最佳匹配时使用的名称

        // 4. 从筛选结果中选择最佳匹配
        if (matchedItems.isEmpty()) {
            DanmakuSpider.log(hasEpisodeNum
                    ? "📭 带集数搜索没有命中对应集，放弃本轮自动选择"
                    : "📭 无集数搜索没有电影/综艺/单集候选，放弃本轮自动选择");
            return new SearchResult(false, 0, null, null);
        }
        List<DanmakuItem> listToSearch = matchedItems;

        for (DanmakuItem item : listToSearch) {
            // 准备用于比较的标题
            String rawTitle = item.getAnimeTitle() != null ? item.getAnimeTitle() : item.getTitle();

            String titleToCompare = rawTitle.split("【")[0].trim();
            String s2 = searchKeyword;
            if (TextUtils.isEmpty(episodeInfo.getEpisodeYear()) || !titleToCompare.contains(episodeInfo.getEpisodeYear())) {
                titleToCompare = titleToCompare.replaceAll("\\s*\\(\\d{4}\\)\\s*", "");
            } else {
                s2 = searchKeyword + "(" + episodeInfo.getEpisodeYear() + ")";
            }

            double similarity = calculateSimilarity(titleToCompare, s2);
            if (!hasEpisodeNum && isMovieOrVarietyCandidate(item)) {
                similarity = Math.min(1.0, similarity + 0.03);
            }

            DanmakuSpider.log("🤔 比较: " + titleToCompare + " vs " + s2 + " (相似度: " + similarity + ")");

            if (item.getAnimeTitle() != null && item.getAnimeTitle().contains("NaN")) {
                similarity -= 0.5; // 惩罚
            }

            int sourceScore = getPreferredSourceScore(item, preferredSourceItem);
            if (similarity > bestSimilarity
                    || (Math.abs(similarity - bestSimilarity) < 0.0001 && sourceScore > bestSourceScore)) {
                bestSimilarity = similarity;
                bestSourceScore = sourceScore;
                selectedItem = item;
                bestMatchedName = titleToCompare; // 直接记录计算时使用的名称
            }
        }

        if (selectedItem != null) {
            DanmakuSpider.log("🎯 " + scopeName + " 在筛选列表中自动搜索选择: " + selectedItem.title + " - " + selectedItem.epTitle + " (相似度: " + bestSimilarity + ")");
            return new SearchResult(true, bestSimilarity, selectedItem, bestMatchedName);
        }

        return new SearchResult(false, 0, null, null);
    }

    private static String buildItemCompareText(DanmakuItem item) {
        if (item == null) return "";
        return safe(item.title) + " " + safe(item.animeTitle) + " " + safe(item.epTitle) + " "
                + safe(item.shortTitle) + " " + safe(item.from) + " " + safe(item.animeType) + " "
                + safe(item.typeDescription);
    }

    private static String buildItemEpisodeText(DanmakuItem item) {
        if (item == null) return "";
        return safe(item.epTitle) + " " + safe(item.shortTitle);
    }

    private static String buildEpisodeInfoCompareText(EpisodeInfo episodeInfo) {
        if (episodeInfo == null) return "";
        StringBuilder sb = new StringBuilder();
        sb.append(safe(episodeInfo.getSeriesName())).append(' ');
        sb.append(safe(episodeInfo.getFileName())).append(' ');
        sb.append(safe(episodeInfo.getEpisodeDateCode())).append(' ');
        sb.append(safe(episodeInfo.getEpisodePartSuffix())).append(' ');
        if (episodeInfo.getEpisodeNames() != null) {
            for (String name : episodeInfo.getEpisodeNames()) {
                sb.append(safe(name)).append(' ');
            }
        }
        return sb.toString();
    }

    private static String extractSeasonNumber(String text) {
        if (TextUtils.isEmpty(text)) return "";
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "(?i)(?:第\\s*([零一二三四五六七八九十两0-9]+)\\s*[季部]|season\\s*([0-9]{1,2})|s([0-9]{1,2})(?:e[0-9]{1,3})?)");
        java.util.regex.Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String value = firstNonEmptyGroup(matcher, 1, 2, 3);
            value = normalizeNumberText(value);
            if (!TextUtils.isEmpty(value)) return value;
        }
        return "";
    }

    private static String extractEpisodePartSuffix(String text) {
        return DanmakuUtils.extractEpisodePartSuffix(text);
    }

    private static String firstNonEmptyGroup(java.util.regex.Matcher matcher, int... groups) {
        if (matcher == null || groups == null) return "";
        for (int group : groups) {
            String value = matcher.group(group);
            if (!TextUtils.isEmpty(value)) return value;
        }
        return "";
    }

    private static String normalizeNumberText(String value) {
        if (TextUtils.isEmpty(value)) return "";
        value = value.trim();
        if (value.matches("\\d+")) {
            return String.valueOf(Integer.parseInt(value.replaceFirst("^0+(?!$)", "")));
        }
        int chinese = parseSmallChineseNumber(value);
        return chinese > 0 ? String.valueOf(chinese) : "";
    }

    private static int parseSmallChineseNumber(String value) {
        if (TextUtils.isEmpty(value)) return 0;
        value = value.replace("两", "二").replace("零", "");
        if (value.matches("[一二三四五六七八九]")) return chineseDigit(value.charAt(0));
        int tenIndex = value.indexOf("十");
        if (tenIndex >= 0) {
            int tens = tenIndex == 0 ? 1 : chineseDigit(value.charAt(tenIndex - 1));
            int ones = tenIndex == value.length() - 1 ? 0 : chineseDigit(value.charAt(tenIndex + 1));
            return tens * 10 + ones;
        }
        return 0;
    }

    private static int chineseDigit(char c) {
        switch (c) {
            case '一': return 1;
            case '二': return 2;
            case '三': return 3;
            case '四': return 4;
            case '五': return 5;
            case '六': return 6;
            case '七': return 7;
            case '八': return 8;
            case '九': return 9;
            default: return 0;
        }
    }

    private static boolean containsAnyYear(String text) {
        return !TextUtils.isEmpty(text) && text.matches(".*(?:19|20)\\d{2}.*");
    }

    private static boolean isLikelyDateEpisode(String episodeNum) {
        if (TextUtils.isEmpty(episodeNum) || !episodeNum.matches("(?:19|20)\\d{6}")) return false;
        try {
            int month = Integer.parseInt(episodeNum.substring(4, 6));
            int day = Integer.parseInt(episodeNum.substring(6, 8));
            return month >= 1 && month <= 12 && day >= 1 && day <= 31;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isNoEpisodeAutoCandidate(DanmakuItem item, List<DanmakuItem> scopedResults) {
        if (item == null) return false;
        if (item.getEpisodeCount() > 0 && item.getEpisodeCount() <= 1) return true;
        if (scopedResults != null && scopedResults.size() == 1) return true;
        if (isMovieOrVarietyCandidate(item)) return true;

        String episodeText = (safe(item.epTitle) + " " + safe(item.shortTitle)).toLowerCase();
        return containsAny(episodeText, "正片", "全片", "全集", "完整", "特别篇", "特別篇", "sp", "special", "ova");
    }

    private static boolean isMovieOrVarietyCandidate(DanmakuItem item) {
        if (item == null) return false;
        String typeText = (safe(item.animeType) + " " + safe(item.typeDescription) + " "
                + safe(item.from) + " " + safe(item.title)).toLowerCase();
        return containsAny(typeText,
                "movie", "film", "jpmovie", "电影", "電影", "剧场", "劇場", "劇場版", "剧场版", "映画",
                "variety", "综艺", "綜藝", "真人秀", "脱口秀", "talk show", "tv show", "show");
    }

    private static boolean containsAny(String text, String... needles) {
        if (TextUtils.isEmpty(text) || needles == null) return false;
        for (String needle : needles) {
            if (!TextUtils.isEmpty(needle) && text.contains(needle.toLowerCase())) return true;
        }
        return false;
    }

    private static String safe(String text) {
        return text == null ? "" : text;
    }

    private static DanmakuItem getPreferredSourceItemForSearch(String searchKeyword, EpisodeInfo episodeInfo) {
        DanmakuItem lastItem = DanmakuManager.getLastDanmakuItem();
        if (lastItem == null || !DanmakuManager.hasDanmakuSource(lastItem)) return null;
        if (!isSameSearchSeries(lastItem, searchKeyword, episodeInfo)) return null;
        return lastItem;
    }

    private static boolean isSameSearchSeries(DanmakuItem item, String searchKeyword, EpisodeInfo episodeInfo) {
        String itemSeries = DanmakuManager.getItemSeriesName(item);
        if (matchesSeriesKey(itemSeries, searchKeyword)) return true;
        if (episodeInfo == null) return false;

        if (matchesSeriesKey(itemSeries, episodeInfo.getSeriesName())) return true;

        List<String> names = episodeInfo.getEpisodeNames();
        if (names != null) {
            for (String name : names) {
                if (matchesSeriesKey(itemSeries, name)) return true;
            }
        }
        return false;
    }

    private static boolean matchesSeriesKey(String left, String right) {
        String leftKey = DanmakuManager.normalizeSeriesKey(left);
        String rightKey = DanmakuManager.normalizeSeriesKey(right);
        if (TextUtils.isEmpty(leftKey) || TextUtils.isEmpty(rightKey)) return false;
        return leftKey.equals(rightKey) || leftKey.contains(rightKey) || rightKey.contains(leftKey);
    }

    private static int getPreferredSourceScore(DanmakuItem item, DanmakuItem preferredSourceItem) {
        if (item == null || preferredSourceItem == null) return 0;
        int score = 0;
        if (DanmakuManager.isSameDanmakuSource(item, preferredSourceItem)) score += 20;
        if (!TextUtils.isEmpty(preferredSourceItem.getApiBase())
                && preferredSourceItem.getApiBase().equals(item.getApiBase())) {
            score += 10;
        }
        return score;
    }

    private static EpisodeInfo copyEpisodeInfoForKeyword(EpisodeInfo source, String keyword) {
        EpisodeInfo copy = new EpisodeInfo();
        List<String> names = new ArrayList<>();
        names.add(keyword);
        copy.setEpisodeNames(names);
        copy.setEpisodeNum(source.getEpisodeNum());
        copy.setEpisodeYear(source.getEpisodeYear());
        copy.setEpisodeSeasonNum(source.getEpisodeSeasonNum());
        copy.setEpisodeDateCode(source.getEpisodeDateCode());
        copy.setEpisodePartSuffix(source.getEpisodePartSuffix());
        copy.setSeriesName(source.getSeriesName());
        copy.setFileName(source.getFileName());
        copy.setEpisodeUrl(source.getEpisodeUrl());
        return copy;
    }


    private static double calculateSimilarity(String s1, String s2) {
        String longer = s1, shorter = s2;
        if (s1.length() < s2.length()) {
            longer = s2;
            shorter = s1;
        }
        int longerLength = longer.length();
        if (longerLength == 0) {
            return 1.0;
        }
        return (longerLength - editDistance(longer, shorter)) / (double) longerLength;
    }

    private static int editDistance(String s1, String s2) {
        s1 = s1.toLowerCase();
        s2 = s2.toLowerCase();

        int[] costs = new int[s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) {
            int lastValue = i;
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0) {
                    costs[j] = j;
                } else {
                    if (j > 0) {
                        int newValue = costs[j - 1];
                        if (s1.charAt(i - 1) != s2.charAt(j - 1)) {
                            newValue = Math.min(Math.min(newValue, lastValue), costs[j]) + 1;
                        }
                        costs[j - 1] = lastValue;
                        lastValue = newValue;
                    }
                }
            }
            if (i > 0) {
                costs[s2.length()] = lastValue;
            }
        }
        return costs[s2.length()];
    }

    // 手动搜索（不使用集数参数，显示全部集数供用户选择）
    public static List<DanmakuItem> manualSearch(EpisodeInfo episodeInfo, Activity activity) {
        List<DanmakuItem> results = new ArrayList<>();

        if (episodeInfo == null || episodeInfo.getEpisodeNames() == null || episodeInfo.getEpisodeNames().isEmpty()) return results;

        try {
            // 手动搜索时不使用集数参数，让用户能看到全部集数
            results = searchDanmaku(episodeInfo, activity, false);
        } catch (Exception e) {
            DanmakuSpider.log("手动搜索失败: " + e.getMessage());
        }

        return results;
    }

    // 直接推送弹幕URL
    public static void pushDanmakuDirect(DanmakuItem danmakuItem, Activity activity, boolean isAuto) {
        pushDanmakuDirect(danmakuItem, activity, isAuto, false);
    }

    public static int validateDanmakuItem(DanmakuItem danmakuItem, int timeoutMs) {
        if (danmakuItem == null || danmakuItem.getEpId() == null || TextUtils.isEmpty(danmakuItem.getApiBase())) return -1;
        String danmakuData = NetworkUtils.robustHttpGet(danmakuItem.getDanmakuUrl(), timeoutMs, 1, 0);
        return countDanmakuItems(danmakuData);
    }

    public static void pushDanmakuDirect(DanmakuItem danmakuItem, Activity activity, boolean isAuto, boolean forceRefresh) {
        if (danmakuItem == null) {
            DanmakuSpider.log("⚠️ 推送弹幕为空，跳过");
            return;
        }
        String danmakuUrl = danmakuItem.getDanmakuUrl();
        if (TextUtils.isEmpty(danmakuUrl)) {
            DanmakuSpider.log("⚠️ 推送弹幕URL为空，跳过");
            return;
        }

        long currentTime = System.currentTimeMillis();
        DanmakuConfig config = DanmakuConfigManager.getConfig(activity);
        int offsetMs = config != null ? config.getDanmakuTimeOffsetMs() : 0;
        String pushKey = danmakuUrl + "#offset=" + offsetMs;

        // 检查此URL的上次推送时间
        Long lastPush = lastPushTimes.get(pushKey);
        if (!forceRefresh && lastPush != null && (currentTime - lastPush < PUSH_MIN_INTERVAL)) {
            DanmakuSpider.log("⚠️ 推送过于频繁 (同一URL和偏移)，跳过: " + danmakuUrl);
            return;
        }

        // 更新推送时间并清理旧记录
        lastPushTimes.put(pushKey, currentTime);
        cleanupOldPushTimes(currentTime);

        // 记录弹幕URL（这个可以在主线程执行）
        DanmakuSpider.recordDanmakuUrl(danmakuItem, isAuto);

        // 在网络请求前检查是否在主线程
        boolean isMainThread = Looper.myLooper() == Looper.getMainLooper();
        if (isMainThread) {
            DanmakuSpider.log("警告：推送弹幕在主线程调用，切换到子线程");
            // 切换到子线程执行
            new Thread(new Runnable() {
                @Override
                public void run() {
                    pushDanmakuInThread(danmakuItem, activity, !isAuto);
                }
            }).start();
        } else {
            // 已经在子线程，直接执行
            DanmakuSpider.log("已经在子线程，直接执行弹幕推送");
            pushDanmakuInThread(danmakuItem, activity, !isAuto);
        }
    }

    // 清理旧的推送记录，防止Map无限增大
    private static void cleanupOldPushTimes(long currentTime) {
        // 清理超过5分钟的记录
        long cleanupThreshold = 5 * 60 * 1000;
        Iterator<Map.Entry<String, Long>> iterator = lastPushTimes.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            if (currentTime - entry.getValue() > cleanupThreshold) {
                iterator.remove();
            }
        }
    }

    // 单独的网络推送方法，确保在子线程中执行
    private static void pushDanmakuInThread(DanmakuItem danmakuItem, Activity activity, boolean validateBeforePush) {
        try {
            if (TextUtils.isEmpty(danmakuItem.getDanmakuUrl())) {
                DanmakuSpider.log("推送弹幕URL为空");
                return;
            }

            DanmakuSpider.apiUrl = danmakuItem.getApiBase();

            // 步骤1: 先获取弹幕数据，验证是否有效
            String danmakuData = null;
            int danmakuCount = -1;
            final int maxRetries = 2;

            if (validateBeforePush) {
                for (int attempt = 0; attempt < maxRetries; attempt++) {
                    try {
                        danmakuData = NetworkUtils.robustHttpGet(danmakuItem.getDanmakuUrl(), 15000, 1, 0);
                        DanmakuSpider.log("获取弹幕数据 (尝试 " + (attempt + 1) + "/" + maxRetries + ") - URL: " + danmakuItem.getDanmakuUrl());

                        // 直接尝试解析，如果成功（返回-1代表解析异常，0代表无内容，大于0代表成功）
                        danmakuCount = countDanmakuItems(danmakuData);
                        if (danmakuCount > 0) {
                            DanmakuSpider.log("✅ 获取到有效弹幕数据，总数: " + danmakuCount + " 条");
                            break; // 成功获取，跳出重试
                        } else if (danmakuCount == 0) {
                            DanmakuSpider.log("⚠️ 弹幕数据为空或无内容，尝试次数: " + (attempt + 1) + "/" + maxRetries);
                        } else {
                            DanmakuSpider.log("⚠️ 弹幕数据格式错误或解析失败，尝试次数: " + (attempt + 1) + "/" + maxRetries);
                        }

                        // 重试等待
                        if (attempt < maxRetries - 1) {
                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                            }
                        }
                    } catch (Exception e) {
                        DanmakuSpider.log("获取弹幕数据异常 (尝试 " + (attempt + 1) + "/" + maxRetries + "): " + e.getMessage());
                        if (attempt < maxRetries - 1) {
                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                            }
                        }
                    }
                }
            } else {
                DanmakuSpider.log("自动推送跳过弹幕预验证，直接刷新播放器");
            }

            // 如果数据验证失败，直接返回
            if (validateBeforePush && danmakuCount <= 0) {
                DanmakuSpider.log("❌ 无法获取有效的弹幕数据（或弹幕为空），取消推送");
                if (activity != null && !activity.isFinishing()) {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Utils.safeShowToast(activity, "弹幕数据验证失败，请稍后重试");
                        }
                    });
                }
                return;
            }

            // 步骤2: 数据验证成功，开始推送
            String localIp = NetworkUtils.getLocalIpAddress();
            DanmakuConfig config = DanmakuConfigManager.getConfig(activity);
            int offsetMs = config != null ? config.getDanmakuTimeOffsetMs() : 0;
            String refreshPath = buildDanmakuRefreshPath(danmakuItem, localIp, offsetMs);
            String pushUrl = "http://" + localIp + ":" + Utils.getPort() + "/action?do=refresh&type=danmaku&path=" +
                    URLEncoder.encode(refreshPath, "UTF-8");
            if (offsetMs != 0) {
                DanmakuSpider.log("启用弹幕时间偏移: " + DanmakuUtils.formatOffsetLabel(offsetMs) + "，通过本地代理推送");
            }
            DanmakuSpider.log("推送地址: " + pushUrl);

            String pushResp = "";
            for (int i = 0; i < 3; i++) {
                pushResp = NetworkUtils.robustHttpGet(pushUrl, 5000, 1, 0);
                DanmakuSpider.log("推送尝试 " + (i + 1) + "/3: " + (!TextUtils.isEmpty(pushResp) ? "成功" : "失败"));
                if (!TextUtils.isEmpty(pushResp) && pushResp.toLowerCase().contains("ok")) {
                    DanmakuSpider.log("✅ 推送成功");
                    break;
                }
                if (i < 2) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }

            final int finalDanmakuCount = danmakuCount;
            final String finalPushResp = pushResp;

            // 步骤3: 在主线程显示结果
            if (activity != null && !activity.isFinishing()) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!TextUtils.isEmpty(finalPushResp) && finalPushResp.toLowerCase().contains("ok")) {
                            String message = finalDanmakuCount > 0 ?
                                    String.format("弹幕已推送: %s - %s (共%d条)",
                                            danmakuItem.getTitle(),
                                            danmakuItem.getEpTitle(),
                                            finalDanmakuCount) :
                                    String.format("弹幕已推送: %s - %s",
                                            danmakuItem.getTitle(),
                                            danmakuItem.getEpTitle());
                            Utils.safeShowToast(activity, message);
                            DanmakuSpider.log(message);
                        } else {
                            Utils.safeShowToast(activity, "推送失败: 无响应或响应异常");
                            DanmakuSpider.log("❌ 推送失败，响应: " + finalPushResp);
                        }
                    }
                });
            }
        } catch (Exception e) {
            DanmakuSpider.log("推送异常: " + e.getMessage());
            e.printStackTrace();
            if (activity != null && !activity.isFinishing()) {
                Utils.safeShowToast(activity, "推送异常: " + e.getMessage());
            }
        }
    }

    private static String buildDanmakuRefreshPath(DanmakuItem danmakuItem, String localIp, int offsetMs) throws Exception {
        String rawUrl = danmakuItem.getDanmakuUrl();
        if (offsetMs == 0) return rawUrl;
        return "http://" + localIp + ":9810/danmaku?url=" +
                URLEncoder.encode(rawUrl, "UTF-8") +
                "&t=" + System.currentTimeMillis();
    }

    // 辅助方法：从XML中解析弹幕总数
    private static int countDanmakuItems(String xmlData) {
        try {
            if (TextUtils.isEmpty(xmlData) || !xmlData.trim().startsWith("<")) return 0;

            javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
            org.xml.sax.InputSource is = new org.xml.sax.InputSource(new java.io.StringReader(xmlData));
            org.w3c.dom.Document doc = builder.parse(is);

            return doc.getElementsByTagName("d").getLength();
        } catch (Exception e) {
            DanmakuSpider.log("解析弹幕数据异常: " + e.getMessage());
            return -1; // 返回-1表示解析异常
        }
    }
}
