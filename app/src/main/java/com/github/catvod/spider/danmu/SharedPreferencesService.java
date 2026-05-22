package com.github.catvod.spider.danmu;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import com.github.catvod.spider.DanmakuSpider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class SharedPreferencesService {
    private static final String PREF_NAME = "danmaku_search_cache";
    private static final String KEY_PREFIX = "search_keyword_";

    /**
     * 保存搜索关键词缓存
     * 创建映射关系：initialKeyword -> 用户手动输入的关键词
     * @param context 上下文
     * @param initialKeyword 初始关键词（作为缓存的键）
     * @param manualKeyword 用户手动输入的关键词（缓存的值）
     */
    public static void saveSearchKeywordCache(Context context, String initialKeyword, String manualKeyword) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            // 保存映射关系：以initialKeyword作为键，manualKeyword作为值
            String cacheKey = KEY_PREFIX + initialKeyword;
            editor.putString(cacheKey, manualKeyword);
            editor.apply();
            DanmakuSpider.log("保存搜索缓存成功: " + initialKeyword + " -> " + manualKeyword);
        } catch (Exception e) {
            DanmakuSpider.log("保存搜索缓存失败: " + e.getMessage());
        }
    }

    /**
     * 读取搜索关键词缓存
     * 优先返回缓存的手动输入值，如果没有缓存或缓存为空则返回initialKeyword
     * @param context 上下文
     * @param initialKeyword 初始关键词（作为缓存的键）
     * @return 缓存的关键词或initialKeyword
     */
    public static String getSearchKeywordCache(Context context, String initialKeyword) {
        try {
            if (TextUtils.isEmpty(initialKeyword)) {
                return "";
            }

            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            String cacheKey = KEY_PREFIX + initialKeyword;
            // 获取缓存值，如果不存在则使用initialKeyword作为默认值
            String cachedKeyword = prefs.getString(cacheKey, initialKeyword);

            // 如果缓存值为空字符串（表示已清空），返回initialKeyword
            if (TextUtils.isEmpty(cachedKeyword)) {
//                DanmakuSpider.log("读取搜索缓存为空，返回初始值: " + initialKeyword);
                return initialKeyword;
            }

            if (!initialKeyword.equals(cachedKeyword)) {
                DanmakuSpider.log("读取搜索缓存: " + initialKeyword + " -> " + cachedKeyword);
            }
            return cachedKeyword;
        } catch (Exception e) {
            DanmakuSpider.log("读取搜索缓存失败: " + e.getMessage());
            return initialKeyword;
        }
    }

    public static int getSearchKeywordCacheCount(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            Map<String, ?> all = prefs.getAll();
            int count = 0;
            for (String key : all.keySet()) {
                if (key != null && key.startsWith(KEY_PREFIX)) count++;
            }
            return count;
        } catch (Exception e) {
            DanmakuSpider.log("统计搜索缓存失败: " + e.getMessage());
            return 0;
        }
    }

    public static void clearSearchKeywordCache(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            prefs.edit().clear().apply();
            DanmakuSpider.log("搜索关键词缓存已清空");
        } catch (Exception e) {
            DanmakuSpider.log("清空搜索缓存失败: " + e.getMessage());
        }
    }

    public static List<SearchCacheEntry> getSearchKeywordCacheEntries(Context context) {
        List<SearchCacheEntry> entries = new ArrayList<>();
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            Map<String, ?> all = prefs.getAll();
            for (Map.Entry<String, ?> item : all.entrySet()) {
                String key = item.getKey();
                if (key == null || !key.startsWith(KEY_PREFIX)) continue;
                String initialKeyword = key.substring(KEY_PREFIX.length());
                Object value = item.getValue();
                String manualKeyword = value == null ? "" : String.valueOf(value);
                entries.add(new SearchCacheEntry(key, initialKeyword, manualKeyword));
            }
            Collections.sort(entries, new Comparator<SearchCacheEntry>() {
                @Override
                public int compare(SearchCacheEntry o1, SearchCacheEntry o2) {
                    return o1.initialKeyword.compareToIgnoreCase(o2.initialKeyword);
                }
            });
        } catch (Exception e) {
            DanmakuSpider.log("读取搜索缓存列表失败: " + e.getMessage());
        }
        return entries;
    }

    public static void removeSearchKeywordCache(Context context, String rawKey) {
        if (TextUtils.isEmpty(rawKey)) return;
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            prefs.edit().remove(rawKey).apply();
            DanmakuSpider.log("已删除搜索缓存: " + rawKey);
        } catch (Exception e) {
            DanmakuSpider.log("删除搜索缓存失败: " + e.getMessage());
        }
    }

    public static class SearchCacheEntry {
        public final String rawKey;
        public final String initialKeyword;
        public final String manualKeyword;

        public SearchCacheEntry(String rawKey, String initialKeyword, String manualKeyword) {
            this.rawKey = rawKey;
            this.initialKeyword = initialKeyword;
            this.manualKeyword = manualKeyword;
        }
    }
}
