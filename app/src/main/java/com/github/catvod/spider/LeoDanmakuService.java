package com.github.catvod.spider;

import android.app.Activity;
import android.os.Looper;
import android.text.TextUtils;

import com.github.catvod.spider.entity.DanmakuItem;
import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private static final long REFLECTION_RESOLVE_FAILURE_COOLDOWN_MS = 5000;
    private static volatile ReflectionBinding reflectionBindingCache;
    private static volatile long lastReflectionResolveFailureAtMs = 0;
    private static volatile String lastReflectionResolveFailureKey = "";
    private static volatile String lastReflectionResolveFailureReason = "";

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
        List<String> keywordAliases = buildKeywordAliases(keyword);
        Iterator<DanmakuItem> it = items.iterator();
        while (it.hasNext()) {
            DanmakuItem item = it.next();
            if (item == null || TextUtils.isEmpty(item.title)) {
                it.remove();
                continue;
            }
            if (!matchesKeywordAliases(item, keywordAliases)) {
                it.remove();
            }
        }
    }

    private static class ReflectionBinding {
        final String cacheKey;
        final WeakReference<Object> playerRef;
        final Class<?> danmakuClass;
        final Method setDanmakuMethod;
        final long resolvedAtMs;

        ReflectionBinding(String cacheKey, Object player, Class<?> danmakuClass, Method setDanmakuMethod, long resolvedAtMs) {
            this.cacheKey = cacheKey;
            this.playerRef = new WeakReference<>(player);
            this.danmakuClass = danmakuClass;
            this.setDanmakuMethod = setDanmakuMethod;
            this.resolvedAtMs = resolvedAtMs;
        }
    }

    private static boolean matchesKeywordAliases(DanmakuItem item, List<String> keywordAliases) {
        if (item == null || keywordAliases == null || keywordAliases.isEmpty()) return false;
        String[] candidates = new String[]{
                item.title,
                item.animeTitle,
                item.getTitleWithEp()
        };
        for (String candidate : candidates) {
            if (TextUtils.isEmpty(candidate)) continue;
            String normalizedCandidate = normalizeKeywordForMatch(candidate);
            for (String alias : keywordAliases) {
                if (TextUtils.isEmpty(alias)) continue;
                if (normalizedCandidate.contains(alias) || alias.contains(normalizedCandidate)) {
                    return true;
                }
            }
        }
        return false;
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
            String json = "";
            for (String alias : buildKeywordAliases(keyword)) {
                json = getSearchResponse(buildSearchUrl(apiBase, "/api/v2/search/episodes", alias, episodeInfo, useEpisodeNum), useEpisodeNum);
                if (!TextUtils.isEmpty(json)) break;
                json = getSearchResponse(buildSearchUrl(apiBase, "/search/episodes", alias, episodeInfo, useEpisodeNum), useEpisodeNum);
                if (!TextUtils.isEmpty(json)) break;
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

    private static String buildSearchUrl(String apiBase, String path, String keyword, EpisodeInfo episodeInfo, boolean useEpisodeNum) throws Exception {
        String searchUrl = apiBase + path + "?anime=" + URLEncoder.encode(keyword, "UTF-8");
        if (useEpisodeNum && episodeInfo != null && !TextUtils.isEmpty(episodeInfo.getEpisodeNum())) {
            searchUrl += "&episode=" + URLEncoder.encode(episodeInfo.getEpisodeNum(), "UTF-8");
        }
        DanmakuSpider.log("搜索URL: " + searchUrl);
        return searchUrl;
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

    private static List<String> buildKeywordAliases(String keyword) {
        List<String> aliases = new ArrayList<>();
        addKeywordAlias(aliases, keyword);
        if (TextUtils.isEmpty(keyword)) return aliases;

        String season = extractSeasonNumber(keyword);
        if (TextUtils.isEmpty(season)) return aliases;

        String chineseSeason = toChineseSeasonNumber(season);
        if (!TextUtils.isEmpty(chineseSeason)) {
            addKeywordAlias(aliases, keyword.replaceAll("第\\s*" + java.util.regex.Pattern.quote(season) + "\\s*[季部]", "第" + chineseSeason + "季"));
            addKeywordAlias(aliases, keyword.replaceAll("第\\s*[零一二三四五六七八九十两0-9]+\\s*[季部]", "第" + chineseSeason + "季"));
        }

        addKeywordAlias(aliases, keyword.replaceAll("第\\s*[零一二三四五六七八九十两0-9]+\\s*[季部]", "第" + season + "季"));
        addKeywordAlias(aliases, keyword.replaceAll("(?i)season\\s*\\d{1,2}", "Season " + season));
        addKeywordAlias(aliases, keyword.replaceAll("(?i)s\\s*\\d{1,2}", "S" + season));

        return aliases;
    }

    private static void addKeywordAlias(List<String> aliases, String value) {
        if (aliases == null || TextUtils.isEmpty(value)) return;
        String normalized = normalizeKeywordForMatch(value);
        if (TextUtils.isEmpty(normalized) || aliases.contains(normalized)) return;
        aliases.add(normalized);
    }

    private static String normalizeKeywordForMatch(String text) {
        if (TextUtils.isEmpty(text)) return "";
        String normalized = text;
        String season = extractSeasonNumber(normalized);
        if (!TextUtils.isEmpty(season)) {
            normalized = normalized.replaceAll("(?i)season\\s*\\d{1,2}", "第" + season + "季");
            normalized = normalized.replaceAll("(?i)s\\s*" + java.util.regex.Pattern.quote(season) + "(?!\\d)", "第" + season + "季");
            normalized = normalized.replaceAll("第\\s*[零一二三四五六七八九十两0-9]+\\s*[季部]", "第" + season + "季");
        }
        return normalized.replaceAll("\\s+", "").trim().toLowerCase();
    }

    private static String toChineseSeasonNumber(String value) {
        String normalized = normalizeNumberText(value);
        if (TextUtils.isEmpty(normalized)) return "";
        int number;
        try {
            number = Integer.parseInt(normalized);
        } catch (Exception e) {
            return "";
        }
        if (number <= 0) return "";
        if (number < 10) {
            return new String[]{"", "一", "二", "三", "四", "五", "六", "七", "八", "九"}[number];
        }
        if (number == 10) return "十";
        if (number < 20) return "十" + toChineseSeasonNumber(String.valueOf(number - 10));
        if (number < 100) {
            int tens = number / 10;
            int ones = number % 10;
            return toChineseSeasonNumber(String.valueOf(tens)) + "十" + (ones > 0 ? toChineseSeasonNumber(String.valueOf(ones)) : "");
        }
        return normalized;
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
        copy.setSearchCacheKey(source.getSearchCacheKey());
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
        String danmakuData = DanmakuXmlCache.fetchRaw(danmakuItem.getDanmakuUrl(), timeoutMs, 1, 0);
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
                        danmakuData = DanmakuXmlCache.fetchRaw(danmakuItem.getDanmakuUrl(), 15000, 1, 0);
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
            String pushResp = "";
            boolean reflectionPushed = false;
            if (reflectionPushed) {
                pushResp = "OK";
                DanmakuSpider.log("✅ 已通过反射方式推送弹幕: " + buildDanmakuDisplayName(danmakuItem));
            } else {
                DanmakuSpider.log("反射推送不可用，回退到HTTP推送: " + pushUrl);
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
        return "http://" + localIp + ":9810/danmaku?url=" +
                URLEncoder.encode(rawUrl, "UTF-8") +
                "&t=" + System.currentTimeMillis();
    }

    private static boolean tryPushDanmakuByReflection(final DanmakuItem danmakuItem, final Activity activity, final String danmakuPath) {
        if (activity == null || TextUtils.isEmpty(danmakuPath)) return false;

        final ReflectionBinding binding = resolveReflectionBinding(activity);
        if (binding == null) return false;

        final CountDownLatch latch = new CountDownLatch(1);
        final boolean[] success = new boolean[]{false};
        final String[] error = new String[]{null};

        Runnable task = new Runnable() {
            @Override
            public void run() {
                try {
                    Object player = binding.playerRef.get();
                    if (player == null) {
                        clearReflectionBindingCache();
                        error[0] = "宿主播放器缓存已失效";
                        return;
                    }
                    Object danmaku = createFongMiDanmaku(activity, danmakuItem, danmakuPath, binding.danmakuClass);
                    if (danmaku == null) {
                        error[0] = "未能构造宿主弹幕对象";
                        return;
                    }
                    binding.setDanmakuMethod.setAccessible(true);
                    binding.setDanmakuMethod.invoke(player, danmaku);
                    success[0] = true;
                } catch (Throwable e) {
                    clearReflectionBindingCache();
                    error[0] = e.getClass().getSimpleName() + ": " + e.getMessage();
                } finally {
                    latch.countDown();
                }
            }
        };

        if (Looper.myLooper() == Looper.getMainLooper()) {
            task.run();
        } else {
            activity.runOnUiThread(task);
            try {
                latch.await(3, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                error[0] = "等待主线程反射推送被中断";
            }
        }

        if (success[0]) return true;

        String lastError = error[0];
        if (!TextUtils.isEmpty(lastError)) {
            DanmakuSpider.log("反射推送失败: " + lastError);
        }
        return false;
    }

    private static Object resolveFongMiPlayer(Activity activity, Class<?> danmakuClass) throws Exception {
        Object player = tryResolveExactFongMiDanmakuTarget(activity, danmakuClass);
        if (player != null) return player;

        Activity topActivity = Utils.getTopActivity();
        if (topActivity != null && topActivity != activity) {
            player = tryResolveExactFongMiDanmakuTarget(topActivity, danmakuClass);
            if (player != null) return player;
        }

        player = tryResolveKnownHostControllerTarget(activity, danmakuClass);
        if (player != null) return player;
        if (topActivity != null && topActivity != activity) {
            player = tryResolveKnownHostControllerTarget(topActivity, danmakuClass);
            if (player != null) return player;
        }

        player = tryResolveDanmakuTargetFromActivityDirect(activity, danmakuClass);
        if (player != null) return player;
        if (topActivity != null && topActivity != activity) {
            player = tryResolveDanmakuTargetFromActivityDirect(topActivity, danmakuClass);
            if (player != null) return player;
        }

        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        player = tryResolveDanmakuTargetFromObject(activity, "activity-root", 5, visited, true, danmakuClass);
        if (player != null) return player;
        if (topActivity != null && topActivity != activity) {
            player = tryResolveDanmakuTargetFromObject(topActivity, "top-activity-root", 5, visited, true, danmakuClass);
            if (player != null) return player;
        }

        player = tryResolvePlayerFromActivity(activity, danmakuClass);
        if (player != null) return player;

        if (topActivity != null && topActivity != activity) {
            player = tryResolvePlayerFromActivity(topActivity, danmakuClass);
            if (player != null) return player;
        }

        player = tryResolvePlayerFromServer(activity, danmakuClass);
        if (player != null) return player;

        player = tryResolvePlayerFromActivityFields(activity, danmakuClass);
        if (player != null) return player;

        if (topActivity != null && topActivity != activity) {
            player = tryResolvePlayerFromActivityFields(topActivity, danmakuClass);
            if (player != null) return player;
        }

        for (Activity candidate : getAliveActivities()) {
            if (candidate == null || candidate == activity || candidate == topActivity) continue;
            player = tryResolveExactFongMiDanmakuTarget(candidate, danmakuClass);
            if (player != null) return player;
            player = tryResolveDanmakuTargetFromActivityDirect(candidate, danmakuClass);
            if (player != null) return player;
            player = tryResolveDanmakuTargetFromObject(candidate, "candidate-root", 4, visited, false, danmakuClass);
            if (player != null) return player;
            player = tryResolvePlayerFromActivity(candidate, danmakuClass);
            if (player != null) return player;
            player = tryResolvePlayerFromActivityFields(candidate, danmakuClass);
            if (player != null) return player;
        }

        return null;
    }

    public static boolean canPushDanmakuByReflection(Activity activity) {
        if (activity == null || activity.isFinishing()) return false;
        try {
            return resolveReflectionBinding(activity) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static ReflectionBinding resolveReflectionBinding(Activity activity) {
        if (activity == null || activity.isFinishing()) return null;
        ReflectionBinding cached = getCachedReflectionBinding(activity);
        if (cached != null) {
            return cached;
        }

        String cacheKey = buildReflectionCacheKey(activity);
        long now = System.currentTimeMillis();
        if (cacheKey.equals(lastReflectionResolveFailureKey)
                && now - lastReflectionResolveFailureAtMs < REFLECTION_RESOLVE_FAILURE_COOLDOWN_MS) {
            return null;
        }

        try {
            Class<?> danmakuClass = resolveHostDanmakuClass(activity);
            if (danmakuClass == null) {
                markReflectionResolveFailure(cacheKey, "未找到宿主Danmaku类");
                return null;
            }
            Object player = resolveFongMiPlayer(activity, danmakuClass);
            if (player == null) {
                markReflectionResolveFailure(cacheKey, "未找到宿主播放器实例");
                return null;
            }
            Method setDanmakuMethod = findDanmakuMethod(player.getClass(), danmakuClass);
            if (setDanmakuMethod == null) {
                markReflectionResolveFailure(cacheKey, "宿主目标缺少setDanmaku/o方法: " + player.getClass().getName());
                return null;
            }
            setDanmakuMethod.setAccessible(true);
            ReflectionBinding binding = new ReflectionBinding(cacheKey, player, danmakuClass, setDanmakuMethod, now);
            reflectionBindingCache = binding;
            lastReflectionResolveFailureAtMs = 0;
            lastReflectionResolveFailureKey = "";
            lastReflectionResolveFailureReason = "";
            return binding;
        } catch (Throwable e) {
            markReflectionResolveFailure(cacheKey, e.getClass().getSimpleName() + ": " + e.getMessage());
            return null;
        }
    }

    private static ReflectionBinding getCachedReflectionBinding(Activity activity) {
        ReflectionBinding binding = reflectionBindingCache;
        if (binding == null || activity == null) return null;
        if (!buildReflectionCacheKey(activity).equals(binding.cacheKey)) return null;
        if (binding.danmakuClass == null || binding.setDanmakuMethod == null) return null;
        Object player = binding.playerRef != null ? binding.playerRef.get() : null;
        if (player == null) return null;
        if (!binding.setDanmakuMethod.getDeclaringClass().isAssignableFrom(player.getClass())) return null;
        return binding;
    }

    private static void clearReflectionBindingCache() {
        reflectionBindingCache = null;
    }

    private static void markReflectionResolveFailure(String cacheKey, String reason) {
        clearReflectionBindingCache();
        lastReflectionResolveFailureKey = cacheKey == null ? "" : cacheKey;
        lastReflectionResolveFailureAtMs = System.currentTimeMillis();
        lastReflectionResolveFailureReason = reason == null ? "" : reason;
    }

    private static String buildReflectionCacheKey(Activity activity) {
        if (activity == null) return "";
        String packageName = "";
        Package pkg = activity.getClass().getPackage();
        if (pkg != null && pkg.getName() != null) packageName = pkg.getName();
        return activity.getClass().getName() + "#" + packageName;
    }

    private static Object tryResolveKnownHostControllerTarget(Activity activity, Class<?> danmakuClass) {
        if (activity == null) return null;

        Object target = tryResolveKnownControllerType(activity, danmakuClass,
                "F3.f",
                "com.fongmi.android.tv.ui.activity.VideoActivity",
                "k0",
                "OK影视Mobile/F3.f.k0");
        if (target != null) return target;

        target = tryResolveKnownControllerType(activity, danmakuClass,
                "C3.e",
                "com.fongmi.android.tv.ui.activity.VideoActivity",
                "i0",
                "OK影视TV/C3.e.i0");
        if (target != null) return target;

        target = tryResolveKnownControllerField(activity, danmakuClass, "Z", "l0", "影视+/Z.l0");
        if (target != null) return target;

        target = tryResolveKnownControllerByMethodSignatures(activity, danmakuClass);
        if (target != null) return target;

        target = tryResolveKnownPlaybackServiceStaticByMethodSignatures(activity, danmakuClass);
        if (target != null) return target;

        return null;
    }

    private static Object tryResolveKnownControllerField(Activity activity, Class<?> danmakuClass, String fieldName, String methodName, String label) {
        try {
            Field field = findField(activity.getClass(), fieldName);
            if (field == null) {
                DanmakuSpider.log("宿主链路未命中: " + label + " 缺少字段 " + fieldName);
                return null;
            }
            field.setAccessible(true);
            Object controller = field.get(activity);
            if (controller == null) {
                DanmakuSpider.log("宿主链路未命中: " + label + " 字段为空");
                return null;
            }
            Method method = findCompatibleMethod(controller.getClass(), methodName, danmakuClass);
            if (method == null) {
                DanmakuSpider.log("宿主链路未命中: " + label + " 缺少方法 " + methodName + "(" + danmakuClass.getSimpleName() + ")");
                return null;
            }
            DanmakuSpider.log("宿主链路命中: " + label + " -> " + controller.getClass().getName() + "#" + method.getName());
            return controller;
        } catch (Throwable e) {
            DanmakuSpider.log("宿主链路异常: " + label + " - " + e.getMessage());
            return null;
        }
    }

    private static Object tryResolveKnownControllerType(Activity activity,
                                                        Class<?> danmakuClass,
                                                        String simpleTypeName,
                                                        String ownerClassName,
                                                        String methodName,
                                                        String label) {
        try {
            if (!ownerClassName.equals(activity.getClass().getName())) {
                return null;
            }
            Class<?> current = activity.getClass();
            while (current != null) {
                for (Field field : current.getDeclaredFields()) {
                    Class<?> fieldType = field.getType();
                    if (fieldType == null) continue;
                    if (!simpleTypeName.equals(fieldType.getName()) && !simpleTypeName.equals(fieldType.getSimpleName())) continue;
                    field.setAccessible(true);
                    Object controller = field.get(activity);
                    if (controller == null) {
                        DanmakuSpider.log("宿主链路未命中: " + label + " 字段为空(" + current.getName() + "#" + field.getName() + ")");
                        return null;
                    }
                    Method method = findCompatibleMethod(controller.getClass(), methodName, danmakuClass);
                    if (method == null) {
                        DanmakuSpider.log("宿主链路未命中: " + label + " 缺少方法 " + methodName + "(" + danmakuClass.getSimpleName() + ")");
                        return null;
                    }
                    DanmakuSpider.log("宿主链路命中: " + label + " -> " + current.getName() + "#" + field.getName() + " / " + controller.getClass().getName() + "#" + method.getName());
                    return controller;
                }
                current = current.getSuperclass();
            }
            DanmakuSpider.log("宿主链路未命中: " + label + " 缺少类型字段 " + simpleTypeName);
            return null;
        } catch (Throwable e) {
            DanmakuSpider.log("宿主链路异常: " + label + " - " + e.getMessage());
            return null;
        }
    }

    private static Object tryResolveKnownControllerByMethodSignatures(Activity activity, Class<?> danmakuClass) {
        try {
            String[] methodNames = new String[]{"k0", "i0", "l0", "o"};
            Class<?> current = activity.getClass();
            while (current != null) {
                for (Field field : current.getDeclaredFields()) {
                    field.setAccessible(true);
                    Object value = field.get(activity);
                    if (value == null) continue;
                    Class<?> valueClass = value.getClass();
                    if (isSimpleValueType(valueClass) || isViewOnlyDanmakuTarget(valueClass) || isWrapperOrAsyncTarget(valueClass)) continue;
                    for (String methodName : methodNames) {
                        Method method = findCompatibleMethod(valueClass, methodName, danmakuClass);
                        if (method == null) continue;
                        DanmakuSpider.log("宿主链路命中: 方法签名字段 -> " + current.getName() + "#" + field.getName() + " / " + valueClass.getName() + "#" + method.getName());
                        return value;
                    }
                }
                current = current.getSuperclass();
            }
            DanmakuSpider.log("宿主链路未命中: 方法签名字段扫描未找到 k0/i0/l0/o(" + danmakuClass.getSimpleName() + ")");
            return null;
        } catch (Throwable e) {
            DanmakuSpider.log("宿主链路异常: 方法签名字段扫描 - " + e.getMessage());
            return null;
        }
    }

    private static Object tryResolveKnownPlaybackServiceStaticByMethodSignatures(Activity activity, Class<?> danmakuClass) {
        try {
            ClassLoader loader = activity.getClassLoader() != null ? activity.getClassLoader() : LeoDanmakuService.class.getClassLoader();
            Class<?> serviceClass = Class.forName("com.fongmi.android.tv.service.PlaybackService", false, loader);
            String[] methodNames = new String[]{"k0", "i0", "l0", "o"};
            Class<?> current = serviceClass;
            while (current != null) {
                for (Field field : current.getDeclaredFields()) {
                    if ((field.getModifiers() & java.lang.reflect.Modifier.STATIC) == 0) continue;
                    field.setAccessible(true);
                    Object value = field.get(null);
                    if (value == null) continue;
                    Class<?> valueClass = value.getClass();
                    if (isSimpleValueType(valueClass) || isViewOnlyDanmakuTarget(valueClass) || isWrapperOrAsyncTarget(valueClass)) continue;
                    for (String methodName : methodNames) {
                        Method method = findCompatibleMethod(valueClass, methodName, danmakuClass);
                        if (method == null) continue;
                        DanmakuSpider.log("宿主链路命中: PlaybackService静态控制器 -> " + current.getName() + "#" + field.getName() + " / " + valueClass.getName() + "#" + method.getName());
                        return value;
                    }
                }
                current = current.getSuperclass();
            }
            DanmakuSpider.log("宿主链路未命中: PlaybackService静态控制器扫描未找到 k0/i0/l0/o(" + danmakuClass.getSimpleName() + ")");
            return null;
        } catch (Throwable e) {
            DanmakuSpider.log("宿主链路异常: PlaybackService静态控制器扫描 - " + e.getMessage());
            return null;
        }
    }

    private static Object tryResolveExactFongMiDanmakuTarget(Activity activity, Class<?> danmakuClass) {
        if (activity == null) return null;
        try {
            if (!"com.fongmi.android.tv.ui.activity.VideoActivity".equals(activity.getClass().getName())) {
                return null;
            }
            Field serviceField = findField(activity.getClass(), "F");
            if (serviceField == null) {
                DanmakuSpider.log("精确链路失败: VideoActivity 缺少字段 F");
                return null;
            }
            serviceField.setAccessible(true);
            Object service = serviceField.get(activity);
            if (service == null) {
                DanmakuSpider.log("精确链路失败: VideoActivity.F 为空");
                return null;
            }
            if (!"com.fongmi.android.tv.service.PlaybackService".equals(service.getClass().getName())) {
                DanmakuSpider.log("精确链路失败: VideoActivity.F 类型异常 -> " + service.getClass().getName());
                logObjectShape("VideoActivity.F", service, danmakuClass, 2);
                return null;
            }
            Field danmakuField = findField(service.getClass(), "u");
            if (danmakuField == null) {
                DanmakuSpider.log("精确链路失败: PlaybackService 缺少字段 u");
                logObjectShape("PlaybackService", service, danmakuClass, 2);
                return null;
            }
            danmakuField.setAccessible(true);
            Object target = danmakuField.get(service);
            if (target == null) {
                DanmakuSpider.log("精确链路失败: PlaybackService.u 为空");
                logObjectShape("PlaybackService", service, danmakuClass, 2);
                return null;
            }
            if (findDanmakuMethod(target.getClass(), danmakuClass) == null) {
                DanmakuSpider.log("精确链路失败: PlaybackService.u 不含 Danmaku 方法 -> " + target.getClass().getName());
                logObjectShape("PlaybackService.u", target, danmakuClass, 2);
                return null;
            }
            DanmakuSpider.log("精确链路命中: VideoActivity.F.u -> " + target.getClass().getName());
            return target;
        } catch (Throwable e) {
            DanmakuSpider.log("精确链路反射失败: " + e.getMessage());
            return null;
        }
    }

    private static Class<?> resolveHostDanmakuClass(Activity activity) throws Exception {
        ClassLoader loader = activity.getClassLoader() != null ? activity.getClassLoader() : LeoDanmakuService.class.getClassLoader();
        return findHostClass(loader, activity, "bean.Danmaku");
    }

    private static Object createFongMiDanmaku(Activity activity, DanmakuItem danmakuItem, String danmakuPath, Class<?> danmakuClass) throws Exception {
        if (danmakuClass == null) return null;
        Constructor<?> constructor = danmakuClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object danmaku = constructor.newInstance();

        Method setName = findMethod(danmakuClass, "setName", String.class);
        Method setUrl = findMethod(danmakuClass, "setUrl", String.class);
        Method setSelected = findMethod(danmakuClass, "setSelected", boolean.class);
        if (setUrl == null) return null;

        if (setName != null) {
            setName.setAccessible(true);
            setName.invoke(danmaku, buildDanmakuDisplayName(danmakuItem));
        }
        setUrl.setAccessible(true);
        setUrl.invoke(danmaku, danmakuPath);
        if (setSelected != null) {
            setSelected.setAccessible(true);
            setSelected.invoke(danmaku, true);
        }
        return danmaku;
    }

    private static String buildDanmakuDisplayName(DanmakuItem danmakuItem) {
        if (danmakuItem == null) return "弹幕";
        String title = danmakuItem.getTitleWithEp();
        String source = danmakuItem.getApiSourceName();
        if (!TextUtils.isEmpty(title) && !TextUtils.isEmpty(source)) return title + " · " + source;
        if (!TextUtils.isEmpty(title)) return title;
        if (!TextUtils.isEmpty(source)) return source;
        if (!TextUtils.isEmpty(danmakuItem.getEpTitle())) return danmakuItem.getEpTitle();
        if (!TextUtils.isEmpty(danmakuItem.getTitle())) return danmakuItem.getTitle();
        return danmakuItem.getDanmakuUrl();
    }

    private static Method findCompatibleMethod(Class<?> type, String name, Class<?> parameterType) {
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                Class<?>[] params = method.getParameterTypes();
                if (method.getName().equals(name) &&
                        params.length == 1 &&
                        params[0].isAssignableFrom(parameterType)) {
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredMethod(name, parameterTypes);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static Object tryResolvePlayerFromActivity(Activity activity, Class<?> danmakuClass) {
        if (activity == null) return null;
        try {
            Method playerMethod = findMethod(activity.getClass(), "player");
            if (playerMethod != null) {
                playerMethod.setAccessible(true);
                Object player = playerMethod.invoke(activity);
                if (player != null) {
                    Object resolved = normalizeResolvedPlayerTarget(player, "activity.player()", danmakuClass, true);
                    if (resolved != null) return resolved;
                }
                DanmakuSpider.log("activity.player() 返回空: " + activity.getClass().getName());
            }
        } catch (Throwable e) {
            DanmakuSpider.log("activity.player() 反射失败: " + e.getMessage());
        }

        try {
            Method serviceMethod = findMethod(activity.getClass(), "service");
            if (serviceMethod != null) {
                serviceMethod.setAccessible(true);
                Object service = serviceMethod.invoke(activity);
                if (service == null) {
                    DanmakuSpider.log("activity.service() 返回空: " + activity.getClass().getName());
                } else {
                    Object player = tryResolvePlayerFromService(service, true, danmakuClass);
                    if (player != null) {
                        DanmakuSpider.log("反射定位播放器成功: activity.service().player() -> " + activity.getClass().getName());
                        return player;
                    }
                }
            }
        } catch (Throwable e) {
            DanmakuSpider.log("activity.service() 反射失败: " + e.getMessage());
        }

        try {
            Field serviceField = findField(activity.getClass(), "mService");
            if (serviceField != null) {
                serviceField.setAccessible(true);
                Object service = serviceField.get(activity);
                if (service == null) {
                    DanmakuSpider.log("activity.mService 返回空: " + activity.getClass().getName());
                } else {
                    Object player = tryResolvePlayerFromService(service, true, danmakuClass);
                    if (player != null) {
                        DanmakuSpider.log("反射定位播放器成功: activity.mService.player() -> " + activity.getClass().getName());
                        return player;
                    }
                }
            }
        } catch (Throwable e) {
            DanmakuSpider.log("activity.mService 反射失败: " + e.getMessage());
        }

        return null;
    }

    private static Object tryResolvePlayerFromServer(Activity activity, Class<?> danmakuClass) {
        try {
            ClassLoader loader = activity != null && activity.getClassLoader() != null ? activity.getClassLoader() : LeoDanmakuService.class.getClassLoader();
            Class<?> serverClass = findHostClass(loader, activity, "server.Server");
            if (serverClass == null) return null;
            Method getMethod = findMethod(serverClass, "get");
            Method getServiceMethod = findMethod(serverClass, "getService");
            if (getMethod == null || getServiceMethod == null) return null;
            getMethod.setAccessible(true);
            Object server = getMethod.invoke(null);
            if (server == null) return null;
            getServiceMethod.setAccessible(true);
            Object service = getServiceMethod.invoke(server);
            if (service == null) {
                DanmakuSpider.log("Server.get().getService() 返回空");
                return null;
            }
            Object player = tryResolvePlayerFromService(service, true, danmakuClass);
            if (player != null) {
                DanmakuSpider.log("反射定位播放器成功: Server.get().getService().player()");
                return player;
            }
        } catch (Throwable e) {
            DanmakuSpider.log("Server.get().getService() 反射失败: " + e.getMessage());
        }
        return null;
    }

    private static Object tryResolvePlayerFromService(Object service, boolean verbose, Class<?> danmakuClass) {
        if (service == null) return null;
        try {
            Object directTarget = tryResolveDanmakuTargetFromPlaybackServiceDirect(service, verbose, danmakuClass);
            if (directTarget != null) return directTarget;

            Object directCandidate = tryResolveDanmakuTargetFromObject(service, service.getClass().getName(), 2,
                    Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>()), false, danmakuClass);
            if (directCandidate != null) {
                if (verbose) DanmakuSpider.log("反射定位弹幕目标成功: service-root-scan -> " + directCandidate.getClass().getName());
                return directCandidate;
            }

            Method playerMethod = findMethod(service.getClass(), "player");
            if (playerMethod == null) {
                if (verbose) DanmakuSpider.log("service.player() 方法不存在: " + service.getClass().getName());
            } else {
                playerMethod.setAccessible(true);
                Object player = playerMethod.invoke(service);
                if (player == null) {
                    if (verbose) DanmakuSpider.log("service.player() 返回空: " + service.getClass().getName());
                } else {
                    Object resolved = normalizeResolvedPlayerTarget(player, "service.player()", danmakuClass, verbose);
                    if (resolved != null) return resolved;
                }
            }

            Field playerField = findField(service.getClass(), "player");
            if (playerField == null) playerField = findField(service.getClass(), "mPlayer");
            if (playerField != null) {
                playerField.setAccessible(true);
                Object player = playerField.get(service);
                if (player == null) {
                    if (verbose) DanmakuSpider.log("service.player 字段返回空: " + service.getClass().getName());
                } else {
                    Object resolved = normalizeResolvedPlayerTarget(player, "service.player field", danmakuClass, verbose);
                    if (resolved != null) return resolved;
                }
            } else if (verbose) {
                DanmakuSpider.log("service.player 字段不存在: " + service.getClass().getName());
            }

            Object candidate = tryResolveDanmakuTargetFromFields(service, service.getClass().getName(), 4, Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>()), verbose, danmakuClass);
            if (candidate != null) return candidate;
        } catch (Throwable e) {
            if (verbose) DanmakuSpider.log("service.player/service字段 反射失败: " + e.getMessage());
        }
        return null;
    }

    private static Object tryResolveDanmakuTargetFromActivityDirect(Activity activity, Class<?> danmakuClass) {
        if (activity == null) return null;
        try {
            Field serviceField = findField(activity.getClass(), "F");
            if (serviceField == null) return null;
            serviceField.setAccessible(true);
            Object service = serviceField.get(activity);
            if (service == null) {
                DanmakuSpider.log("activity.F 返回空: " + activity.getClass().getName());
                return null;
            }
            Object target = tryResolveDanmakuTargetFromPlaybackServiceDirect(service, true, danmakuClass);
            if (target != null) {
                DanmakuSpider.log("反射定位弹幕目标成功: activity.F.u -> " + activity.getClass().getName() + " / " + target.getClass().getName());
                return target;
            }
        } catch (Throwable e) {
            DanmakuSpider.log("activity.F 反射失败: " + e.getMessage());
        }

        Object candidate = tryResolveDanmakuTargetFromLikelyActivityFields(activity, true, danmakuClass);
        if (candidate != null) return candidate;
        return null;
    }

    private static Object tryResolveDanmakuTargetFromPlaybackServiceDirect(Object service, boolean verbose, Class<?> danmakuClass) {
        if (service == null) return null;
        try {
            Field danmakuField = findField(service.getClass(), "u");
            if (danmakuField == null) {
                if (verbose) DanmakuSpider.log("service.u 字段不存在: " + service.getClass().getName());
                return null;
            }
            danmakuField.setAccessible(true);
            Object target = danmakuField.get(service);
            if (target == null) {
                if (verbose) DanmakuSpider.log("service.u 返回空: " + service.getClass().getName());
                return null;
            }
            if (findDanmakuMethod(target.getClass(), danmakuClass) != null) {
                Object normalized = normalizeControllerLikeTarget(target, "service.u", danmakuClass, verbose);
                if (normalized != null) return normalized;
            }
            if (verbose) DanmakuSpider.log("service.u 不含弹幕方法: " + target.getClass().getName());
        } catch (Throwable e) {
            if (verbose) DanmakuSpider.log("service.u 反射失败: " + e.getMessage());
        }

        try {
            Class<?> current = service.getClass();
            while (current != null) {
                for (Field field : current.getDeclaredFields()) {
                    field.setAccessible(true);
                    Object value = field.get(service);
                    if (value == null) continue;
                    Class<?> valueClass = value.getClass();
                    if (isSimpleValueType(valueClass)) continue;
                    if (!isLikelyDanmakuCarrierField(field, valueClass) && findDanmakuMethod(valueClass, danmakuClass) == null) continue;
                    Object resolved = tryResolveDanmakuTargetFromObject(value,
                            service.getClass().getName() + "#" + field.getName(),
                            2,
                            Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>()),
                            false,
                            danmakuClass);
                    if (resolved != null) {
                        if (verbose) DanmakuSpider.log("反射定位弹幕目标成功: service-field-scan -> " + valueClass.getName());
                        return resolved;
                    }
                }
                current = current.getSuperclass();
            }
        } catch (Throwable e) {
            if (verbose) DanmakuSpider.log("service 字段扫描失败: " + e.getMessage());
        }
        return null;
    }

    private static Object tryResolvePlayerFromActivityFields(Activity activity, Class<?> danmakuClass) {
        if (activity == null) return null;
        Class<?> current = activity.getClass();
        while (current != null) {
            Field[] fields = current.getDeclaredFields();
            for (Field field : fields) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(activity);
                    if (value == null) continue;

                    if (isFongMiPlayerClass(value.getClass())) {
                        Object resolved = normalizeResolvedPlayerTarget(value, current.getName() + "#" + field.getName(), danmakuClass, true);
                        if (resolved != null) return resolved;
                    }

                    if (isLikelyPlaybackServiceField(field, value)) {
                        Object nestedPlayer = tryResolvePlayerFromService(value, true, danmakuClass);
                        if (nestedPlayer != null) {
                            DanmakuSpider.log("反射定位播放器成功: 字段服务 " + current.getName() + "#" + field.getName());
                            return nestedPlayer;
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static Object normalizeResolvedPlayerTarget(Object candidate, String label, Class<?> danmakuClass, boolean verbose) {
        if (candidate == null) return null;
        Class<?> candidateClass = candidate.getClass();
        if (findDanmakuMethod(candidateClass, danmakuClass) != null &&
                !isViewOnlyDanmakuTarget(candidateClass) &&
                !isWrapperOrAsyncTarget(candidateClass)) {
            if (verbose) DanmakuSpider.log("反射定位弹幕目标成功: " + label + " -> " + candidateClass.getName());
            return candidate;
        }
        if (isViewOnlyDanmakuTarget(candidateClass) || isWrapperOrAsyncTarget(candidateClass) || isLikelyPlayerWrapperClass(candidateClass)) {
            Object nested = tryResolveDanmakuTargetFromObject(candidate,
                    label + "#" + candidateClass.getSimpleName(),
                    3,
                    Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>()),
                    false,
                    danmakuClass);
            if (nested != null) {
                if (verbose) DanmakuSpider.log("反射定位弹幕目标成功: " + label + " -> " + nested.getClass().getName());
                return nested;
            }
            return null;
        }
        if (isFongMiPlayerClass(candidateClass)) {
            if (verbose) DanmakuSpider.log("反射定位播放器成功: " + label + " -> " + candidateClass.getName());
            return candidate;
        }
        return null;
    }

    private static boolean isFongMiPlayerClass(Class<?> type) {
        if (type == null) return false;
        String name = type.getName();
        return name.endsWith(".player.PlayerManager") ||
                name.endsWith(".player.Players") ||
                name.startsWith("com.fongmi.android.tv.player.") ||
                name.endsWith(".Player") ||
                name.endsWith(".ExoMediaPlayer") ||
                name.endsWith(".IjkPlayer") ||
                name.endsWith(".VodPlayer");
    }

    private static boolean hasSingleArgMethodNamed(Class<?> type, String methodName) {
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(methodName) && method.getParameterTypes().length == 1) {
                    return true;
                }
            }
            current = current.getSuperclass();
        }
        return false;
    }

    private static Method findDanmakuMethod(Class<?> targetClass, Class<?> danmakuClass) {
        if (targetClass == null || danmakuClass == null) return null;
        Method method = findCompatibleMethod(targetClass, "o", danmakuClass);
        if (method != null) return method;
        method = findCompatibleMethod(targetClass, "setDanmaku", danmakuClass);
        if (method != null) return method;
        return findAnyCompatibleSingleArgMethod(targetClass, danmakuClass);
    }

    private static Object tryResolveDanmakuTargetFromObject(Object holder, String ownerLabel, int depth, Set<Object> visited, boolean verbose, Class<?> danmakuClass) {
        if (holder == null || depth < 0) return null;
        if (visited.contains(holder)) return null;
        visited.add(holder);

        Class<?> holderClass = holder.getClass();
        Method danmakuMethod = findDanmakuMethod(holderClass, danmakuClass);
        if (danmakuMethod != null) {
            Object normalized = normalizeControllerLikeTarget(holder, ownerLabel, danmakuClass, verbose);
            if (normalized != null) return normalized;
        }

        if (isFongMiPlayerClass(holderClass) && !isLikelyPlayerWrapperClass(holderClass)) {
            if (verbose) DanmakuSpider.log("反射定位弹幕目标成功: " + ownerLabel + " -> " + holderClass.getName());
            return holder;
        }

        return tryResolveDanmakuTargetFromFields(holder, ownerLabel, depth, visited, verbose, danmakuClass);
    }

    private static Object tryResolveDanmakuTargetFromFields(Object holder, String ownerLabel, int depth, Set<Object> visited, boolean verbose, Class<?> danmakuClass) {
        if (holder == null || depth < 0) return null;
        Class<?> current = holder.getClass();
        while (current != null) {
            for (Field field : current.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(holder);
                    if (value == null) continue;

                    Class<?> valueClass = value.getClass();
                    if (isSimpleValueType(valueClass)) continue;

                    if (isFongMiPlayerClass(valueClass) &&
                            !isLikelyPlayerWrapperClass(valueClass) &&
                            !isViewOnlyDanmakuTarget(valueClass) &&
                            !isWrapperOrAsyncTarget(valueClass)) {
                        if (verbose) DanmakuSpider.log("反射定位播放器成功: " + ownerLabel + "#" + field.getName() + " -> " + valueClass.getName());
                        return value;
                    }

                    if (findDanmakuMethod(valueClass, danmakuClass) != null) {
                        Object normalized = normalizeControllerLikeTarget(value, ownerLabel + "#" + field.getName(), danmakuClass, verbose);
                        if (normalized != null) return normalized;
                    }

                    if (isLikelyPlaybackServiceField(field, value)) {
                        Object player = tryResolvePlayerFromService(value, false, danmakuClass);
                        if (player != null) {
                            if (verbose) DanmakuSpider.log("反射定位播放器成功: " + ownerLabel + "#" + field.getName() + " -> service-scan");
                            return player;
                        }
                    }

                    if (depth > 0) {
                        Object nested = tryResolveDanmakuTargetFromObject(value, ownerLabel + "#" + field.getName(), depth - 1, visited, verbose, danmakuClass);
                        if (nested != null) return nested;
                    }
                } catch (Throwable ignored) {
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static boolean isLikelyDanmakuCarrierField(Field field, Class<?> valueClass) {
        if (field == null || valueClass == null) return false;
        String fieldName = field.getName().toLowerCase();
        String typeName = valueClass.getName().toLowerCase();
        if ("u".equals(fieldName) || "f".equals(fieldName) || "h".equals(fieldName) || "d".equals(fieldName)) return true;
        if (fieldName.contains("player") || fieldName.contains("manager") || fieldName.contains("controller") ||
                fieldName.contains("session") || fieldName.contains("spec") || fieldName.contains("render") ||
                fieldName.contains("engine") || fieldName.contains("media") || fieldName.contains("core")) return true;
        return typeName.contains("player") || typeName.contains("manager") || typeName.contains("controller") ||
                typeName.contains("session") || typeName.contains("spec") || typeName.contains("render") ||
                typeName.contains("engine") || typeName.contains("media") || typeName.contains("exo") ||
                typeName.contains("ijk");
    }

    private static boolean isSimpleValueType(Class<?> type) {
        if (type == null) return true;
        if (type.isPrimitive() || type.isArray() || type.isEnum()) return true;
        String name = type.getName();
        return name.startsWith("java.lang.") ||
                name.startsWith("java.util.") ||
                name.startsWith("android.animation.") ||
                name.startsWith("android.graphics.") ||
                name.startsWith("android.drawable.") ||
                name.startsWith("android.content.res.") ||
                name.startsWith("android.view.") ||
                name.startsWith("android.view.animation.") ||
                name.startsWith("android.widget.") ||
                name.startsWith("androidx.");
    }

    private static boolean isLikelyPlaybackServiceField(Field field, Object value) {
        if (field == null || value == null) return false;
        String fieldName = field.getName().toLowerCase();
        String typeName = value.getClass().getName().toLowerCase();
        if (value instanceof CharSequence) return false;
        if (isSimpleValueType(value.getClass())) return false;
        if ("mservice".equals(fieldName) || "service".equals(fieldName)) return true;
        return typeName.endsWith(".service.playbackservice") ||
                typeName.equals("com.fongmi.android.tv.service.playbackservice") ||
                typeName.contains("playbackservice");
    }

    private static boolean isLikelyPlayerWrapperClass(Class<?> type) {
        if (type == null) return false;
        String name = type.getName().toLowerCase();
        if (name.endsWith(".players")) return true;
        int objectFieldCount = 0;
        int danmakuMethodCount = 0;
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getParameterTypes().length == 1) danmakuMethodCount++;
            }
            for (Field field : current.getDeclaredFields()) {
                if (field.getType().isPrimitive()) continue;
                if (isSimpleValueType(field.getType())) continue;
                objectFieldCount++;
            }
            current = current.getSuperclass();
        }
        return danmakuMethodCount == 0 && objectFieldCount > 0;
    }

    private static boolean isViewOnlyDanmakuTarget(Class<?> type) {
        if (type == null) return false;
        String name = type.getName();
        if (name.contains("master.flame.danmaku.ui.widget.DanmakuView")) return true;
        Class<?> current = type;
        while (current != null) {
            if ("android.view.View".equals(current.getName())) return true;
            current = current.getSuperclass();
        }
        return false;
    }

    private static Object normalizeControllerLikeTarget(Object candidate, String label, Class<?> danmakuClass, boolean verbose) {
        if (candidate == null) return null;
        Class<?> candidateClass = candidate.getClass();
        Method method = findDanmakuMethod(candidateClass, danmakuClass);
        if (method == null) return null;
        if (isViewOnlyDanmakuTarget(candidateClass) || isWrapperOrAsyncTarget(candidateClass)) return null;
        if (!looksLikePlaybackController(candidateClass, method) && !looksLikeDanmakuController(candidateClass, method)) return null;
        if (verbose) DanmakuSpider.log("反射定位弹幕目标成功: " + label + " -> " + candidateClass.getName());
        return candidate;
    }

    private static boolean looksLikePlaybackController(Class<?> type, Method danmakuMethod) {
        if (type == null || danmakuMethod == null) return false;
        String name = type.getName().toLowerCase();
        if (name.startsWith("defpackage.")) {
            int score = 0;
            Class<?> current = type;
            while (current != null) {
                for (Field field : current.getDeclaredFields()) {
                    Class<?> fieldType = field.getType();
                    String fieldName = field.getName().toLowerCase();
                    String fieldTypeName = fieldType.getName().toLowerCase();
                    if (fieldName.equals("h") || fieldName.equals("d") || fieldName.equals("g") || fieldName.equals("c")) score++;
                    if (fieldTypeName.contains("media3") || fieldTypeName.contains("danmaku") || fieldTypeName.contains("playback")) score += 2;
                    if (fieldTypeName.contains("player") || fieldTypeName.contains("view")) score++;
                }
                current = current.getSuperclass();
            }
            if ("o".equals(danmakuMethod.getName())) score += 2;
            return score >= 3;
        }
        return danmakuMethod.getName().equals("o");
    }

    private static boolean looksLikeDanmakuController(Class<?> type, Method danmakuMethod) {
        if (type == null || danmakuMethod == null) return false;
        String name = type.getName().toLowerCase();
        int score = 0;
        if (name.startsWith("defpackage.")) score++;
        if ("o".equals(danmakuMethod.getName())) score += 2;
        if (findMethod(type, "getRealUrl") != null) score++;
        Class<?> current = type;
        while (current != null) {
            for (Field field : current.getDeclaredFields()) {
                String fieldTypeName = field.getType().getName().toLowerCase();
                String fieldName = field.getName().toLowerCase();
                if (fieldTypeName.contains("danmakuview")) score += 2;
                if (fieldTypeName.contains("ijkvideoview") || fieldTypeName.contains("playerview")) score++;
                if (fieldTypeName.contains("okhttp") || fieldTypeName.contains("request")) score++;
                if (fieldName.equals("g") || fieldName.equals("c") || fieldName.equals("i")) score++;
            }
            current = current.getSuperclass();
        }
        return score >= 3;
    }

    private static boolean isWrapperOrAsyncTarget(Class<?> type) {
        if (type == null) return false;
        String name = type.getName();
        if (name.startsWith("java.util.concurrent.")) return true;
        if (name.startsWith("kotlinx.coroutines.")) return true;
        if (name.startsWith("android.animation.")) return true;
        if (name.startsWith("android.graphics.drawable.")) return true;
        if (name.startsWith("android.view.animation.")) return true;
        if (Runnable.class.isAssignableFrom(type)) return true;
        if (java.util.concurrent.Callable.class.isAssignableFrom(type)) return true;
        if (java.util.concurrent.Executor.class.isAssignableFrom(type)) return true;
        if (java.util.concurrent.Future.class.isAssignableFrom(type)) return true;
        if (android.animation.Animator.class.isAssignableFrom(type)) return true;
        if (android.graphics.drawable.Drawable.class.isAssignableFrom(type)) return true;
        return false;
    }

    private static Object tryResolveDanmakuTargetFromLikelyActivityFields(Activity activity, boolean verbose, Class<?> danmakuClass) {
        if (activity == null) return null;
        try {
            Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
            Class<?> current = activity.getClass();
            while (current != null) {
                for (Field field : current.getDeclaredFields()) {
                    field.setAccessible(true);
                    Object value = field.get(activity);
                    if (value == null) continue;
                    Class<?> valueClass = value.getClass();
                    if (isSimpleValueType(valueClass)) continue;

                    if (isLikelyPlaybackServiceField(field, value)) {
                        Object player = tryResolvePlayerFromService(value, false, danmakuClass);
                        if (player != null) {
                            if (verbose) DanmakuSpider.log("反射定位播放器成功: activity-field-service -> " + current.getName() + "#" + field.getName());
                            return player;
                        }
                    }

                    if (isLikelyDanmakuCarrierField(field, valueClass) || findDanmakuMethod(valueClass, danmakuClass) != null || isFongMiPlayerClass(valueClass)) {
                        Object candidate = tryResolveDanmakuTargetFromObject(value,
                                activity.getClass().getName() + "#" + field.getName(),
                                3,
                                visited,
                                false,
                                danmakuClass);
                        if (candidate != null) {
                            if (verbose) DanmakuSpider.log("反射定位弹幕目标成功: activity-field-scan -> " + current.getName() + "#" + field.getName());
                            return candidate;
                        }
                    }
                }
                current = current.getSuperclass();
            }
        } catch (Throwable e) {
            if (verbose) DanmakuSpider.log("activity 字段扫描失败: " + e.getMessage());
        }
        return null;
    }

    private static Method findAnyCompatibleSingleArgMethod(Class<?> type, Class<?> parameterType) {
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getParameterTypes().length != 1) continue;
                Class<?> argType = method.getParameterTypes()[0];
                if (!argType.isAssignableFrom(parameterType)) continue;
                if (method.getReturnType() != Void.TYPE && method.getReturnType() != type) continue;
                String name = method.getName();
                if (name.startsWith("set") || name.length() <= 2 || name.contains("dan")) {
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<Activity> getAliveActivities() {
        List<Activity> result = new ArrayList<>();
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Object activityThread = activityThreadClass.getMethod("currentActivityThread").invoke(null);
            Field activitiesField = activityThreadClass.getDeclaredField("mActivities");
            activitiesField.setAccessible(true);
            Map<Object, Object> activities;
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.KITKAT) {
                activities = (java.util.HashMap<Object, Object>) activitiesField.get(activityThread);
            } else {
                activities = (android.util.ArrayMap<Object, Object>) activitiesField.get(activityThread);
            }
            for (Object activityRecord : activities.values()) {
                if (activityRecord == null) continue;
                Class<?> recordClass = activityRecord.getClass();
                Field activityField = recordClass.getDeclaredField("activity");
                activityField.setAccessible(true);
                Activity activity = (Activity) activityField.get(activityRecord);
                if (activity == null || activity.isFinishing()) continue;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed()) {
                    continue;
                }
                result.add(activity);
            }
        } catch (Throwable e) {
            DanmakuSpider.log("枚举存活Activity失败: " + e.getMessage());
        }
        return result;
    }

    private static Class<?> findHostClass(ClassLoader loader, Activity activity, String suffix) {
        String[] prefixes = getHostPackagePrefixes(activity);
        for (String prefix : prefixes) {
            if (TextUtils.isEmpty(prefix)) continue;
            try {
                return Class.forName(prefix + "." + suffix, false, loader);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static String[] getHostPackagePrefixes(Activity activity) {
        List<String> prefixes = new ArrayList<>();
        Activity topActivity = Utils.getTopActivity();
        addPackagePrefixes(prefixes, activity);
        if (topActivity != null && topActivity != activity) addPackagePrefixes(prefixes, topActivity);
        if (prefixes.isEmpty()) prefixes.add("com.fongmi.android.tv");
        return prefixes.toArray(new String[0]);
    }

    private static void addPackagePrefixes(List<String> prefixes, Activity activity) {
        if (activity == null) return;
        String name = activity.getClass().getName();
        String[] markers = new String[]{
                ".ui.activity.",
                ".ui.",
                ".activity."
        };
        for (String marker : markers) {
            int index = name.indexOf(marker);
            if (index > 0) {
                String prefix = name.substring(0, index);
                if (!prefixes.contains(prefix)) prefixes.add(prefix);
            }
        }
        Package pkg = activity.getClass().getPackage();
        if (pkg != null) {
            String packageName = pkg.getName();
            if (!prefixes.contains(packageName)) prefixes.add(packageName);
        }
    }

    private static void logObjectShape(String label, Object target, Class<?> danmakuClass, int depth) {
        try {
            Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
            logObjectShapeInternal(label, target, danmakuClass, depth, visited);
        } catch (Throwable e) {
            DanmakuSpider.log("对象结构转储失败(" + label + "): " + e.getMessage());
        }
    }

    private static void logObjectShapeInternal(String label, Object target, Class<?> danmakuClass, int depth, Set<Object> visited) {
        if (target == null || depth < 0 || visited.contains(target)) return;
        visited.add(target);
        Class<?> cls = target.getClass();
        DanmakuSpider.log("结构[" + label + "]: " + cls.getName() +
                " methods{o=" + (findCompatibleMethod(cls, "o", danmakuClass) != null) +
                ",setDanmaku=" + (findCompatibleMethod(cls, "setDanmaku", danmakuClass) != null) + "}");

        Class<?> current = cls;
        while (current != null) {
            for (Field field : current.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(target);
                    if (value == null) continue;
                    Class<?> valueClass = value.getClass();
                    if (isSimpleValueType(valueClass)) continue;
                    DanmakuSpider.log("结构字段[" + label + "]: " + current.getName() + "#" + field.getName() + " -> " + valueClass.getName() +
                            " methods{o=" + (findCompatibleMethod(valueClass, "o", danmakuClass) != null) +
                            ",setDanmaku=" + (findCompatibleMethod(valueClass, "setDanmaku", danmakuClass) != null) + "}");
                    if (depth > 0) {
                        logObjectShapeInternal(label + "." + field.getName(), value, danmakuClass, depth - 1, visited);
                    }
                } catch (Throwable ignored) {
                }
            }
            current = current.getSuperclass();
        }
    }

    // 辅助方法：从XML中解析弹幕总数
    private static int countDanmakuItems(String xmlData) {
        if (TextUtils.isEmpty(xmlData) || !xmlData.trim().startsWith("<")) return 0;
        return DanmakuUtils.countItems(xmlData);
    }
}
