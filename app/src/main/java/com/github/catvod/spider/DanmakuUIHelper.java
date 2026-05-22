package com.github.catvod.spider;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.github.catvod.spider.entity.DanmakuItem;
import com.github.catvod.spider.danmu.SharedPreferencesService;
import com.github.catvod.net.OkHttp;
import okhttp3.Response;

import java.io.InputStream;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class DanmakuUIHelper {

    // 定义统一的颜色常量 - 现代化配色方案
    private static final int PRIMARY_COLOR = 0xFF007AFF;        // 主色调蓝色 - 第一层(页签)
    private static final int PRIMARY_DARK = 0xFF0056B3;         // 深蓝色
    private static final int PRIMARY_LIGHT = 0xFF80BFFF;        // 浅蓝色
    private static final int SECONDARY_COLOR = 0xFFFF9500;      // 橙色 - 第二层(分组)
    private static final int SECONDARY_DARK = 0xFFCC7700;       // 深橙色
    private static final int SECONDARY_LIGHT = 0xFFFFB84D;      // 浅橙色
    private static final int TERTIARY_COLOR = 0xFF34C759;       // 绿色 - 第三层(结果)
    private static final int TERTIARY_DARK = 0xFF2D9D4F;        // 深绿色
    private static final int TERTIARY_LIGHT = 0xFF5ECA7C;       // 浅绿色
    private static final int ACCENT_COLOR = 0xFFFF9500;         // 橙色强调
    private static final int ACCENT_LIGHT = 0xFFFFB84D;         // 浅橙色
    private static final int SUCCESS_COLOR = 0xFF34C759;        // 绿色成功
    private static final int TEXT_PRIMARY = 0xFF1A1A1A;         // 深灰色主文本
    private static final int TEXT_SECONDARY = 0xFF666666;       // 中灰色副文本
    private static final int TEXT_TERTIARY = 0xFF999999;        // 浅灰色辅助文本
    private static final int BACKGROUND_LIGHT = 0xFFF8F9FA;     // 浅灰白背景
    private static final int BACKGROUND_WHITE = 0xFFFFFFFF;     // 纯白背景
    private static final int BORDER_COLOR = 0xFFE0E0E0;         // 边框色
    private static final int BORDER_LIGHT = 0xFFF0F0F0;         // 浅边框色
    private static final int FOCUS_HIGHLIGHT_COLOR = 0xFF80BFFF;// 焦点高亮色
    private static final int SHADOW_COLOR = 0x1A000000;         // 阴影色
    private static final int GRAY_INACTIVE = 0xFFBBBBBB;        // 灰色(未选中状态)

    // ========== 电视端焦点高亮专用颜色 ==========
    private static final int TV_FOCUS_BORDER_COLOR = 0xFF00D4FF;  // 高对比度青色边框 - 电视端焦点
    private static final int TV_FOCUS_BG_COLOR = 0xFF007AFF;      // 焦点背景色 - 蓝色
    private static final int TV_FOCUS_TEXT_COLOR = 0xFFFFFFFF;    // 焦点文字颜色 - 白色
    private static final int TV_FOCUS_GLOW_COLOR = 0xFF00D4FF;    // 发光效果颜色
    private static final int TV_INPUT_FOCUS_BG = 0xFFE3F2FD;      // 输入框焦点背景 - 浅蓝
    private static final int TV_INPUT_FOCUS_BORDER = 0xFF007AFF;  // 输入框焦点边框

    // ========== 深色透明主题颜色 (from DanmakuUIHelper3) ==========
    private static final int DARK_BG_PRIMARY =  0xCC000000;     // 主背景色 - 半透明黑色
    private static final int DARK_BG_SECONDARY = 0x33FFFFFF;   // 次级背景色 - 更透明
    private static final int DARK_BG_TERTIARY = 0x1AFFFFFF;    // 三级背景色 - 更透明
    private static final int DARK_TEXT_PRIMARY = 0xFFFFFFFF;   // 主文本色 - 白色
    private static final int DARK_TEXT_SECONDARY = 0xFFCCCCCC; // 次文本色 - 浅灰色
    private static final int DARK_TEXT_TERTIARY = 0xFF999999;  // 三级文本色 - 中灰色
    private static final int DARK_BORDER = 0x44FFFFFF;         // 边框色 - 浅白透明
    private static final int DARK_HIGHLIGHT = 0xFF007AFF;      // 高亮色 - 蓝色
    private static final int DARK_HIGHLIGHT_DARK = 0xFF0056B3; // 深高亮色 - 深蓝
    private static final int DARK_HIGHLIGHT_LIGHT = 0xFF80BFFF;// 浅高亮色 - 浅蓝
    private static final int DARK_INACTIVE = 0x55444444;       // 非激活状态 - 深灰半透明

    // 功能色（深色透明版本）
    private static final int DARK_PRIMARY_COLOR = 0x99007AFF;        // 页签蓝色
    private static final int DARK_PRIMARY_DARK = 0xCC0056B3;         // 深蓝色
    private static final int DARK_PRIMARY_LIGHT = 0xCC80BFFF;        // 浅蓝色
    private static final int DARK_SECONDARY_COLOR = 0xAAFF9500;      // 分组橙色 (translucent orange)
    private static final int DARK_SECONDARY_DARK = 0xCCCC7700;       // 深橙色 (darker translucent orange)
    private static final int DARK_SECONDARY_LIGHT = 0xCCFFB84D;      // 浅橙色
    private static final int DARK_TERTIARY_COLOR = 0xAA34C759;       // 结果绿色 (translucent green)
    private static final int DARK_TERTIARY_DARK = 0xCC2D9D4F;        // 深绿色 (darker translucent green)
    private static final int DARK_TERTIARY_LIGHT = 0x99BBBBBB;       // 浅绿色
    private static final int DARK_ACCENT_COLOR = 0xAAFF9500;         // 橙色强调
    private static final int DARK_ACCENT_LIGHT = 0xCCFFB84D;         // 浅橙色


    /**
     * 排序状态标记 (false=正序, true=倒序)
     */
    private static boolean isReversed;
    /**
     * 当前选中的标签索引
     */
    private static List<DanmakuItem> currentItems = new ArrayList<>();
    private static String currentSearchKeyword = "";
    private static final int SIMPLE_SOURCE_TAB_LIMIT = 3;
    private static final int SIMPLE_RESULT_COUNT_LIMIT = 36;


    // 显示配置对话框
    public static void showConfigDialog(Context ctx) {
        // 添加检查
        if (!(ctx instanceof Activity)) {
            DanmakuSpider.log("错误：Context不是Activity");
            return;
        }
        Activity activity = (Activity) ctx;
        if (activity.isFinishing() || activity.isDestroyed()) {
            DanmakuSpider.log("Activity已销毁或正在销毁，不显示配置对话框");
            return;
        }

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    // 在创建对话框前再次检查状态
                    if (activity.isFinishing() || activity.isDestroyed()) {
                        DanmakuSpider.log("Activity已销毁，不显示配置对话框");
                        return;
                    }

                    AlertDialog.Builder builder = new AlertDialog.Builder(activity);

                    LinearLayout mainLayout = new LinearLayout(activity);
                    mainLayout.setOrientation(LinearLayout.VERTICAL);
                    mainLayout.setBackgroundColor(BACKGROUND_WHITE);
                    mainLayout.setPadding(dpToPx(activity, 24), dpToPx(activity, 20), dpToPx(activity, 24), dpToPx(activity, 20));

                    // 标题 - 增强视觉效果
                    TextView title = new TextView(activity);
                    title.setText("Leo弹幕配置");
                    title.setTextSize(24);
                    title.setTextColor(PRIMARY_COLOR);
                    title.setGravity(Gravity.CENTER);
                    title.setPadding(0, dpToPx(activity, 8), 0, dpToPx(activity, 20));
                    title.setTypeface(null, android.graphics.Typeface.BOLD);
                    mainLayout.addView(title);

                    // 副标题说明
                    TextView subtitle = new TextView(activity);
                    subtitle.setText("管理弹幕API源和时间偏移");
                    subtitle.setTextSize(13);
                    subtitle.setTextColor(TEXT_SECONDARY);
                    subtitle.setGravity(Gravity.CENTER);
                    subtitle.setPadding(0, 0, 0, dpToPx(activity, 16));
                    mainLayout.addView(subtitle);

                    // IP地址提示 - 改进样式
                    TextView ipInfo = new TextView(activity);
                    String ip = NetworkUtils.getLocalIpAddress();
//                ipInfo.setText("Web配置: http://" + ip + ":9810");
                    ipInfo.setTextSize(13);
                    ipInfo.setTextColor(ACCENT_COLOR);
                    ipInfo.setGravity(Gravity.CENTER);
                    ipInfo.setPadding(dpToPx(activity, 12), dpToPx(activity, 8), dpToPx(activity, 12), dpToPx(activity, 12));
                    ipInfo.setBackgroundColor(0xFFFFF8E1);
                    mainLayout.addView(ipInfo);

                    // API输入框容器 - 改进样式
                    LinearLayout inputContainer = new LinearLayout(activity);
                    inputContainer.setOrientation(LinearLayout.VERTICAL);
                    inputContainer.setBackgroundColor(BORDER_LIGHT);
                    inputContainer.setPadding(dpToPx(activity, 2), dpToPx(activity, 2), dpToPx(activity, 2), dpToPx(activity, 2));
                    LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    containerParams.setMargins(0, dpToPx(activity, 12), 0, dpToPx(activity, 12));
                    inputContainer.setLayoutParams(containerParams);

                    DanmakuConfig config = DanmakuConfigManager.getConfig(activity);

                    EditText apiInput = new EditText(activity);
                    apiInput.setText(TextUtils.join("\n", config.getApiUrlEntries()));
                    apiInput.setHint("每行一个API地址，可用 | 设置别名\n例如: https://example.com/danmu|公益源");
                    apiInput.setMinLines(3);
                    apiInput.setMaxLines(4);
                    apiInput.setBackgroundColor(BACKGROUND_WHITE);
                    apiInput.setTextColor(TEXT_PRIMARY);
                    apiInput.setTextSize(13);
                    apiInput.setPadding(dpToPx(activity, 12), dpToPx(activity, 12), dpToPx(activity, 12), dpToPx(activity, 12));
                    apiInput.setHintTextColor(TEXT_TERTIARY);

                    inputContainer.addView(apiInput);
                    mainLayout.addView(inputContainer);

                    TextView apiSummary = new TextView(activity);
                    apiSummary.setText(buildApiSourceSummary(config));
                    apiSummary.setTextSize(13);
                    apiSummary.setTextColor(TEXT_SECONDARY);
                    apiSummary.setPadding(0, 0, 0, dpToPx(activity, 8));
                    mainLayout.addView(apiSummary);

                    Button apiManagerBtn = createStyledButton(activity, "API源管理", TERTIARY_COLOR);
                    LinearLayout.LayoutParams apiManagerParams = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(activity, 42));
                    apiManagerParams.setMargins(0, 0, 0, dpToPx(activity, 12));
                    apiManagerBtn.setLayoutParams(apiManagerParams);
                    mainLayout.addView(apiManagerBtn);

                    TextView offsetLabel = new TextView(activity);
                    offsetLabel.setText("弹幕时间偏移（秒，负数提前）");
                    offsetLabel.setTextSize(14);
                    offsetLabel.setTextColor(TEXT_PRIMARY);
                    offsetLabel.setPadding(0, dpToPx(activity, 4), 0, dpToPx(activity, 6));
                    mainLayout.addView(offsetLabel);

                    EditText offsetInput = new EditText(activity);
                    offsetInput.setHint("0 表示不偏移，例如 1.5 或 -0.8");
                    offsetInput.setText(DanmakuUtils.formatOffsetSeconds(config.getDanmakuTimeOffsetMs()));
                    offsetInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER |
                            android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL |
                            android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
                    offsetInput.setTextColor(TEXT_PRIMARY);
                    offsetInput.setTextSize(14);
                    offsetInput.setSingleLine(true);
                    offsetInput.setPadding(dpToPx(activity, 12), dpToPx(activity, 10), dpToPx(activity, 12), dpToPx(activity, 10));
                    offsetInput.setHintTextColor(TEXT_TERTIARY);
                    applyTVInputFocusEffect(offsetInput, false);
                    mainLayout.addView(offsetInput, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                    // 分割线 - 改进样式
                    View divider = new View(activity);
                    divider.setBackgroundColor(BORDER_LIGHT);
                    LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(activity, 1));
                    dividerParams.setMargins(0, dpToPx(activity, 16), 0, dpToPx(activity, 16));
                    divider.setLayoutParams(dividerParams);
                    mainLayout.addView(divider);

                    // 按钮布局 - 改进设计
                    LinearLayout btnLayout = new LinearLayout(activity);
                    btnLayout.setOrientation(LinearLayout.HORIZONTAL);
                    btnLayout.setGravity(Gravity.CENTER);

                    Button saveBtn = createStyledButton(activity, "保存", PRIMARY_COLOR);
                    Button lpConfigBtn = createStyledButton(activity, "布局", ACCENT_COLOR);
                    Button cancelBtn = createStyledButtonWithBorder(activity, "取消", PRIMARY_COLOR);

                    LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                            0, dpToPx(activity, 44), 1);
                    btnParams.setMargins(dpToPx(activity, 6), 0, dpToPx(activity, 6), 0);

                    saveBtn.setLayoutParams(btnParams);
                    lpConfigBtn.setLayoutParams(btnParams);
                    cancelBtn.setLayoutParams(btnParams);

                    btnLayout.addView(saveBtn);
                    btnLayout.addView(lpConfigBtn);
                    btnLayout.addView(cancelBtn);

                    mainLayout.addView(btnLayout);

                    builder.setView(mainLayout);
                    AlertDialog dialog = builder.create();
                    dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                            dpToPx(activity, 600)); // 设置固定高度

                    // 按钮事件
                    saveBtn.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            String text = apiInput.getText().toString();
                            String[] lines = text.split("\n");
                            Set<String> newUrls = new LinkedHashSet<>();
                            for (String line : lines) {
                                String trimmed = line.trim();
                                String url = DanmakuApiSource.normalizeUrl(trimmed);
                                if (!TextUtils.isEmpty(url) && (url.startsWith("http://") || url.startsWith("https://"))) {
                                    newUrls.add(trimmed);
                                }
                            }

                            if (newUrls.isEmpty()) {
                                Utils.safeShowToast(activity, "请输入有效的API地址");
                                return;
                            }

                            int offsetMs;
                            try {
                                offsetMs = parseOffsetMs(offsetInput.getText().toString());
                            } catch (NumberFormatException e) {
                                Utils.safeShowToast(activity, "请输入有效的时间偏移");
                                return;
                            }

                            DanmakuConfig config = DanmakuConfigManager.getConfig( activity);
                            int oldOffsetMs = config.getDanmakuTimeOffsetMs();
                            config.setApiUrls(newUrls);
                            config.setDanmakuTimeOffsetMs(offsetMs);
                            DanmakuConfigManager.saveConfig(activity, config);

                            Utils.safeShowToast(activity, "配置已保存");

                            DanmakuSpider.log("已保存API地址: " + newUrls + "，弹幕时间偏移: " + DanmakuUtils.formatOffsetLabel(offsetMs));
                            if (oldOffsetMs != offsetMs) {
                                refreshCurrentDanmaku(activity, offsetMs);
                            }

                            dialog.dismiss();
                        }
                    });

                    lpConfigBtn.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            showLpConfigDialog(activity);
                            dialog.dismiss();
                        }
                    });

                    apiManagerBtn.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            showApiSourceManagerDialog(activity);
                            dialog.dismiss();
                        }
                    });

                    cancelBtn.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            dialog.dismiss();
                        }
                    });

                    safeShowDialog(activity, dialog);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public static void showCacheManagerDialog(Context ctx) {
        if (!(ctx instanceof Activity)) {
            DanmakuSpider.log("错误：Context不是Activity");
            return;
        }
        Activity activity = (Activity) ctx;
        if (activity.isFinishing() || activity.isDestroyed()) {
            DanmakuSpider.log("Activity已销毁或正在销毁，不显示缓存管理对话框");
            return;
        }

        activity.runOnUiThread(() -> {
            try {
                if (activity.isFinishing() || activity.isDestroyed()) {
                    DanmakuSpider.log("Activity已销毁，不显示缓存管理对话框");
                    return;
                }

                AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                LinearLayout mainLayout = new LinearLayout(activity);
                mainLayout.setOrientation(LinearLayout.VERTICAL);
                mainLayout.setBackgroundColor(BACKGROUND_WHITE);
                mainLayout.setPadding(dpToPx(activity, 20), dpToPx(activity, 18), dpToPx(activity, 20), dpToPx(activity, 18));

                TextView title = new TextView(activity);
                title.setText("缓存管理");
                title.setTextSize(24);
                title.setTextColor(PRIMARY_COLOR);
                title.setGravity(Gravity.CENTER);
                title.setTypeface(null, android.graphics.Typeface.BOLD);
                title.setPadding(0, dpToPx(activity, 4), 0, dpToPx(activity, 8));
                mainLayout.addView(title);

                TextView summary = new TextView(activity);
                summary.setTextSize(13);
                summary.setTextColor(TEXT_SECONDARY);
                summary.setPadding(0, 0, 0, dpToPx(activity, 12));
                mainLayout.addView(summary);

                ScrollView scrollView = new ScrollView(activity);
                LinearLayout listLayout = new LinearLayout(activity);
                listLayout.setOrientation(LinearLayout.VERTICAL);
                scrollView.addView(listLayout);
                mainLayout.addView(scrollView, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

                LinearLayout bottomLayout = new LinearLayout(activity);
                bottomLayout.setOrientation(LinearLayout.HORIZONTAL);
                bottomLayout.setGravity(Gravity.CENTER);
                bottomLayout.setPadding(0, dpToPx(activity, 14), 0, 0);

                Button refreshBtn = createStyledButton(activity, "刷新", TERTIARY_COLOR);
                Button clearBtn = createStyledButton(activity, "全部清空", ACCENT_COLOR);
                Button closeBtn = createStyledButtonWithBorder(activity, "关闭", PRIMARY_COLOR);

                LinearLayout.LayoutParams bottomBtnParams = new LinearLayout.LayoutParams(0, dpToPx(activity, 44), 1);
                bottomBtnParams.setMargins(dpToPx(activity, 6), 0, dpToPx(activity, 6), 0);
                bottomLayout.addView(refreshBtn, bottomBtnParams);
                bottomLayout.addView(clearBtn, bottomBtnParams);
                bottomLayout.addView(closeBtn, new LinearLayout.LayoutParams(0, dpToPx(activity, 44), 1));
                mainLayout.addView(bottomLayout);

                AlertDialog dialog = builder.setView(mainLayout).create();

                final Runnable[] refresh = new Runnable[1];
                refresh[0] = new Runnable() {
                    @Override
                    public void run() {
                        DanmakuSpider.CacheStats stats = DanmakuSpider.getCacheStats(activity);
                        summary.setText(buildCacheSummary(stats));
                        renderCacheSections(activity, listLayout, refresh[0]);
                    }
                };

                refreshBtn.setOnClickListener(v -> refresh[0].run());
                clearBtn.setOnClickListener(v -> {
                    try {
                        DanmakuSpider.clearCache(activity);
                        Utils.safeShowToast(activity, "缓存已清空");
                        refresh[0].run();
                    } catch (Exception e) {
                        Utils.safeShowToast(activity, "清空失败");
                    }
                });
                closeBtn.setOnClickListener(v -> dialog.dismiss());

                refresh[0].run();
                safeShowDialog(activity, dialog);
            } catch (Exception e) {
                DanmakuSpider.log("显示缓存管理失败: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    private static String buildCacheSummary(DanmakuSpider.CacheStats stats) {
        return "弹幕文件 " + stats.fileCount + " 个，"
                + "占用 " + formatCacheSize(stats.totalBytes) + "；"
                + "搜索关键词 " + stats.searchCacheCount + " 条；"
                + "运行时缓存 " + DanmakuScanner.getRuntimeCacheCount() + " 条";
    }

    private static String formatCacheSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.getDefault(), "%.2f KB", bytes / 1024f);
        if (bytes < 1024L * 1024L * 1024L) return String.format(Locale.getDefault(), "%.2f MB", bytes / 1024f / 1024f);
        return String.format(Locale.getDefault(), "%.2f GB", bytes / 1024f / 1024f / 1024f);
    }

    private static void renderCacheSections(Activity activity, LinearLayout container, Runnable refresh) {
        container.removeAllViews();

        DanmakuSpider.CacheStats stats = DanmakuSpider.getCacheStats(activity);
        container.addView(createCacheInfoCard(activity, "缓存目录", stats.cachePath));

        List<DanmakuSpider.FileCacheEntry> fileEntries = DanmakuSpider.getFileCacheEntries(activity);
        container.addView(createCacheSectionHeader(activity,
                "弹幕文件缓存",
                fileEntries.isEmpty() ? "暂无缓存文件" : ("共 " + fileEntries.size() + " 个文件，" + formatCacheSize(stats.totalBytes)),
                "清空本组",
                v -> {
                    DanmakuSpider.clearFileCache(activity);
                    Utils.safeShowToast(activity, "弹幕文件缓存已清空");
                    refresh.run();
                }));
        if (fileEntries.isEmpty()) {
            container.addView(createCacheEmptyText(activity, "当前没有弹幕文件缓存"));
        } else {
            for (DanmakuSpider.FileCacheEntry entry : fileEntries) {
                String subtitle = entry.relativePath + "\n" + formatCacheSize(entry.bytes);
                container.addView(createCacheEntryView(activity, "文件", subtitle, "删除", v -> {
                    DanmakuSpider.removeFileCacheEntry(activity, entry.relativePath);
                    Utils.safeShowToast(activity, "已删除缓存文件");
                    refresh.run();
                }));
            }
        }

        List<SharedPreferencesService.SearchCacheEntry> searchEntries = SharedPreferencesService.getSearchKeywordCacheEntries(activity);
        container.addView(createCacheSectionHeader(activity,
                "搜索关键词缓存",
                searchEntries.isEmpty() ? "暂无搜索缓存" : ("共 " + searchEntries.size() + " 条"),
                "清空本组",
                v -> {
                    SharedPreferencesService.clearSearchKeywordCache(activity);
                    Utils.safeShowToast(activity, "搜索关键词缓存已清空");
                    refresh.run();
                }));
        if (searchEntries.isEmpty()) {
            container.addView(createCacheEmptyText(activity, "当前没有搜索关键词缓存"));
        } else {
            for (SharedPreferencesService.SearchCacheEntry entry : searchEntries) {
                String subtitle = "原关键词: " + safeText(entry.initialKeyword)
                        + "\n当前关键词: " + safeText(entry.manualKeyword);
                container.addView(createCacheEntryView(activity, "搜索缓存", subtitle, "删除", v -> {
                    SharedPreferencesService.removeSearchKeywordCache(activity, entry.rawKey);
                    Utils.safeShowToast(activity, "已删除搜索缓存");
                    refresh.run();
                }));
            }
        }

        List<String> runtimeKeys = DanmakuScanner.getRuntimeCacheKeys();
        container.addView(createCacheSectionHeader(activity,
                "运行时缓存",
                runtimeKeys.isEmpty() ? "暂无运行时缓存" : ("共 " + runtimeKeys.size() + " 条"),
                "清空本组",
                v -> {
                    DanmakuScanner.clearRuntimeCache();
                    Utils.safeShowToast(activity, "运行时缓存已清空");
                    refresh.run();
                }));
        if (runtimeKeys.isEmpty()) {
            container.addView(createCacheEmptyText(activity, "当前没有运行时缓存"));
        } else {
            for (String key : runtimeKeys) {
                container.addView(createCacheEntryView(activity, "运行时键", key, "删除", v -> {
                    DanmakuScanner.removeRuntimeCacheKey(key);
                    Utils.safeShowToast(activity, "已删除运行时缓存");
                    refresh.run();
                }));
            }
        }
    }

    private static View createCacheSectionHeader(Activity activity, String titleText, String summaryText, String actionText, View.OnClickListener action) {
        LinearLayout wrapper = new LinearLayout(activity);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setPadding(0, dpToPx(activity, 10), 0, dpToPx(activity, 6));

        TextView title = new TextView(activity);
        title.setText(titleText);
        title.setTextSize(16);
        title.setTextColor(PRIMARY_COLOR);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        wrapper.addView(title);

        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView summary = new TextView(activity);
        summary.setText(summaryText);
        summary.setTextSize(13);
        summary.setTextColor(TEXT_SECONDARY);
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        row.addView(summary, summaryParams);

        Button actionBtn = createStyledButton(activity, actionText, ACCENT_COLOR);
        row.addView(actionBtn, new LinearLayout.LayoutParams(dpToPx(activity, 92), dpToPx(activity, 38)));
        actionBtn.setOnClickListener(action);

        wrapper.addView(row);
        return wrapper;
    }

    private static View createCacheEntryView(Activity activity, String titleText, String bodyText, String actionText, View.OnClickListener action) {
        LinearLayout wrapper = new LinearLayout(activity);
        wrapper.setOrientation(LinearLayout.HORIZONTAL);
        wrapper.setGravity(Gravity.CENTER_VERTICAL);
        wrapper.setBackgroundColor(0xFFF7F7F7);
        wrapper.setPadding(dpToPx(activity, 12), dpToPx(activity, 12), dpToPx(activity, 12), dpToPx(activity, 12));

        LinearLayout.LayoutParams wrapperParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wrapperParams.setMargins(0, 0, 0, dpToPx(activity, 8));
        wrapper.setLayoutParams(wrapperParams);

        LinearLayout textWrap = new LinearLayout(activity);
        textWrap.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);

        TextView title = new TextView(activity);
        title.setText(titleText);
        title.setTextSize(14);
        title.setTextColor(TEXT_PRIMARY);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        textWrap.addView(title);

        TextView body = new TextView(activity);
        body.setText(bodyText);
        body.setTextSize(12);
        body.setTextColor(TEXT_SECONDARY);
        body.setPadding(0, dpToPx(activity, 4), 0, 0);
        textWrap.addView(body);

        wrapper.addView(textWrap, textParams);

        Button actionBtn = createStyledButton(activity, actionText, ACCENT_COLOR);
        wrapper.addView(actionBtn, new LinearLayout.LayoutParams(dpToPx(activity, 76), dpToPx(activity, 38)));
        actionBtn.setOnClickListener(action);
        return wrapper;
    }

    private static View createCacheInfoCard(Activity activity, String titleText, String bodyText) {
        LinearLayout wrapper = new LinearLayout(activity);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setBackgroundColor(0xFFF7F7F7);
        wrapper.setPadding(dpToPx(activity, 12), dpToPx(activity, 12), dpToPx(activity, 12), dpToPx(activity, 12));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dpToPx(activity, 10));
        wrapper.setLayoutParams(params);

        TextView title = new TextView(activity);
        title.setText(titleText);
        title.setTextSize(14);
        title.setTextColor(TEXT_PRIMARY);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        wrapper.addView(title);

        TextView body = new TextView(activity);
        body.setText(bodyText);
        body.setTextSize(12);
        body.setTextColor(TEXT_SECONDARY);
        body.setPadding(0, dpToPx(activity, 4), 0, 0);
        wrapper.addView(body);
        return wrapper;
    }

    private static TextView createCacheEmptyText(Activity activity, String text) {
        TextView empty = new TextView(activity);
        empty.setText(text);
        empty.setTextSize(13);
        empty.setTextColor(TEXT_TERTIARY);
        empty.setPadding(0, 0, 0, dpToPx(activity, 10));
        return empty;
    }

    private static String safeText(String value) {
        return TextUtils.isEmpty(value) ? "空" : value;
    }

    public static void showApiSourceManagerDialog(Context ctx) {
        if (!(ctx instanceof Activity)) {
            DanmakuSpider.log("错误：Context不是Activity");
            return;
        }
        Activity activity = (Activity) ctx;
        if (activity.isFinishing() || activity.isDestroyed()) {
            DanmakuSpider.log("Activity已销毁或正在销毁，不显示API源管理对话框");
            return;
        }

        activity.runOnUiThread(() -> {
            try {
                if (activity.isFinishing() || activity.isDestroyed()) {
                    DanmakuSpider.log("Activity已销毁，不显示API源管理对话框");
                    return;
                }

                AlertDialog.Builder builder = new AlertDialog.Builder(activity);

                LinearLayout mainLayout = new LinearLayout(activity);
                mainLayout.setOrientation(LinearLayout.VERTICAL);
                mainLayout.setBackgroundColor(BACKGROUND_WHITE);
                mainLayout.setPadding(dpToPx(activity, 20), dpToPx(activity, 18), dpToPx(activity, 20), dpToPx(activity, 18));

                TextView title = new TextView(activity);
                title.setText("API源管理");
                title.setTextSize(24);
                title.setTextColor(PRIMARY_COLOR);
                title.setGravity(Gravity.CENTER);
                title.setTypeface(null, android.graphics.Typeface.BOLD);
                title.setPadding(0, dpToPx(activity, 4), 0, dpToPx(activity, 8));
                mainLayout.addView(title);

                TextView summary = new TextView(activity);
                summary.setTextSize(13);
                summary.setTextColor(TEXT_SECONDARY);
                summary.setGravity(Gravity.CENTER);
                summary.setPadding(0, 0, 0, dpToPx(activity, 10));
                mainLayout.addView(summary);

                LinearLayout addLayout = new LinearLayout(activity);
                addLayout.setOrientation(LinearLayout.HORIZONTAL);
                addLayout.setGravity(Gravity.CENTER);
                addLayout.setPadding(0, 0, 0, dpToPx(activity, 10));

                EditText addInput = new EditText(activity);
                addInput.setHint("新增API地址，可写 URL|别名");
                addInput.setSingleLine(true);
                addInput.setTextSize(13);
                addInput.setTextColor(TEXT_PRIMARY);
                addInput.setHintTextColor(TEXT_TERTIARY);
                addInput.setPadding(dpToPx(activity, 12), dpToPx(activity, 10), dpToPx(activity, 12), dpToPx(activity, 10));
                applyTVInputFocusEffect(addInput, false);
                LinearLayout.LayoutParams addInputParams = new LinearLayout.LayoutParams(0, dpToPx(activity, 44), 1);
                addInputParams.setMargins(0, 0, dpToPx(activity, 8), 0);
                addLayout.addView(addInput, addInputParams);

                Button addBtn = createStyledButton(activity, "新增", PRIMARY_COLOR);
                addLayout.addView(addBtn, new LinearLayout.LayoutParams(dpToPx(activity, 76), dpToPx(activity, 44)));
                mainLayout.addView(addLayout);

                ScrollView scrollView = new ScrollView(activity);
                LinearLayout listLayout = new LinearLayout(activity);
                listLayout.setOrientation(LinearLayout.VERTICAL);
                scrollView.addView(listLayout);
                LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
                mainLayout.addView(scrollView, scrollParams);

                LinearLayout bottomLayout = new LinearLayout(activity);
                bottomLayout.setOrientation(LinearLayout.HORIZONTAL);
                bottomLayout.setGravity(Gravity.CENTER);
                bottomLayout.setPadding(0, dpToPx(activity, 12), 0, 0);

                Button testAllBtn = createStyledButton(activity, "全部测试", TERTIARY_COLOR);
                Button closeBtn = createStyledButtonWithBorder(activity, "关闭", PRIMARY_COLOR);
                LinearLayout.LayoutParams bottomBtnParams = new LinearLayout.LayoutParams(0, dpToPx(activity, 44), 1);
                bottomBtnParams.setMargins(dpToPx(activity, 6), 0, dpToPx(activity, 6), 0);
                bottomLayout.addView(testAllBtn, bottomBtnParams);
                bottomLayout.addView(closeBtn, new LinearLayout.LayoutParams(0, dpToPx(activity, 44), 1));
                mainLayout.addView(bottomLayout);

                builder.setView(mainLayout);
                AlertDialog dialog = builder.create();

                final Runnable[] refresh = new Runnable[1];
                refresh[0] = new Runnable() {
                    @Override
                    public void run() {
                        DanmakuConfig latest = DanmakuConfigManager.getConfig(activity);
                        summary.setText(buildApiSourceSummary(latest));
                        renderApiSourceList(activity, listLayout, latest, refresh[0]);
                    }
                };

                addBtn.setOnClickListener(v -> {
                    String entry = addInput.getText().toString();
                    String url = DanmakuApiSource.parseEntry(entry).url;
                    if (TextUtils.isEmpty(url) || !(url.startsWith("http://") || url.startsWith("https://"))) {
                        Utils.safeShowToast(activity, "请输入有效的API地址");
                        return;
                    }

                    DanmakuConfig config = DanmakuConfigManager.getConfig(activity);
                    if (config.findApiSource(url) != null) {
                        Utils.safeShowToast(activity, "API源已存在");
                        return;
                    }
                    config.addApiUrls(java.util.Collections.singletonList(entry));
                    DanmakuConfigManager.saveConfig(activity, config);
                    addInput.setText("");
                    Utils.safeShowToast(activity, "API源已新增");
                    refresh[0].run();
                });

                testAllBtn.setOnClickListener(v -> testAllApiSources(activity, testAllBtn, refresh[0]));
                closeBtn.setOnClickListener(v -> dialog.dismiss());

                refresh[0].run();
                safeShowDialog(activity, dialog);
            } catch (Exception e) {
                DanmakuSpider.log("显示API源管理失败: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    private static void renderApiSourceList(Activity activity, LinearLayout listLayout, DanmakuConfig config, Runnable refresh) {
        listLayout.removeAllViews();
        List<DanmakuApiSource> sources = config.getApiSources();
        if (sources.isEmpty()) {
            TextView empty = new TextView(activity);
            empty.setText("暂无API源");
            empty.setTextSize(14);
            empty.setTextColor(TEXT_TERTIARY);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dpToPx(activity, 24), 0, dpToPx(activity, 24));
            listLayout.addView(empty);
            return;
        }

        for (int i = 0; i < sources.size(); i++) {
            DanmakuApiSource source = sources.get(i);
            final String sourceUrl = source.url;

            LinearLayout itemLayout = new LinearLayout(activity);
            itemLayout.setOrientation(LinearLayout.VERTICAL);
            itemLayout.setPadding(dpToPx(activity, 12), dpToPx(activity, 10), dpToPx(activity, 12), dpToPx(activity, 10));
            itemLayout.setBackground(createRoundedBackgroundDrawable(BACKGROUND_LIGHT));
            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            itemParams.setMargins(0, 0, 0, dpToPx(activity, 10));
            listLayout.addView(itemLayout, itemParams);

            TextView urlView = new TextView(activity);
            String sourceLabel = source.getDisplayName("源" + (i + 1));
            String sourceTitle = (i + 1) + ". " + sourceLabel;
            if (!TextUtils.isEmpty(source.name)) {
                sourceTitle += "\n" + sourceUrl;
            } else {
                sourceTitle += "  " + sourceUrl;
            }
            urlView.setText(sourceTitle);
            urlView.setTextSize(14);
            urlView.setTextColor(TEXT_PRIMARY);
            urlView.setSingleLine(false);
            itemLayout.addView(urlView);

            TextView statusView = new TextView(activity);
            statusView.setText(formatApiSourceStatus(source));
            statusView.setTextSize(12);
            statusView.setTextColor(source.enabled ? TEXT_SECONDARY : TEXT_TERTIARY);
            statusView.setPadding(0, dpToPx(activity, 6), 0, dpToPx(activity, 8));
            itemLayout.addView(statusView);

            LinearLayout actionLayout = new LinearLayout(activity);
            actionLayout.setOrientation(LinearLayout.HORIZONTAL);
            actionLayout.setGravity(Gravity.CENTER);

            Button toggleBtn = createStyledButton(activity, source.enabled ? "停用" : "启用",
                    source.enabled ? ACCENT_COLOR : TERTIARY_COLOR);
            Button aliasBtn = createStyledButtonWithBorder(activity, "别名", PRIMARY_COLOR);
            Button testBtn = createStyledButton(activity, "测试", PRIMARY_COLOR);
            Button deleteBtn = createStyledButtonWithBorder(activity, "删除", ACCENT_COLOR);

            actionLayout.addView(toggleBtn, createSourceButtonParams(activity));
            actionLayout.addView(aliasBtn, createSourceButtonParams(activity));
            actionLayout.addView(testBtn, createSourceButtonParams(activity));
            actionLayout.addView(deleteBtn, createSourceButtonParams(activity));
            itemLayout.addView(actionLayout);

            toggleBtn.setOnClickListener(v -> {
                DanmakuConfig latest = DanmakuConfigManager.getConfig(activity);
                DanmakuApiSource latestSource = latest.findApiSource(sourceUrl);
                if (latestSource != null) {
                    latestSource.enabled = !latestSource.enabled;
                    DanmakuConfigManager.saveConfig(activity, latest);
                    refresh.run();
                }
            });

            testBtn.setOnClickListener(v -> testApiSource(activity, sourceUrl, testBtn, refresh));
            aliasBtn.setOnClickListener(v -> showApiSourceAliasDialog(activity, sourceUrl, refresh));

            deleteBtn.setOnClickListener(v -> {
                DanmakuConfig latest = DanmakuConfigManager.getConfig(activity);
                latest.removeApiSource(sourceUrl);
                DanmakuConfigManager.saveConfig(activity, latest);
                Utils.safeShowToast(activity, "API源已删除");
                refresh.run();
            });
        }
    }

    private static void showApiSourceAliasDialog(Activity activity, String sourceUrl, Runnable refresh) {
        DanmakuConfig config = DanmakuConfigManager.getConfig(activity);
        DanmakuApiSource source = config.findApiSource(sourceUrl);
        if (source == null) {
            Utils.safeShowToast(activity, "API源不存在");
            return;
        }

        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dpToPx(activity, 20), dpToPx(activity, 16), dpToPx(activity, 20), dpToPx(activity, 8));

        TextView urlView = new TextView(activity);
        urlView.setText(source.url);
        urlView.setTextSize(12);
        urlView.setTextColor(TEXT_SECONDARY);
        urlView.setPadding(0, 0, 0, dpToPx(activity, 10));
        layout.addView(urlView);

        EditText aliasInput = new EditText(activity);
        aliasInput.setHint("别名，例如 公益源");
        aliasInput.setSingleLine(true);
        aliasInput.setText(DanmakuApiSource.normalizeName(source.name));
        aliasInput.setTextSize(14);
        aliasInput.setTextColor(TEXT_PRIMARY);
        aliasInput.setHintTextColor(TEXT_TERTIARY);
        aliasInput.setPadding(dpToPx(activity, 12), dpToPx(activity, 10), dpToPx(activity, 12), dpToPx(activity, 10));
        applyTVInputFocusEffect(aliasInput, false);
        layout.addView(aliasInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("设置API源别名")
                .setView(layout)
                .setPositiveButton("保存", null)
                .setNegativeButton("取消", null)
                .setNeutralButton("清空", null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                DanmakuConfig latest = DanmakuConfigManager.getConfig(activity);
                latest.setApiSourceName(sourceUrl, aliasInput.getText().toString());
                DanmakuConfigManager.saveConfig(activity, latest);
                Utils.safeShowToast(activity, "别名已保存");
                refresh.run();
                dialog.dismiss();
            });

            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                DanmakuConfig latest = DanmakuConfigManager.getConfig(activity);
                latest.setApiSourceName(sourceUrl, "");
                DanmakuConfigManager.saveConfig(activity, latest);
                Utils.safeShowToast(activity, "别名已清空");
                refresh.run();
                dialog.dismiss();
            });
        });

        safeShowDialog(activity, dialog);
    }

    private static LinearLayout.LayoutParams createSourceButtonParams(Activity activity) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dpToPx(activity, 38), 1);
        params.setMargins(dpToPx(activity, 3), 0, dpToPx(activity, 3), 0);
        return params;
    }

    private static void testApiSource(Activity activity, String sourceUrl, Button button, Runnable refresh) {
        button.setEnabled(false);
        button.setText("测试中");
        new Thread(() -> {
            LeoDanmakuService.ApiSourceTestResult result = LeoDanmakuService.testApiSource(sourceUrl);
            DanmakuConfigManager.recordApiSourceResult(activity.getApplicationContext(), sourceUrl,
                    result.success, result.latencyMs, result.message);
            DanmakuSpider.log("API源测试 " + (result.success ? "成功" : "失败") + ": " + sourceUrl + " - " + result.message);

            activity.runOnUiThread(() -> {
                if (!activity.isFinishing() && !activity.isDestroyed()) {
                    Utils.safeShowToast(activity, result.success ? "测试成功: " + result.latencyMs + "ms" : "测试失败: " + result.message);
                    refresh.run();
                }
            });
        }).start();
    }

    private static void testAllApiSources(Activity activity, Button button, Runnable refresh) {
        DanmakuConfig config = DanmakuConfigManager.getConfig(activity);
        List<DanmakuApiSource> sources = new ArrayList<>(config.getApiSources());
        if (sources.isEmpty()) {
            Utils.safeShowToast(activity, "暂无API源");
            return;
        }

        button.setEnabled(false);
        button.setText("测试中");
        new Thread(() -> {
            int successCount = 0;
            for (DanmakuApiSource source : sources) {
                LeoDanmakuService.ApiSourceTestResult result = LeoDanmakuService.testApiSource(source.url);
                if (result.success) successCount++;
                DanmakuConfigManager.recordApiSourceResult(activity.getApplicationContext(), source.url,
                        result.success, result.latencyMs, result.message);
            }
            final int finalSuccessCount = successCount;
            activity.runOnUiThread(() -> {
                if (!activity.isFinishing() && !activity.isDestroyed()) {
                    button.setEnabled(true);
                    button.setText("全部测试");
                    Utils.safeShowToast(activity, "测试完成: " + finalSuccessCount + "/" + sources.size() + " 可用");
                    refresh.run();
                }
            });
        }).start();
    }

    private static String buildApiSourceSummary(DanmakuConfig config) {
        if (config == null) return "未配置API源";
        List<DanmakuApiSource> sources = config.getApiSources();
        List<DanmakuApiSource> enabledSources = config.getEnabledApiSources();
        if (sources.isEmpty()) return "未配置API源";
        if (enabledSources.isEmpty()) return "已配置 " + sources.size() + " 个API源，当前全部停用";
        DanmakuApiSource first = enabledSources.get(0);
        String firstName = first.getDisplayName(shortenUrl(first.url));
        return "已启用 " + enabledSources.size() + "/" + sources.size() + "，例如：" + firstName;
    }

    private static String formatApiSourceStatus(DanmakuApiSource source) {
        String state = source.enabled ? "启用" : "停用";
        String latency = source.lastLatencyMs >= 0 ? source.lastLatencyMs + "ms" : "未测试";
        String successTime = source.lastSuccessTimeMs > 0 ? formatTime(source.lastSuccessTimeMs) : "未成功";
        String error = TextUtils.isEmpty(source.lastError) ? "" : " | " + source.lastError;
        return state + " | 延迟 " + latency + " | 最近成功 " + successTime + error;
    }

    private static String formatTime(long timeMs) {
        if (timeMs <= 0) return "未记录";
        return new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(new Date(timeMs));
    }

    private static String shortenUrl(String url) {
        if (TextUtils.isEmpty(url) || url.length() <= 42) return url;
        return url.substring(0, 39) + "...";
    }

    public static void showLpConfigDialog(Context ctx) {
        if (!(ctx instanceof Activity)) {
            DanmakuSpider.log("错误：Context不是Activity");
            return;
        }
        Activity activity = (Activity) ctx;
        if (activity.isFinishing() || activity.isDestroyed()) {
            DanmakuSpider.log("Activity已销毁或正在销毁，不显示配置对话框");
            return;
        }

        activity.runOnUiThread(() -> {
            try {
                if (activity.isFinishing() || activity.isDestroyed()) {
                    DanmakuSpider.log("Activity已销毁，不显示配置对话框");
                    return;
                }

                AlertDialog.Builder builder = new AlertDialog.Builder(activity);

                LinearLayout mainLayout = new LinearLayout(activity);
                mainLayout.setOrientation(LinearLayout.VERTICAL);
                mainLayout.setBackgroundColor(BACKGROUND_WHITE);
                mainLayout.setPadding(dpToPx(activity, 24), dpToPx(activity, 20), dpToPx(activity, 24), dpToPx(activity, 20));

                TextView title = new TextView(activity);
                title.setText("布局配置");
                title.setTextSize(24);
                title.setTextColor(PRIMARY_COLOR);
                title.setGravity(Gravity.CENTER);
                title.setPadding(0, dpToPx(activity, 8), 0, dpToPx(activity, 20));
                title.setTypeface(null, android.graphics.Typeface.BOLD);
                mainLayout.addView(title);

                DanmakuConfig config = DanmakuConfigManager.getConfig(activity);

                // 宽度配置行
                LinearLayout widthLayout = new LinearLayout(activity);
                widthLayout.setOrientation(LinearLayout.HORIZONTAL);
                widthLayout.setGravity(Gravity.CENTER_VERTICAL);

                TextView widthLabel = new TextView(activity);
                widthLabel.setText("宽度:");
                widthLabel.setTextSize(14);
                widthLabel.setTextColor(TEXT_PRIMARY);
                widthLabel.setPadding(0, 0, dpToPx(activity, 10), 0);

                EditText widthInput = new EditText(activity);
                widthInput.setHint("0.1 - 1.0");
                widthInput.setText(String.valueOf(config.getLpWidth()));
                widthInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER |
                        android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);

                widthLayout.addView(widthLabel);
                widthLayout.addView(widthInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                mainLayout.addView(widthLayout);

                // 高度配置行
                LinearLayout heightLayout = new LinearLayout(activity);
                heightLayout.setOrientation(LinearLayout.HORIZONTAL);
                heightLayout.setGravity(Gravity.CENTER_VERTICAL);

                TextView heightLabel = new TextView(activity);
                heightLabel.setText("高度:");
                heightLabel.setTextSize(14);
                heightLabel.setTextColor(TEXT_PRIMARY);
                heightLabel.setPadding(0, 0, dpToPx(activity, 10), 0);

                EditText heightInput = new EditText(activity);
                heightInput.setHint("0.1 - 1.0");
                heightInput.setText(String.valueOf(config.getLpHeight()));
                heightInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER |
                        android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);

                heightLayout.addView(heightLabel);
                heightLayout.addView(heightInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                mainLayout.addView(heightLayout);

                // 透明度配置行
                LinearLayout alphaLayout = new LinearLayout(activity);
                alphaLayout.setOrientation(LinearLayout.HORIZONTAL);
                alphaLayout.setGravity(Gravity.CENTER_VERTICAL);

                TextView alphaLabel = new TextView(activity);
                alphaLabel.setText("透明度:");
                alphaLabel.setTextSize(14);
                alphaLabel.setTextColor(TEXT_PRIMARY);
                alphaLabel.setPadding(0, 0, dpToPx(activity, 10), 0);

                EditText alphaInput = new EditText(activity);
                alphaInput.setHint("0.1 - 1.0");
                alphaInput.setText(String.valueOf(config.getLpAlpha()));
                alphaInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER |
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);

                alphaLayout.addView(alphaLabel);
                alphaLayout.addView(alphaInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                mainLayout.addView(alphaLayout);


                LinearLayout btnLayout = new LinearLayout(activity);
                btnLayout.setOrientation(LinearLayout.HORIZONTAL);
                btnLayout.setGravity(Gravity.CENTER);

                Button saveBtn = createStyledButton(activity, "保存", PRIMARY_COLOR);
                Button cancelBtn = createStyledButtonWithBorder(activity, "取消", PRIMARY_COLOR);

                LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                        0, dpToPx(activity, 44), 1);
                btnParams.setMargins(dpToPx(activity, 6), 0, dpToPx(activity, 6), 0);

                saveBtn.setLayoutParams(btnParams);
                cancelBtn.setLayoutParams(btnParams);

                btnLayout.addView(saveBtn);
                btnLayout.addView(cancelBtn);

                mainLayout.addView(btnLayout);

                builder.setView(mainLayout);
                AlertDialog dialog = builder.create();

                saveBtn.setOnClickListener(v -> {
                    try {
                        float width = Float.parseFloat(widthInput.getText().toString());
                        float height = Float.parseFloat(heightInput.getText().toString());
                        float alpha = Float.parseFloat(alphaInput.getText().toString());

                        if (width > 1.0f) width = 1.0f;
                        if (width < 0.1f) width = 0.1f;
                        if (height > 1.0f) height = 1.0f;
                        if (height < 0.1f) height = 0.1f;
                        if (alpha > 1.0f) alpha = 1.0f;
                        if (alpha < 0.1f) alpha = 0.1f;

                        config.setLpWidth(width);
                        config.setLpHeight(height);
                        config.setLpAlpha(alpha);
                        DanmakuConfigManager.saveConfig(activity, config);
                        Utils.safeShowToast(activity, "布局配置已保存");
                        dialog.dismiss();
                    } catch (NumberFormatException e) {
                        Utils.safeShowToast(activity, "请输入有效的数字");
                    }
                });

                cancelBtn.setOnClickListener(v -> dialog.dismiss());

                safeShowDialog(activity, dialog);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public static void showDanmakuOffsetDialog(Context ctx) {
        if (!(ctx instanceof Activity)) {
            DanmakuSpider.log("错误：Context不是Activity");
            return;
        }
        Activity activity = (Activity) ctx;
        if (activity.isFinishing() || activity.isDestroyed()) {
            DanmakuSpider.log("Activity已销毁或正在销毁，不显示配置对话框");
            return;
        }

        activity.runOnUiThread(() -> {
            try {
                if (activity.isFinishing() || activity.isDestroyed()) {
                    DanmakuSpider.log("Activity已销毁，不显示配置对话框");
                    return;
                }

                AlertDialog.Builder builder = new AlertDialog.Builder(activity);

                LinearLayout mainLayout = new LinearLayout(activity);
                mainLayout.setOrientation(LinearLayout.VERTICAL);
                mainLayout.setBackgroundColor(BACKGROUND_WHITE);
                mainLayout.setPadding(dpToPx(activity, 24), dpToPx(activity, 20), dpToPx(activity, 24), dpToPx(activity, 20));

                TextView title = new TextView(activity);
                title.setText("弹幕时间偏移");
                title.setTextSize(24);
                title.setTextColor(PRIMARY_COLOR);
                title.setGravity(Gravity.CENTER);
                title.setPadding(0, dpToPx(activity, 8), 0, dpToPx(activity, 12));
                title.setTypeface(null, android.graphics.Typeface.BOLD);
                mainLayout.addView(title);

                TextView subtitle = new TextView(activity);
                subtitle.setText("正数延后，负数提前，单位秒");
                subtitle.setTextSize(13);
                subtitle.setTextColor(TEXT_SECONDARY);
                subtitle.setGravity(Gravity.CENTER);
                subtitle.setPadding(0, 0, 0, dpToPx(activity, 16));
                mainLayout.addView(subtitle);

                DanmakuConfig config = DanmakuConfigManager.getConfig(activity);

                EditText offsetInput = new EditText(activity);
                offsetInput.setHint("例如 1.5 或 -0.8");
                offsetInput.setText(DanmakuUtils.formatOffsetSeconds(config.getDanmakuTimeOffsetMs()));
                offsetInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER |
                        android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL |
                        android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
                offsetInput.setTextColor(TEXT_PRIMARY);
                offsetInput.setTextSize(16);
                offsetInput.setSingleLine(true);
                offsetInput.setGravity(Gravity.CENTER);
                offsetInput.setPadding(dpToPx(activity, 12), dpToPx(activity, 12), dpToPx(activity, 12), dpToPx(activity, 12));
                offsetInput.setHintTextColor(TEXT_TERTIARY);
                applyTVInputFocusEffect(offsetInput, false);
                mainLayout.addView(offsetInput, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                TextView current = new TextView(activity);
                current.setText("当前：" + DanmakuUtils.formatOffsetLabel(config.getDanmakuTimeOffsetMs()));
                current.setTextSize(13);
                current.setTextColor(TEXT_SECONDARY);
                current.setGravity(Gravity.CENTER);
                current.setPadding(0, dpToPx(activity, 12), 0, dpToPx(activity, 16));
                mainLayout.addView(current);

                LinearLayout btnLayout = new LinearLayout(activity);
                btnLayout.setOrientation(LinearLayout.HORIZONTAL);
                btnLayout.setGravity(Gravity.CENTER);

                Button saveBtn = createStyledButton(activity, "保存", PRIMARY_COLOR);
                Button resetBtn = createStyledButton(activity, "重置", ACCENT_COLOR);
                Button cancelBtn = createStyledButtonWithBorder(activity, "取消", PRIMARY_COLOR);

                LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                        0, dpToPx(activity, 44), 1);
                btnParams.setMargins(dpToPx(activity, 6), 0, dpToPx(activity, 6), 0);

                saveBtn.setLayoutParams(btnParams);
                resetBtn.setLayoutParams(btnParams);
                cancelBtn.setLayoutParams(btnParams);

                btnLayout.addView(saveBtn);
                btnLayout.addView(resetBtn);
                btnLayout.addView(cancelBtn);
                mainLayout.addView(btnLayout);

                builder.setView(mainLayout);
                AlertDialog dialog = builder.create();

                saveBtn.setOnClickListener(v -> {
                    try {
                        int offsetMs = parseOffsetMs(offsetInput.getText().toString());
                        int oldOffsetMs = config.getDanmakuTimeOffsetMs();
                        config.setDanmakuTimeOffsetMs(offsetMs);
                        DanmakuConfigManager.saveConfig(activity, config);
                        Utils.safeShowToast(activity, "弹幕时间偏移已保存：" + DanmakuUtils.formatOffsetLabel(offsetMs));
                        DanmakuSpider.log("弹幕时间偏移已保存：" + DanmakuUtils.formatOffsetLabel(offsetMs));
                        if (oldOffsetMs != offsetMs) {
                            refreshCurrentDanmaku(activity, offsetMs);
                        }
                        dialog.dismiss();
                    } catch (NumberFormatException e) {
                        Utils.safeShowToast(activity, "请输入有效的时间偏移");
                    }
                });

                resetBtn.setOnClickListener(v -> {
                    int oldOffsetMs = config.getDanmakuTimeOffsetMs();
                    config.setDanmakuTimeOffsetMs(0);
                    DanmakuConfigManager.saveConfig(activity, config);
                    Utils.safeShowToast(activity, "弹幕时间偏移已重置");
                    DanmakuSpider.log("弹幕时间偏移已重置");
                    if (oldOffsetMs != 0) {
                        refreshCurrentDanmaku(activity, 0);
                    }
                    dialog.dismiss();
                });

                cancelBtn.setOnClickListener(v -> dialog.dismiss());

                safeShowDialog(activity, dialog);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public static void showDanmakuStyleDialog(Context ctx) {
        if (!(ctx instanceof Activity)) {
            DanmakuSpider.log("错误：Context不是Activity");
            return;
        }
        Activity activity = (Activity) ctx;
        if (activity.isFinishing() || activity.isDestroyed()) {
            DanmakuSpider.log("Activity已销毁或正在销毁，不显示配置对话框");
            return;
        }

        activity.runOnUiThread(() -> {
            try {
                if (activity.isFinishing() || activity.isDestroyed()) {
                    DanmakuSpider.log("Activity已销毁，不显示配置对话框");
                    return;
                }

                AlertDialog.Builder builder = new AlertDialog.Builder(activity);

                LinearLayout mainLayout = new LinearLayout(activity);
                mainLayout.setOrientation(LinearLayout.VERTICAL);
                mainLayout.setBackgroundColor(BACKGROUND_WHITE);
                mainLayout.setPadding(dpToPx(activity, 24), dpToPx(activity, 20), dpToPx(activity, 24), dpToPx(activity, 20));

                TextView title = new TextView(activity);
                title.setText("弹幕交互模式");
                title.setTextSize(24);
                title.setTextColor(PRIMARY_COLOR);
                title.setGravity(Gravity.CENTER);
                title.setPadding(0, dpToPx(activity, 8), 0, dpToPx(activity, 20));
                title.setTypeface(null, android.graphics.Typeface.BOLD);
                mainLayout.addView(title);

                DanmakuConfig config = DanmakuConfigManager.getConfig(activity);
                String[] styles = DanmakuConfig.STYLE_OPTIONS;
                String currentStyle = config.getDanmakuStyle();
                
                AlertDialog dialog = builder.create();

                for (String style : styles) {
                    Button styleBtn = createStyledButton(activity, buildDanmakuStyleOptionText(style),
                            style.equals(currentStyle) ? PRIMARY_COLOR : GRAY_INACTIVE);
                    LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(activity, 44));
                    btnParams.setMargins(0, 0, 0, dpToPx(activity, 10));
                    styleBtn.setLayoutParams(btnParams);
                    mainLayout.addView(styleBtn);

                    styleBtn.setOnClickListener(v -> {
                        config.setDanmakuStyle(style);
                        DanmakuConfigManager.saveConfig(activity, config);
                        Utils.safeShowToast(activity, "弹幕交互模式已切换为: " + config.getDanmakuStyleDisplayName());
                        dialog.dismiss();
                    });
                }

                builder.setView(mainLayout);
                dialog.setView(mainLayout);
                safeShowDialog(activity, dialog);

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private static String buildDanmakuStyleOptionText(String style) {
        if (DanmakuConfig.STYLE_CLASSIC.equals(style)) return style + "（原模板一）";
        if (DanmakuConfig.STYLE_GRID.equals(style)) return style + "（原模板二）";
        if (DanmakuConfig.STYLE_DARK_GRID.equals(style)) return style + "（原模板三）";
        if (DanmakuConfig.STYLE_MODERN_PANEL.equals(style)) return style + "（原模板四）";
        return DanmakuConfig.normalizeDanmakuStyle(style);
    }


    private static int parseOffsetMs(String text) throws NumberFormatException {
        if (TextUtils.isEmpty(text) || TextUtils.isEmpty(text.trim())) return 0;
        double seconds = Double.parseDouble(text.trim());
        if (seconds > 600) seconds = 600;
        if (seconds < -600) seconds = -600;
        return (int) Math.round(seconds * 1000);
    }

    private static void refreshCurrentDanmaku(Activity activity, int offsetMs) {
        DanmakuItem item = DanmakuManager.getLastDanmakuItem();
        if (item == null) {
            DanmakuSpider.log("弹幕时间偏移已更新；当前没有可重推的弹幕，下一次选择/自动推送时生效");
            return;
        }

        DanmakuSpider.log("弹幕时间偏移变更，刷新当前弹幕: " + item.getTitleWithEp() + " -> " + DanmakuUtils.formatOffsetLabel(offsetMs));
        LeoDanmakuService.pushDanmakuDirect(item, activity, false, true);
    }

    // 创建带边框的按钮 - 电视端优化版本
    private static Button createStyledButtonWithBorder(Activity activity, String text, int color) {
        Button button = new Button(activity);
        button.setText(text);
        button.setTextColor(color);
        button.setBackground(createTVFocusableBorderDrawable(color, false));
        button.setTextSize(14);
        button.setTypeface(null, android.graphics.Typeface.BOLD);
        // 启用焦点支持电视端
        button.setFocusable(true);
        button.setFocusableInTouchMode(true);

        // 添加电视端焦点效果 - 高对比度发光边框
        button.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    // 获得焦点时：发光边框 + 填充背景 + 白色文字
                    ((Button) v).setBackground(createTVFocusableBorderDrawable(TV_FOCUS_BORDER_COLOR, true));
                    ((Button) v).setTextColor(TV_FOCUS_TEXT_COLOR);
                } else {
                    // 失去焦点时恢复边框样式
                    ((Button) v).setBackground(createTVFocusableBorderDrawable(color, false));
                    ((Button) v).setTextColor(color);
                }
            }
        });

        return button;
    }

    // 创建实心按钮 - 电视端优化版本（带明显焦点高亮）
    private static Button createStyledButton(Activity activity, String text, int backgroundColor) {
        Button button = new Button(activity);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setBackground(createTVFocusableSolidDrawable(backgroundColor, false));
        button.setTextSize(14);
        button.setTypeface(null, android.graphics.Typeface.BOLD);
        // 启用焦点支持电视端
        button.setFocusable(true);
        button.setFocusableInTouchMode(true);

        // 添加电视端焦点效果 - 高对比度发光边框
        button.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    // 获得焦点时：加粗发光边框 + 高亮背景
                    ((Button) v).setBackground(createTVFocusableSolidDrawable(backgroundColor, true));
                    ((Button) v).setTextColor(TV_FOCUS_TEXT_COLOR);
                } else {
                    // 失去焦点时恢复原始样式
                    ((Button) v).setBackground(createTVFocusableSolidDrawable(backgroundColor, false));
                    ((Button) v).setTextColor(Color.WHITE);
                }
            }
        });

        return button;
    }

    // ========== 电视端焦点高亮专用Drawable方法 ==========

    /**
     * 创建电视端可聚焦的边框按钮背景
     * @param color 边框颜色
     * @param focused 是否为焦点状态
     */
    private static android.graphics.drawable.Drawable createTVFocusableBorderDrawable(int color, boolean focused) {
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        if (focused) {
            // 焦点状态：填充背景 + 加粗发光边框
            drawable.setColor(TV_FOCUS_BG_COLOR);
            drawable.setStroke(6, TV_FOCUS_BORDER_COLOR); // 6px加粗边框，高对比度
            drawable.setCornerRadius(12);
        } else {
            // 非焦点状态：透明背景 + 细边框
            drawable.setColor(Color.TRANSPARENT);
            drawable.setStroke(2, color);
            drawable.setCornerRadius(12);
        }
        return drawable;
    }

    /**
     * 创建电视端可聚焦的实心按钮背景
     * @param color 背景颜色
     * @param focused 是否为焦点状态
     */
    private static android.graphics.drawable.Drawable createTVFocusableSolidDrawable(int color, boolean focused) {
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(12);
        if (focused) {
            // 焦点状态：加粗发光边框，背景色稍微变亮
            drawable.setStroke(6, TV_FOCUS_BORDER_COLOR);
            // 背景色稍微提亮，增强对比度
            drawable.setColor(lightenColor(color, 0.15f));
        } else {
            drawable.setStroke(0, 0);
        }
        return drawable;
    }

    /**
     * 创建电视端可聚焦的透明按钮背景（用于深色主题）
     * @param color 背景颜色（带透明度）
     * @param focused 是否为焦点状态
     */
    private static android.graphics.drawable.Drawable createTVFocusableTransparentDrawable(int color, boolean focused) {
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(12);
        if (focused) {
            // 焦点状态：加粗发光边框
            drawable.setStroke(6, TV_FOCUS_BORDER_COLOR);
            // 背景色稍微增加不透明度，增强对比度
            drawable.setColor(lightenAlpha(color, 0.2f));
        } else {
            drawable.setStroke(0, 0);
        }
        return drawable;
    }

    /**
     * 增加颜色透明度（让透明颜色更不透明一点）
     */
    private static int lightenAlpha(int color, float factor) {
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        // 增加alpha值（减少透明度）
        int newA = Math.min(255, (int) (a + (255 - a) * factor));
        return (newA << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * 创建电视端输入框背景
     * @param focused 是否为焦点状态
     * @param isDarkTheme 是否为深色主题
     */
    private static android.graphics.drawable.Drawable createTVInputDrawable(boolean focused, boolean isDarkTheme) {
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        if (focused) {
            // 焦点状态：明显的浅蓝背景 + 蓝色边框
            drawable.setColor(isDarkTheme ? 0x44007AFF : TV_INPUT_FOCUS_BG);
            drawable.setStroke(4, TV_INPUT_FOCUS_BORDER);
            drawable.setCornerRadius(8);
        } else {
            // 非焦点状态：普通背景
            drawable.setColor(isDarkTheme ? DARK_BG_SECONDARY : BACKGROUND_LIGHT);
            drawable.setStroke(2, isDarkTheme ? DARK_BORDER : BORDER_COLOR);
            drawable.setCornerRadius(8);
        }
        return drawable;
    }

    /**
     * 为输入框添加电视端焦点效果
     */
    private static void applyTVInputFocusEffect(EditText editText, boolean isDarkTheme) {
        editText.setFocusable(true);
        editText.setFocusableInTouchMode(true);
        editText.setBackground(createTVInputDrawable(false, isDarkTheme));

        editText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                ((EditText) v).setBackground(createTVInputDrawable(hasFocus, isDarkTheme));
                if (hasFocus) {
                    // 焦点时文字颜色加深，提示用户正在编辑
                    ((EditText) v).setTextColor(isDarkTheme ? DARK_TEXT_PRIMARY : TEXT_PRIMARY);
                }
            }
        });
    }

    // 颜色提亮辅助方法
    private static int lightenColor(int color, float factor) {
        int a = (color >> 24) & 0xFF;
        int r = (int) Math.min(255, ((color >> 16) & 0xFF) + (255 - ((color >> 16) & 0xFF)) * factor);
        int g = (int) Math.min(255, ((color >> 8) & 0xFF) + (255 - ((color >> 8) & 0xFF)) * factor);
        int b = (int) Math.min(255, (color & 0xFF) + (255 - (color & 0xFF)) * factor);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // 创建圆角背景 - 带阴影效果
    private static android.graphics.drawable.Drawable createRoundedBackgroundDrawable(int color) {
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(12); // 使用px单位的圆角半径
        drawable.setStroke(0, 0); // 无边框
        return drawable;
    }

    private static android.graphics.drawable.Drawable createRoundedStrokeBackgroundDrawable(int fillColor, int strokeColor, int strokeWidth) {
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(12);
        drawable.setStroke(strokeWidth, strokeColor);
        return drawable;
    }

    // 创建带边框的圆角背景
    private static android.graphics.drawable.Drawable createRoundedBorderDrawable(int color) {
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setColor(Color.TRANSPARENT);
        drawable.setStroke(2, color);
        drawable.setCornerRadius(12);
        return drawable;
    }

    // 创建带粗边框的焦点页签背景（用于电视端焦点高亮）
    private static android.graphics.drawable.Drawable createFocusedTabDrawable(int color) {
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setColor(color);
        drawable.setStroke(4, PRIMARY_COLOR); // 加粗外边框
        drawable.setCornerRadius(12);
        return drawable;
    }

    // 安全显示对话框的辅助方法
    private static void safeShowDialog(Activity activity, AlertDialog dialog) {
        if (activity != null && !activity.isFinishing() && !activity.isDestroyed() && !dialog.isShowing()) {
            try {
                dialog.show();
            } catch (Exception e) {
                DanmakuSpider.log("显示对话框失败: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            DanmakuSpider.log("Activity已销毁或对话框已在显示，无法显示对话框");
        }
    }


    /**
     * 显示带标签页切换的日志对话框（弹幕日志 + Go代理日志）
     * @param ctx 上下文
     */
    public static void showUnifiedLogDialog(Context ctx) {
        if (!(ctx instanceof Activity)) {
            DanmakuSpider.log("错误：Context不是Activity");
            return;
        }
        Activity activity = (Activity) ctx;
        if (activity.isFinishing() || activity.isDestroyed()) {
            DanmakuSpider.log("Activity已销毁或正在销毁，不显示日志对话框");
            return;
        }

        activity.runOnUiThread(() -> {
            try {
                if (activity.isFinishing() || activity.isDestroyed()) {
                    DanmakuSpider.log("Activity已销毁，不显示日志对话框");
                    return;
                }

                AlertDialog.Builder builder = new AlertDialog.Builder(activity);

                LinearLayout mainLayout = new LinearLayout(activity);
                mainLayout.setOrientation(LinearLayout.VERTICAL);
                mainLayout.setBackgroundColor(BACKGROUND_WHITE);
                mainLayout.setPadding(dpToPx(activity, 20), dpToPx(activity, 12), dpToPx(activity, 20), dpToPx(activity, 12));

                // 标题
                TextView title = new TextView(activity);
                title.setText("日志查看器");
                title.setTextSize(20);
                title.setTextColor(PRIMARY_COLOR);
                title.setGravity(Gravity.CENTER);
                title.setPadding(0, dpToPx(activity, 4), 0, dpToPx(activity, 8));
                title.setTypeface(null, android.graphics.Typeface.BOLD);
                mainLayout.addView(title);

                // 判断是否有代理
                final int activeProxyType = ProxyManager.getActiveProxyType();
                final boolean hasProxy = activeProxyType != ProxyManager.PROXY_TYPE_NONE;

                // 当前选中的页签索引（0=弹幕日志，1=代理日志）
                final int[] currentTab = {0};
                // 当前排序状态（false=正序，true=倒序）
                final boolean[] isReversed = {false};

                // 日志内容显示区域 - 使用权重自适应高度
                ScrollView scrollView = new ScrollView(activity);
                scrollView.setBackgroundColor(0xFFF5F5F5);
                LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
                scrollView.setLayoutParams(scrollParams);
                scrollView.setPadding(dpToPx(activity, 10), dpToPx(activity, 10), dpToPx(activity, 10), dpToPx(activity, 10));

                TextView logText = new TextView(activity);
                logText.setTextSize(11);
                logText.setTextColor(TEXT_PRIMARY);
                logText.setTypeface(android.graphics.Typeface.MONOSPACE);
                logText.setText(DanmakuSpider.getLogContent());
                scrollView.addView(logText);
                mainLayout.addView(scrollView);

                // 页签容器 - 放在日志区域下方
                LinearLayout tabContainer = new LinearLayout(activity);
                tabContainer.setOrientation(LinearLayout.HORIZONTAL);
                tabContainer.setGravity(Gravity.CENTER);
                tabContainer.setPadding(0, dpToPx(activity, 8), 0, dpToPx(activity, 8));

                // 弹幕日志页签按钮
                Button danmakuTabBtn = createTabButton(activity, "弹幕日志", true);
                LinearLayout.LayoutParams danmakuTabParams = new LinearLayout.LayoutParams(
                        0, dpToPx(activity, 36), 1);
                danmakuTabParams.setMargins(0, 0, dpToPx(activity, 4), 0);
                danmakuTabBtn.setLayoutParams(danmakuTabParams);

                // 代理日志页签按钮（仅当存在时显示）
                Button goProxyTabBtn = null;
                if (hasProxy) {
                    String tabLabel = ProxyManager.getProxyTypeShortName() + "代理日志";
                    goProxyTabBtn = createTabButton(activity, tabLabel, false);
                    LinearLayout.LayoutParams goProxyTabParams = new LinearLayout.LayoutParams(
                            0, dpToPx(activity, 36), 1);
                    goProxyTabParams.setMargins(dpToPx(activity, 4), 0, 0, 0);
                    goProxyTabBtn.setLayoutParams(goProxyTabParams);
                }

                tabContainer.addView(danmakuTabBtn);
                if (goProxyTabBtn != null) {
                    tabContainer.addView(goProxyTabBtn);
                }
                mainLayout.addView(tabContainer);

                // 按钮区域
                LinearLayout btnLayout = new LinearLayout(activity);
                btnLayout.setOrientation(LinearLayout.HORIZONTAL);
                btnLayout.setGravity(Gravity.CENTER);
                btnLayout.setPadding(0, dpToPx(activity, 4), 0, 0);

                Button sortButton = createStyledButtonWithBorder(activity, "排序: 正序", PRIMARY_COLOR);
                Button clearButton = createStyledButtonWithBorder(activity, "清空", PRIMARY_COLOR);
                Button closeButton = createStyledButtonWithBorder(activity, "关闭", PRIMARY_COLOR);

                LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                        0, dpToPx(activity, 40), 1);
                btnParams.setMargins(dpToPx(activity, 4), 0, dpToPx(activity, 4), 0);

                sortButton.setLayoutParams(btnParams);
                clearButton.setLayoutParams(btnParams);
                closeButton.setLayoutParams(btnParams);

                btnLayout.addView(sortButton);
                btnLayout.addView(clearButton);
                btnLayout.addView(closeButton);
                mainLayout.addView(btnLayout);

                builder.setView(mainLayout);
                AlertDialog dialog = builder.create();

                // 设置对话框最大高度为屏幕高度的70%
                dialog.setOnShowListener(d -> {
                    android.view.Window window = dialog.getWindow();
                    if (window != null) {
                        android.view.WindowManager.LayoutParams params = window.getAttributes();
                        params.width = (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.90);
                        params.height = (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.85);
                        window.setAttributes(params);
                    }
                });

                // 页签切换逻辑
                final Button finalGoProxyTabBtn = goProxyTabBtn;
                danmakuTabBtn.setOnClickListener(v -> {
                    currentTab[0] = 0;
                    updateTabButtonState(danmakuTabBtn, true);
                    if (finalGoProxyTabBtn != null) {
                        updateTabButtonState(finalGoProxyTabBtn, false);
                    }
                    // 弹幕日志支持倒序
                    logText.setText(DanmakuSpider.getLogContent(isReversed[0]));
                    // 倒序时滚动到顶部，正序时滚动到底部
                    scrollView.post(() -> scrollView.fullScroll(isReversed[0] ? ScrollView.FOCUS_UP : ScrollView.FOCUS_DOWN));
                });

                if (goProxyTabBtn != null) {
                    final Button finalGoProxyTabBtnForClick = goProxyTabBtn;
                    goProxyTabBtn.setOnClickListener(v -> {
                        currentTab[0] = 1;
                        updateTabButtonState(danmakuTabBtn, false);
                        updateTabButtonState(finalGoProxyTabBtnForClick, true);
                        logText.setText(ProxyManager.getLogContent(isReversed[0]));
                        // 倒序时滚动到顶部，正序时滚动到底部
                        scrollView.post(() -> scrollView.fullScroll(isReversed[0] ? ScrollView.FOCUS_UP : ScrollView.FOCUS_DOWN));
                    });
                }

                // 排序按钮逻辑（弹幕日志和Go代理日志都支持倒序）
                sortButton.setOnClickListener(v -> {
                    isReversed[0] = !isReversed[0];
                    sortButton.setText(isReversed[0] ? "排序: 倒序" : "排序: 正序");

                    if (currentTab[0] == 0) {
                        logText.setText(DanmakuSpider.getLogContent(isReversed[0]));
                    } else {
                        logText.setText(ProxyManager.getLogContent(isReversed[0]));
                    }
                    // 倒序时滚动到顶部，正序时滚动到底部
                    scrollView.post(() -> scrollView.fullScroll(isReversed[0] ? ScrollView.FOCUS_UP : ScrollView.FOCUS_DOWN));
                });

                // 清空按钮逻辑
                clearButton.setOnClickListener(v -> {
                    if (currentTab[0] == 0) {
                        DanmakuSpider.clearLogs();
                        logText.setText(DanmakuSpider.getLogContent(isReversed[0]));
                    } else {
                        ProxyManager.clearLogs();
                        logText.setText(ProxyManager.getLogContent(isReversed[0]));
                    }
                });

                closeButton.setOnClickListener(v -> dialog.dismiss());

                safeShowDialog(activity, dialog);

                // 初始滚动到底部（正序时）
                scrollView.post(() -> scrollView.fullScroll(isReversed[0] ? ScrollView.FOCUS_UP : ScrollView.FOCUS_DOWN));

            } catch (Exception e) {
                DanmakuSpider.log("显示统一日志对话框异常: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    // 创建页签按钮
    private static Button createTabButton(Activity activity, String text, boolean isActive) {
        Button button = new Button(activity);
        button.setText(text);
        button.setTextSize(14);
        button.setTypeface(null, android.graphics.Typeface.BOLD);
        // 设置焦点可用，支持电视端遥控器操作
        button.setFocusable(true);
        button.setFocusableInTouchMode(true);
        button.setClickable(true);
        updateTabButtonState(button, isActive);

        // 添加焦点变化监听，电视端高亮效果
        button.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                // 获得焦点时显示高亮边框 - 使用加粗边框而不是缩放，避免覆盖其他按钮
                ((Button) v).setBackground(createFocusedTabDrawable(PRIMARY_LIGHT));
                ((Button) v).setTextColor(Color.WHITE);
            } else {
                // 失去焦点时恢复原始状态
                // 需要根据当前激活状态恢复样式
                boolean isCurrentlyActive = v.getTag() != null && (Boolean) v.getTag();
                updateTabButtonState((Button) v, isCurrentlyActive);
            }
        });

        // 保存激活状态到tag，用于焦点恢复时判断
        button.setTag(isActive);

        return button;
    }

    // 更新页签按钮状态
    private static void updateTabButtonState(Button button, boolean isActive) {
        if (button == null) return;
        if (isActive) {
            button.setTextColor(Color.WHITE);
            button.setBackground(createRoundedBackgroundDrawable(PRIMARY_COLOR));
        } else {
            button.setTextColor(PRIMARY_COLOR);
            button.setBackground(createRoundedBorderDrawable(PRIMARY_COLOR));
        }
        // 更新tag保存当前激活状态，用于焦点恢复时判断
        button.setTag(isActive);
    }


    // 显示搜索对话框
    public static void showSearchDialog(Activity activity, EpisodeInfo episodeInfo) {
        // 检查Activity状态
        if (activity.isFinishing() || activity.isDestroyed()) {
            DanmakuSpider.log("Activity已销毁或正在销毁，不显示搜索对话框");
            return;
        }

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    // 在创建对话框前再次检查状态
                    if (activity.isFinishing() || activity.isDestroyed()) {
                        DanmakuSpider.log("Activity已销毁，不显示搜索对话框");
                        return;
                    }

                    DanmakuConfig config = DanmakuConfigManager.getConfig(activity);
                    boolean isTemplate3 = config.isDarkGridDanmakuStyle();
                    boolean isModernPanel = config.isModernPanelDanmakuStyle();

                    AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                    LinearLayout mainLayout = new LinearLayout(activity);
                    mainLayout.setOrientation(LinearLayout.VERTICAL);
                    mainLayout.setBackgroundColor(isTemplate3 ? DARK_BG_PRIMARY : BACKGROUND_WHITE);
                    mainLayout.setPadding(dpToPx(activity, 15), dpToPx(activity, 10), dpToPx(activity, 15), dpToPx(activity, 10));

                    TextView title = new TextView(activity);
                    title.setText("Leo弹幕搜索");
                    title.setTextSize(16);
                    title.setTextColor(isTemplate3 ? DARK_TEXT_PRIMARY : PRIMARY_COLOR);
                    title.setGravity(Gravity.CENTER);
                    title.setTypeface(null, android.graphics.Typeface.BOLD);
                    title.setPadding(0, dpToPx(activity, 2), 0, dpToPx(activity, 5));
                    mainLayout.addView(title);

                    LinearLayout searchLayout = new LinearLayout(activity);
                    searchLayout.setOrientation(LinearLayout.HORIZONTAL);
                    searchLayout.setPadding(0, 0, 0, dpToPx(activity, 4));
                    searchLayout.setGravity(Gravity.CENTER_VERTICAL);

                    final EditText searchInput = new EditText(activity);
                    searchInput.setHint("输入关键词搜索弹幕...");
                    String initialKeyword = episodeInfo != null && episodeInfo.getEpisodeNames() != null && !episodeInfo.getEpisodeNames().isEmpty() 
                            ? episodeInfo.getEpisodeNames().get(0) : "";
                    String cachedKeyword = SharedPreferencesService.getSearchKeywordCache(activity, initialKeyword);
                    searchInput.setText(cachedKeyword);
                    searchInput.setHintTextColor(isTemplate3 ? DARK_TEXT_TERTIARY : TEXT_TERTIARY);
                    searchInput.setBackgroundColor(isTemplate3 ? DARK_BG_SECONDARY : BACKGROUND_LIGHT);
                    searchInput.setPadding(dpToPx(activity, 12), dpToPx(activity, 10), dpToPx(activity, 12), dpToPx(activity, 10));
                    searchInput.setTextSize(14);
                    searchInput.setTextColor(isTemplate3 ? DARK_TEXT_PRIMARY : TEXT_PRIMARY);
                    LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(0, dpToPx(activity, 44), 1);
                    inputParams.setMargins(0, 0, dpToPx(activity, 4), 0);
                    searchInput.setLayoutParams(inputParams);

                    Button reverseBtn = isTemplate3 ?
                            createDarkSolidButton(activity, isReversed ? "倒序" : "升序", isReversed ? DARK_ACCENT_COLOR : DARK_TERTIARY_LIGHT) :
                            createStyledButton(activity, isReversed ? "倒序" : "升序", isReversed ? ACCENT_COLOR : TERTIARY_LIGHT);
                    reverseBtn.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(activity, 50), dpToPx(activity, 44)));
                    reverseBtn.setTextSize(16);

                    Button searchBtn = isTemplate3 ?
                            createDarkSolidButton(activity, "搜索", DARK_BG_TERTIARY) :
                            createStyledButton(activity, "搜索", PRIMARY_COLOR);
                    searchBtn.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(activity, 70), dpToPx(activity, 44)));

                    searchLayout.addView(searchInput);
                    searchLayout.addView(reverseBtn);
                    View separator = new View(activity);
                    LinearLayout.LayoutParams separatorParams = new LinearLayout.LayoutParams(dpToPx(activity, 4), dpToPx(activity, 44));
                    separator.setLayoutParams(separatorParams);
                    searchLayout.addView(separator);
                    searchLayout.addView(searchBtn);
                    mainLayout.addView(searchLayout);

                    LinearLayout tabContainer = new LinearLayout(activity);
                    tabContainer.setOrientation(LinearLayout.HORIZONTAL);
                    tabContainer.setGravity(Gravity.CENTER_VERTICAL);
                    tabContainer.setPadding(0, dpToPx(activity, 4), 0, dpToPx(activity, 4));
                    tabContainer.setBackgroundColor(isTemplate3 ? DARK_BG_TERTIARY : BACKGROUND_LIGHT);
                    LinearLayout.LayoutParams tabContainerParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(activity, 62));
                    tabContainerParams.setMargins(0, dpToPx(activity, 2), 0, dpToPx(activity, 8));
                    tabContainer.setLayoutParams(tabContainerParams);
                    tabContainer.setVisibility(View.GONE);
                    mainLayout.addView(tabContainer);

                    ScrollView resultScroll = new ScrollView(activity);
                    resultScroll.setBackgroundColor(isTemplate3 ? Color.TRANSPARENT : BACKGROUND_WHITE);
                    LinearLayout resultContainer = new LinearLayout(activity);
                    resultContainer.setOrientation(LinearLayout.VERTICAL);
                    resultContainer.setPadding(0, 10, 0, 0);
                    resultContainer.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);

                    LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0);
                    scrollParams.weight = 1;
                    resultScroll.setLayoutParams(scrollParams);

                    resultScroll.addView(resultContainer);
                    mainLayout.addView(resultScroll);

                    builder.setView(mainLayout);
                    final AlertDialog dialog = builder.create();

                    if (isTemplate3) {
                        dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
                    }

                    reverseBtn.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            isReversed = !isReversed;
                            if (isTemplate3) {
                                reverseBtn.setBackground(createRoundedTransparentDrawable(isReversed ? DARK_ACCENT_COLOR : DARK_TERTIARY_LIGHT));
                            } else {
                                reverseBtn.setBackground(createRoundedBackgroundDrawable(isReversed ? ACCENT_COLOR : TERTIARY_LIGHT));
                            }
                            reverseBtn.setText(isReversed ? "倒序" : "升序");
                            showResultsForTab(resultContainer, currentItems, activity, dialog);
                        }
                    });

                    searchBtn.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            String keyword = searchInput.getText().toString().trim();
                            if (TextUtils.isEmpty(keyword)) {
                                Utils.safeShowToast(activity, "请输入关键词");
                                return;
                            }
                            currentSearchKeyword = keyword;

                            String cacheKey = episodeInfo != null && episodeInfo.getEpisodeNames() != null && !episodeInfo.getEpisodeNames().isEmpty() 
                                    ? episodeInfo.getEpisodeNames().get(0) : "";
                            if (!keyword.equals(cacheKey)) {
                                SharedPreferencesService.saveSearchKeywordCache(activity, cacheKey, keyword);
                                DanmakuSpider.log("已保存新的搜索缓存: " + cacheKey + " -> " + keyword);
                            } else {
                                SharedPreferencesService.saveSearchKeywordCache(activity, cacheKey, "");
                                DanmakuSpider.log("已清空搜索缓存: " + cacheKey);
                            }

                            resultContainer.removeAllViews();
                            tabContainer.removeAllViews();
                            tabContainer.setVisibility(View.GONE);
                            TextView loading = new TextView(activity);
                            loading.setText("正在搜索: " + keyword);
                            loading.setGravity(Gravity.CENTER);
                            loading.setPadding(0, 20, 0, 20);
                            loading.setTextColor(isTemplate3 ? DARK_TEXT_SECONDARY : TEXT_SECONDARY);
                            resultContainer.addView(loading);

                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    // 创建新的 EpisodeInfo 用于手动搜索（使用用户输入的关键词）
                                    EpisodeInfo searchEpisodeInfo = new EpisodeInfo();
                                    List<String> names = new ArrayList<>();
                                    names.add(keyword);
                                    searchEpisodeInfo.setEpisodeNames(names);
                                    // 如果原 episodeInfo 有集数信息，保留它
                                    if (episodeInfo != null && !TextUtils.isEmpty(episodeInfo.getEpisodeNum())) {
                                        searchEpisodeInfo.setEpisodeNum(episodeInfo.getEpisodeNum());
                                    }
                                    List<DanmakuItem> results = LeoDanmakuService.manualSearch(searchEpisodeInfo, activity);
                                    if (isReversed) {
                                        java.util.Collections.reverse(results);
                                    }

                                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                                        @Override
                                        public void run() {
                                            resultContainer.removeAllViews();
                                            tabContainer.removeAllViews();

                                            if (results.isEmpty()) {
                                                tabContainer.setVisibility(View.GONE);
                                                TextView empty = new TextView(activity);
                                                empty.setText("未找到结果");
                                                empty.setGravity(Gravity.CENTER);
                                                empty.setPadding(0, 50, 0, 50);
                                                empty.setTextColor(isTemplate3 ? DARK_TEXT_SECONDARY : TEXT_SECONDARY);
                                                resultContainer.addView(empty);
                                                return;
                                            }

                                            java.util.Map<String, List<DanmakuItem>> groupedResults = new java.util.LinkedHashMap<>();
                                            for (DanmakuItem item : results) {
                                                String groupName = isModernPanel ? buildSearchResultGroupName(activity, item)
                                                        : (item.from != null ? item.from : "默认");
                                                if (!groupedResults.containsKey(groupName)) {
                                                    groupedResults.put(groupName, new java.util.ArrayList<>());
                                                }
                                                groupedResults.get(groupName).add(item);
                                            }

                                            java.util.List<String> tabs = new java.util.ArrayList<>(groupedResults.keySet());
                                            java.util.Collections.sort(tabs);

                                            if (isModernPanel) {
                                                int selectedTabIndex = findInitialSearchTabIndex(tabs, groupedResults);
                                                renderSearchSourceSelector(tabContainer, resultContainer, groupedResults, tabs,
                                                        selectedTabIndex, activity, dialog, isTemplate3);
                                            } else {
                                                renderLegacySearchSourceTabs(tabContainer, resultContainer, groupedResults, tabs,
                                                        activity, dialog, isTemplate3);
                                            }
                                        }
                                    });
                                }
                            }).start();
                        }
                    });

                    safeShowDialog(activity, dialog);

                    android.view.WindowManager.LayoutParams lp = new android.view.WindowManager.LayoutParams();
                    lp.copyFrom(dialog.getWindow().getAttributes());
                    lp.width = (int) (activity.getResources().getDisplayMetrics().widthPixels * config.getLpWidth());
                    lp.height = (int) (activity.getResources().getDisplayMetrics().heightPixels * config.getLpHeight());
                    lp.alpha = config.getLpAlpha();
                    dialog.getWindow().setAttributes(lp);

                    String keywordToSearch = SharedPreferencesService.getSearchKeywordCache(activity, initialKeyword);
                    if (!TextUtils.isEmpty(keywordToSearch)) {
                        searchBtn.performClick();
                    }
                
                } catch (Exception e) {
                    DanmakuSpider.log("显示搜索对话框异常: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });
    }

    // dp转px
    private static int dpToPx(Context context, int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private static int findInitialSearchTabIndex(List<String> tabs, java.util.Map<String, List<DanmakuItem>> groupedResults) {
        if (tabs == null || tabs.isEmpty()) return 0;
        if (TextUtils.isEmpty(DanmakuManager.lastDanmakuUrl)) return 0;

        for (int i = 0; i < tabs.size(); i++) {
            List<DanmakuItem> items = groupedResults.get(tabs.get(i));
            if (items == null) continue;
            for (DanmakuItem item : items) {
                if (item != null && DanmakuManager.lastDanmakuUrl.equals(item.getDanmakuUrl())) {
                    return i;
                }
            }
        }
        return 0;
    }

    private static void renderLegacySearchSourceTabs(LinearLayout tabContainer,
                                                     LinearLayout resultContainer,
                                                     java.util.Map<String, List<DanmakuItem>> groupedResults,
                                                     List<String> tabs,
                                                     Activity activity,
                                                     AlertDialog dialog,
                                                     boolean isTemplate3) {
        tabContainer.removeAllViews();
        if (tabs == null || tabs.isEmpty()) {
            tabContainer.setVisibility(View.GONE);
            resultContainer.removeAllViews();
            return;
        }

        tabContainer.setVisibility(View.VISIBLE);
        updateSearchSourceContainerHeight(tabContainer, activity, 48);
        int selectedIndex = findInitialSearchTabIndex(tabs, groupedResults);

        for (int i = 0; i < tabs.size(); i++) {
            String tabName = tabs.get(i);
            Button tabBtn = isTemplate3 ?
                    createDarkSolidButton(activity, tabName, DARK_PRIMARY_COLOR) :
                    createStyledButton(activity, tabName, PRIMARY_COLOR);
            tabBtn.setTag(tabName);
            tabBtn.setPadding(dpToPx(activity, 15), dpToPx(activity, 10), dpToPx(activity, 15), dpToPx(activity, 10));

            LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
            tabParams.setMargins(dpToPx(activity, 5), 0, dpToPx(activity, 5), 0);
            tabBtn.setLayoutParams(tabParams);
            tabBtn.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    Object tag = v.getTag();
                    boolean active = tag instanceof Boolean && (Boolean) tag;
                    if (isTemplate3) {
                        ((Button) v).setTextColor(DARK_TEXT_PRIMARY);
                        v.setBackground(createRoundedTransparentDrawable(hasFocus ? DARK_PRIMARY_DARK :
                                (active ? DARK_PRIMARY_COLOR : DARK_INACTIVE)));
                    } else {
                        ((Button) v).setTextColor(Color.WHITE);
                        v.setBackground(createRoundedBackgroundDrawable(hasFocus ? PRIMARY_DARK :
                                (active ? PRIMARY_COLOR : GRAY_INACTIVE)));
                    }
                }
            });

            final int tabIndex = i;
            tabBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v1) {
                    for (int j = 0; j < tabContainer.getChildCount(); j++) {
                        updateLegacySourceTabState((Button) tabContainer.getChildAt(j), j == tabIndex, isTemplate3);
                    }
                    showResultsForTab(resultContainer, groupedResults.get(tabName), activity, dialog);
                }
            });

            tabContainer.addView(tabBtn);
            updateLegacySourceTabState(tabBtn, i == selectedIndex, isTemplate3);
        }

        showResultsForTab(resultContainer, groupedResults.get(tabs.get(selectedIndex)), activity, dialog);
    }

    private static void updateLegacySourceTabState(Button button, boolean isActive, boolean isTemplate3) {
        button.setTag(Boolean.valueOf(isActive));
        if (isTemplate3) {
            button.setBackground(createRoundedTransparentDrawable(isActive ? DARK_PRIMARY_COLOR : DARK_INACTIVE));
            button.setTextColor(DARK_TEXT_PRIMARY);
        } else {
            button.setBackground(createRoundedBackgroundDrawable(isActive ? PRIMARY_COLOR : GRAY_INACTIVE));
            button.setTextColor(Color.WHITE);
        }
    }

    private static void renderSearchSourceSelector(LinearLayout tabContainer,
                                                   LinearLayout resultContainer,
                                                   java.util.Map<String, List<DanmakuItem>> groupedResults,
                                                   List<String> tabs,
                                                   int selectedIndex,
                                                   Activity activity,
                                                   AlertDialog dialog,
                                                   boolean isTemplate3) {
        tabContainer.removeAllViews();
        if (tabs == null || tabs.isEmpty()) {
            tabContainer.setVisibility(View.GONE);
            resultContainer.removeAllViews();
            return;
        }

        int selected = Math.max(0, Math.min(selectedIndex, tabs.size() - 1));
        if (tabs.size() == 1) {
            tabContainer.setVisibility(View.GONE);
            showResultsForTab(resultContainer, groupedResults.get(tabs.get(selected)), activity, dialog);
            return;
        }

        tabContainer.setVisibility(View.VISIBLE);
        if (shouldUseSimpleSearchTabs(tabs, groupedResults)) {
            updateSearchSourceContainerHeight(tabContainer, activity, 48);
            renderSearchSourceTabs(tabContainer, resultContainer, groupedResults, tabs,
                    selected, activity, dialog, isTemplate3);
        } else {
            updateSearchSourceContainerHeight(tabContainer, activity, 48);
            renderSearchSourcePager(tabContainer, resultContainer, groupedResults, tabs,
                    selected, activity, dialog, isTemplate3);
        }
    }

    private static boolean shouldUseSimpleSearchTabs(List<String> tabs,
                                                     java.util.Map<String, List<DanmakuItem>> groupedResults) {
        return tabs != null
                && tabs.size() > 1
                && tabs.size() <= SIMPLE_SOURCE_TAB_LIMIT
                && getTotalSearchResultCount(tabs, groupedResults) <= SIMPLE_RESULT_COUNT_LIMIT;
    }

    private static int getTotalSearchResultCount(List<String> tabs,
                                                 java.util.Map<String, List<DanmakuItem>> groupedResults) {
        if (tabs == null || groupedResults == null) return 0;
        int count = 0;
        for (String tab : tabs) {
            List<DanmakuItem> items = groupedResults.get(tab);
            if (items != null) count += items.size();
        }
        return count;
    }

    private static void updateSearchSourceContainerHeight(LinearLayout tabContainer, Activity activity, int heightDp) {
        ViewGroup.LayoutParams params = tabContainer.getLayoutParams();
        if (params != null) {
            params.height = dpToPx(activity, heightDp);
            tabContainer.setLayoutParams(params);
        }
    }

    private static void renderSearchSourceTabs(LinearLayout tabContainer,
                                               LinearLayout resultContainer,
                                               java.util.Map<String, List<DanmakuItem>> groupedResults,
                                               List<String> tabs,
                                               int selectedIndex,
                                               Activity activity,
                                               AlertDialog dialog,
                                               boolean isTemplate3) {
        tabContainer.removeAllViews();
        int selected = Math.max(0, Math.min(selectedIndex, tabs.size() - 1));
        for (int i = 0; i < tabs.size(); i++) {
            String tabName = tabs.get(i);
            Button tabBtn = createSearchSourceTabButton(activity, tabName, i == selected, isTemplate3);
            LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
            tabParams.setMargins(dpToPx(activity, 5), 0, dpToPx(activity, 5), 0);
            tabBtn.setLayoutParams(tabParams);

            final int targetIndex = i;
            tabBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    renderSearchSourceSelector(tabContainer, resultContainer, groupedResults, tabs,
                            targetIndex, activity, dialog, isTemplate3);
                }
            });
            tabContainer.addView(tabBtn);
        }

        showResultsForTab(resultContainer, groupedResults.get(tabs.get(selected)), activity, dialog);
    }

    private static Button createSearchSourceTabButton(Activity activity, String text, boolean isActive, boolean isTemplate3) {
        Button button = new Button(activity);
        button.setText(text);
        button.setTextSize(14);
        button.setTypeface(null, android.graphics.Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setSingleLine(true);
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setAllCaps(false);
        button.setFocusable(true);
        button.setFocusableInTouchMode(true);
        button.setPadding(dpToPx(activity, 10), 0, dpToPx(activity, 10), 0);
        button.setTag(Boolean.valueOf(isActive));
        updateSearchSourceTabStyle(button, isActive, isTemplate3, false);

        button.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                Object tag = v.getTag();
                boolean active = tag instanceof Boolean && (Boolean) tag;
                updateSearchSourceTabStyle((Button) v, active, isTemplate3, hasFocus);
            }
        });
        return button;
    }

    private static void updateSearchSourceTabStyle(Button button, boolean isActive, boolean isTemplate3, boolean hasFocus) {
        int color;
        if (isTemplate3) {
            color = hasFocus ? DARK_PRIMARY_DARK : (isActive ? DARK_PRIMARY_COLOR : DARK_INACTIVE);
            button.setTextColor(DARK_TEXT_PRIMARY);
            button.setBackground(createRoundedTransparentDrawable(color));
        } else {
            color = hasFocus ? PRIMARY_DARK : (isActive ? PRIMARY_COLOR : GRAY_INACTIVE);
            button.setTextColor(Color.WHITE);
            button.setBackground(createRoundedBackgroundDrawable(color));
        }
    }

    private static void renderSearchSourcePager(LinearLayout tabContainer,
                                                LinearLayout resultContainer,
                                                java.util.Map<String, List<DanmakuItem>> groupedResults,
                                                List<String> tabs,
                                                int selectedIndex,
                                                Activity activity,
                                                AlertDialog dialog,
                                                boolean isTemplate3) {
        tabContainer.removeAllViews();
        if (tabs == null || tabs.isEmpty()) {
            resultContainer.removeAllViews();
            return;
        }

        final int selected = Math.max(0, Math.min(selectedIndex, tabs.size() - 1));
        final String tabName = tabs.get(selected);
        List<DanmakuItem> selectedItems = groupedResults.get(tabName);
        boolean canCycle = tabs.size() > 1;

        final Runnable prevAction = new Runnable() {
            @Override
            public void run() {
                if (!canCycle) return;
                int target = selected > 0 ? selected - 1 : tabs.size() - 1;
                renderSearchSourceSelector(tabContainer, resultContainer, groupedResults, tabs,
                        target, activity, dialog, isTemplate3);
            }
        };

        final Runnable nextAction = new Runnable() {
            @Override
            public void run() {
                if (!canCycle) return;
                int target = selected < tabs.size() - 1 ? selected + 1 : 0;
                renderSearchSourceSelector(tabContainer, resultContainer, groupedResults, tabs,
                        target, activity, dialog, isTemplate3);
            }
        };

        final Runnable chooseAction = new Runnable() {
            @Override
            public void run() {
                showSearchSourceChooser(tabContainer, resultContainer, groupedResults, tabs,
                        selected, activity, dialog, isTemplate3);
            }
        };

        Button prevBtn = createSearchPagerArrowButton(activity, "<", isTemplate3, canCycle);
        Button centerBtn = createSearchPagerCenterButton(activity, tabName,
                "来源 " + (selected + 1) + "/" + tabs.size(),
                isTemplate3);
        Button nextBtn = createSearchPagerArrowButton(activity, ">", isTemplate3, canCycle);

        prevBtn.setOnClickListener(v -> prevAction.run());
        nextBtn.setOnClickListener(v -> nextAction.run());
        attachSearchPagerNavigation(centerBtn, prevAction, nextAction, chooseAction, canCycle);

        LinearLayout.LayoutParams arrowParams = new LinearLayout.LayoutParams(dpToPx(activity, 54), ViewGroup.LayoutParams.MATCH_PARENT);
        arrowParams.setMargins(dpToPx(activity, 4), 0, dpToPx(activity, 4), 0);
        LinearLayout.LayoutParams centerParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        centerParams.setMargins(dpToPx(activity, 4), 0, dpToPx(activity, 4), 0);

        tabContainer.addView(prevBtn, arrowParams);
        tabContainer.addView(centerBtn, centerParams);
        tabContainer.addView(nextBtn, arrowParams);

        showResultsForTab(resultContainer, selectedItems, activity, dialog);
    }

    private static Button createSearchPagerArrowButton(Activity activity, String text, boolean isTemplate3, boolean enabled) {
        Button button = new Button(activity);
        button.setText(text);
        button.setTextSize(22);
        button.setTypeface(null, android.graphics.Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setAllCaps(false);
        button.setEnabled(enabled);
        button.setFocusable(enabled);
        button.setFocusableInTouchMode(enabled);
        button.setPadding(0, 0, 0, 0);

        int activeColor = isTemplate3 ? DARK_PRIMARY_COLOR : PRIMARY_COLOR;
        int inactiveColor = isTemplate3 ? DARK_INACTIVE : GRAY_INACTIVE;
        button.setTextColor(isTemplate3 ? DARK_TEXT_PRIMARY : Color.WHITE);
        button.setBackground(isTemplate3 ?
                createRoundedTransparentDrawable(enabled ? activeColor : inactiveColor) :
                createRoundedBackgroundDrawable(enabled ? activeColor : inactiveColor));
        button.setAlpha(enabled ? 1.0f : 0.55f);

        button.setOnFocusChangeListener((v, hasFocus) -> {
            if (!button.isEnabled()) return;
            int color = hasFocus ? (isTemplate3 ? DARK_SECONDARY_COLOR : PRIMARY_DARK) : activeColor;
            v.setBackground(isTemplate3 ? createRoundedTransparentDrawable(color) : createRoundedBackgroundDrawable(color));
            v.setScaleX(hasFocus ? 1.04f : 1.0f);
            v.setScaleY(hasFocus ? 1.04f : 1.0f);
        });

        return button;
    }

    private static Button createSearchPagerCenterButton(Activity activity, String title, String summary, boolean isTemplate3) {
        Button button = new Button(activity);
        button.setText(title + "  " + summary);
        button.setTextSize(14);
        button.setTypeface(null, android.graphics.Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setSingleLine(true);
        button.setMaxLines(1);
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setAllCaps(false);
        button.setFocusable(true);
        button.setFocusableInTouchMode(true);
        button.setPadding(dpToPx(activity, 12), 0, dpToPx(activity, 12), 0);
        button.setTextColor(isTemplate3 ? DARK_TEXT_PRIMARY : Color.WHITE);
        button.setBackground(isTemplate3 ?
                createRoundedTransparentDrawable(DARK_PRIMARY_COLOR) :
                createRoundedBackgroundDrawable(PRIMARY_COLOR));

        button.setOnFocusChangeListener((v, hasFocus) -> {
            int color = hasFocus ? (isTemplate3 ? DARK_SECONDARY_COLOR : PRIMARY_DARK) :
                    (isTemplate3 ? DARK_PRIMARY_COLOR : PRIMARY_COLOR);
            v.setBackground(isTemplate3 ? createRoundedTransparentDrawable(color) : createRoundedBackgroundDrawable(color));
        });

        return button;
    }

    private static void showSearchSourceChooser(LinearLayout tabContainer,
                                                LinearLayout resultContainer,
                                                java.util.Map<String, List<DanmakuItem>> groupedResults,
                                                List<String> tabs,
                                                int selectedIndex,
                                                Activity activity,
                                                AlertDialog parentDialog,
                                                boolean isTemplate3) {
        if (tabs == null || tabs.isEmpty()) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        LinearLayout mainLayout = new LinearLayout(activity);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setBackgroundColor(isTemplate3 ? DARK_BG_PRIMARY : BACKGROUND_WHITE);
        mainLayout.setPadding(dpToPx(activity, 14), dpToPx(activity, 12), dpToPx(activity, 14), dpToPx(activity, 12));

        TextView title = new TextView(activity);
        title.setText("选择来源（共 " + tabs.size() + " 个来源）");
        title.setTextSize(16);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setTextColor(isTemplate3 ? DARK_TEXT_PRIMARY : PRIMARY_COLOR);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dpToPx(activity, 8));
        mainLayout.addView(title);

        ScrollView scrollView = new ScrollView(activity);
        GridLayout gridLayout = new GridLayout(activity);
        DisplayMetrics displayMetrics = activity.getResources().getDisplayMetrics();
        int screenWidthDp = (int) (displayMetrics.widthPixels / displayMetrics.density);
        int columns = screenWidthDp >= 1200 ? 4 : (screenWidthDp >= 760 ? 3 : (screenWidthDp >= 520 ? 2 : 1));
        gridLayout.setColumnCount(columns);
        gridLayout.setUseDefaultMargins(false);
        gridLayout.setPadding(0, 0, 0, 0);
        scrollView.addView(gridLayout);

        final AlertDialog chooserDialog = builder.setView(mainLayout).create();
        if (isTemplate3 && chooserDialog.getWindow() != null) {
            chooserDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        }

        int selected = Math.max(0, Math.min(selectedIndex, tabs.size() - 1));
        for (int i = 0; i < tabs.size(); i++) {
            String tabName = tabs.get(i);
            Button sourceBtn = createSearchSourceChoiceButton(activity, tabName, i == selected, isTemplate3);

            GridLayout.LayoutParams sourceParams = new GridLayout.LayoutParams();
            sourceParams.height = dpToPx(activity, 42);
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                sourceParams.width = 0;
                sourceParams.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
                sourceParams.rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            } else {
                sourceParams.width = dpToPx(activity, 180);
            }
            int margin = dpToPx(activity, 4);
            sourceParams.setMargins(margin, margin, margin, margin);
            sourceBtn.setLayoutParams(sourceParams);

            final int targetIndex = i;
            sourceBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    chooserDialog.dismiss();
                    renderSearchSourceSelector(tabContainer, resultContainer, groupedResults, tabs,
                            targetIndex, activity, parentDialog, isTemplate3);
                }
            });
            gridLayout.addView(sourceBtn);
        }
        mainLayout.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        safeShowDialog(activity, chooserDialog);
        android.view.Window window = chooserDialog.getWindow();
        if (window != null) {
            android.view.WindowManager.LayoutParams lp = new android.view.WindowManager.LayoutParams();
            lp.copyFrom(window.getAttributes());
            lp.width = (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.76f);
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            window.setAttributes(lp);
        }
    }

    private static Button createSearchSourceChoiceButton(Activity activity,
                                                        String title,
                                                        boolean isActive,
                                                        boolean isTemplate3) {
        Button button = new Button(activity);
        button.setText(title);
        button.setTextSize(13);
        button.setTypeface(null, android.graphics.Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setSingleLine(true);
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setAllCaps(false);
        button.setFocusable(true);
        button.setFocusableInTouchMode(true);
        button.setPadding(dpToPx(activity, 8), 0, dpToPx(activity, 8), 0);
        button.setTag(Boolean.valueOf(isActive));
        updateSearchSourceChoiceStyle(button, isActive, isTemplate3, false);

        button.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                Object tag = v.getTag();
                boolean active = tag instanceof Boolean && (Boolean) tag;
                updateSearchSourceChoiceStyle((Button) v, active, isTemplate3, hasFocus);
            }
        });
        return button;
    }

    private static void updateSearchSourceChoiceStyle(Button button, boolean isActive, boolean isTemplate3, boolean hasFocus) {
        int color;
        if (isTemplate3) {
            color = hasFocus ? DARK_PRIMARY_DARK : (isActive ? DARK_PRIMARY_COLOR : DARK_BG_TERTIARY);
            button.setTextColor(DARK_TEXT_PRIMARY);
            button.setBackground(createRoundedTransparentDrawable(color));
        } else {
            color = hasFocus ? PRIMARY_DARK : (isActive ? PRIMARY_COLOR : 0xFFE8E8E8);
            button.setTextColor(isActive || hasFocus ? Color.WHITE : TEXT_PRIMARY);
            button.setBackground(createRoundedBackgroundDrawable(color));
        }
    }

    private static void attachSearchPagerNavigation(Button centerButton,
                                                    Runnable prevAction,
                                                    Runnable nextAction,
                                                    Runnable chooseAction,
                                                    boolean canCycle) {
        centerButton.setOnClickListener(v -> {
            if (chooseAction != null) chooseAction.run();
        });

        centerButton.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_UP) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && canCycle) {
                prevAction.run();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && canCycle) {
                nextAction.run();
                return true;
            }
            return false;
        });

        final float[] downX = new float[1];
        centerButton.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                downX[0] = event.getX();
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                float deltaX = event.getX() - downX[0];
                int threshold = dpToPx(v.getContext(), 48);
                if (deltaX > threshold && canCycle) {
                    prevAction.run();
                } else if (deltaX < -threshold && canCycle) {
                    nextAction.run();
                } else {
                    v.performClick();
                }
                return true;
            }
            return true;
        });
    }


    // 为指定页签显示结果
    private static void showResultsForTab(LinearLayout resultContainer, List<DanmakuItem> items,
                                          Activity activity, AlertDialog dialog) {
        resultContainer.removeAllViews();
        currentItems = items;

        if (items == null || items.isEmpty()) {
            TextView empty = new TextView(activity);
            empty.setText("该来源下无结果");
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, 20, 0, 20);
            resultContainer.addView(empty);
            return;
        }

        // 按 animeTitle 分组
        java.util.Map<String, List<DanmakuItem>> animeGroups = new java.util.HashMap<>();
        for (DanmakuItem item : items) {
            String animeTitle = item.animeTitle != null ? item.animeTitle : item.title;
            if (!animeGroups.containsKey(animeTitle)) {
                animeGroups.put(animeTitle, new java.util.ArrayList<>());
            }
            animeGroups.get(animeTitle).add(item);
        }

        // 检查哪些分组包含上次使用的弹幕URL
        java.util.Set<String> groupsWithLastUrl = new java.util.HashSet<>();
        if (DanmakuManager.lastDanmakuUrl != null) {
            for (java.util.Map.Entry<String, List<DanmakuItem>> entry : animeGroups.entrySet()) {
                String animeTitle = entry.getKey();
                List<DanmakuItem> animeItems = entry.getValue();
                for (DanmakuItem item : animeItems) {
                    if (item.getDanmakuUrl() != null && item.getDanmakuUrl().equals(DanmakuManager.lastDanmakuUrl)) {
                        groupsWithLastUrl.add(animeTitle);
                        break;
                    }
                }
            }
        }

        // 用于跟踪当前选中的分组按钮
        final java.util.Map<String, Button> groupButtons = new java.util.HashMap<>();
        final java.util.List<Button> resultButtons = new java.util.ArrayList<>();
        final String[] highlightedDanmakuUrl = new String[]{DanmakuManager.lastDanmakuUrl};
        final boolean[] suppressGroupSelectionClear = new boolean[]{false};
        java.util.List<String> animeTitles = new java.util.ArrayList<>(animeGroups.keySet());
        sortAnimeTitlesByKeyword(animeTitles);

        DanmakuConfig config = DanmakuConfigManager.getConfig(activity);
        boolean isTemplate3 = config.isDarkGridDanmakuStyle();
        boolean useGrid = config.isGridDanmakuStyle();

        if (useGrid) {
            // 使用网格布局
            for (int groupIndex = 0; groupIndex < animeTitles.size(); groupIndex++) {
                String animeTitle = animeTitles.get(groupIndex);
                List<DanmakuItem> animeItems = animeGroups.get(animeTitle);

                // 创建分组按钮
                Button groupBtn = new Button(activity);
                groupBtn.setText(buildGroupHeaderTitle(animeTitle, animeItems.size(), false));
                groupBtn.setPadding(dpToPx(activity, 20), dpToPx(activity, 12), dpToPx(activity, 20), dpToPx(activity, 12));
                groupBtn.setTextSize(14);
                groupBtn.setTypeface(null, android.graphics.Typeface.BOLD);
                groupBtn.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams groupParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                groupParams.setMargins(0, dpToPx(activity, 6), 0, dpToPx(activity, 3));
                groupBtn.setLayoutParams(groupParams);

                applyGroupHeaderStyle(groupBtn, groupsWithLastUrl.contains(animeTitle), isTemplate3, false);

                groupBtn.setClickable(true);
                groupBtn.setFocusable(true);

                // 保存按钮引用，用于管理选中状态
                groupButtons.put(animeTitle, groupBtn);

                // 添加焦点效果
                groupBtn.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                    @Override
                    public void onFocusChange(View v, boolean hasFocus) {
                        Button button = (Button) v;
                        String title = null;

                        // 找到对应的标题
                        for (java.util.Map.Entry<String, Button> entry : groupButtons.entrySet()) {
                            if (entry.getValue() == v) {
                                title = entry.getKey();
                                break;
                            }
                        }

                        if (hasFocus) {
                            applyGroupHeaderStyle(button, true, isTemplate3, true);
                        } else {
                            // 失去焦点时，恢复到原始选中状态颜色
                            applyGroupHeaderStyle(button, groupsWithLastUrl.contains(title), isTemplate3, false);
                        }
                    }
                });

                // 添加展开/收起状态标记和网格容器的引用
                Object[] stateInfo = new Object[]{0, 0, null}; // [isExpanded(0/1), childCount, gridContainer]
                groupBtn.setTag(stateInfo);

                // 点击分组按钮展开/收起内容
                groupBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // 点击时更新选中状态
                        for (java.util.Map.Entry<String, Button> entry : groupButtons.entrySet()) {
                            Button otherBtn = entry.getValue();
                            if (otherBtn == v) {
                                applyGroupHeaderStyle(otherBtn, true, isTemplate3, false);
                                groupsWithLastUrl.clear();
                                groupsWithLastUrl.add(entry.getKey());
                            } else {
                                applyGroupHeaderStyle(otherBtn, false, isTemplate3, false);
                            }
                        }

                        Object[] currentStateInfo = (Object[]) groupBtn.getTag();
                        boolean isExpanded = (Integer) currentStateInfo[0] == 1;
                        GridLayout gridContainer = (GridLayout) currentStateInfo[2];

                        if (isExpanded) {
                            // 收起内容
                            int groupBtnIndex = resultContainer.indexOfChild(groupBtn);
                            if (groupBtnIndex + 1 < resultContainer.getChildCount()) {
                                resultContainer.removeViewAt(groupBtnIndex + 1);
                            }

                            currentStateInfo[0] = 0; // 未展开
                            currentStateInfo[1] = 0; // 子项数量
                            currentStateInfo[2] = null; // 清空网格容器引用
                            groupBtn.setText(buildGroupHeaderTitle(animeTitle, animeItems.size(), false));
                        } else {
                            // 展开内容 - 创建网格布局
                            int groupBtnIndex = resultContainer.indexOfChild(groupBtn);

                            sortResults(animeItems, isReversed);

                            // 创建网格布局容器
                            GridLayout gridLayout = new GridLayout(activity);
                            // 根据屏幕宽度动态计算列数
                            DisplayMetrics displayMetrics = activity.getResources().getDisplayMetrics();
                            int screenWidthPx = displayMetrics.widthPixels;
                            int screenWidthDp = (int) (screenWidthPx / displayMetrics.density);
                            int columns = Math.max(3, screenWidthDp / 120); // 每列约120dp
                            gridLayout.setColumnCount(columns);
                            gridLayout.setRowCount(GridLayout.UNDEFINED); // 行数自动计算
                            gridLayout.setUseDefaultMargins(false);
                            gridLayout.setPadding(dpToPx(activity, 20), dpToPx(activity, 12),
                                    dpToPx(activity, 20), dpToPx(activity, 12));
                            gridLayout.setBackgroundColor(Color.TRANSPARENT);
                            LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                            gridParams.setMargins(dpToPx(activity, 36), 0, dpToPx(activity, 36), dpToPx(activity, 8));
                            gridLayout.setLayoutParams(gridParams);

                            // 为每个剧集创建网格按钮
                            for (DanmakuItem item : animeItems) {
                                Button gridItem = isTemplate3 ? createDarkGridResultButton(activity, item, dialog) : createGridResultButton(activity, item, dialog);
                                gridLayout.addView(gridItem);
                            }

                            // 将网格布局添加到分组按钮后面
                            resultContainer.addView(gridLayout, groupBtnIndex + 1);

                            currentStateInfo[0] = 1; // 已展开
                            currentStateInfo[1] = animeItems.size(); // 子项数量
                            currentStateInfo[2] = gridLayout; // 保存网格容器引用
                            groupBtn.setText(buildGroupHeaderTitle(animeTitle, animeItems.size(), true));
                        }
                        groupBtn.setTag(currentStateInfo);

                        if (resultContainer.getParent() instanceof ScrollView) {
                            ScrollView scrollView = (ScrollView) resultContainer.getParent();
                            scrollView.post(new Runnable() {
                                @Override
                                public void run() {
                                    int scrollY = resultContainer.getTop() + groupBtn.getTop();
                                    scrollView.smoothScrollTo(0, scrollY);
                                }
                            });
                        }
                    }
                });

                resultContainer.addView(groupBtn);

                // 如果包含上次使用的URL，自动展开
                if (groupsWithLastUrl.contains(animeTitle)) {
                    groupBtn.post(new Runnable() {
                        @Override
                        public void run() {
                            groupBtn.performClick();
                            // 滚动到包含上次使用弹幕的项
                            resultContainer.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    Object[] stateInfo = (Object[]) groupBtn.getTag();
                                    GridLayout gridContainer = (GridLayout) stateInfo[2];
                                    if (gridContainer != null) {
                                        // 在网格中寻找包含lastDanmakuUrl的按钮
                                        for (int i = 0; i < gridContainer.getChildCount(); i++) {
                                            View child = gridContainer.getChildAt(i);
                                            if (child instanceof Button && child.getTag() instanceof DanmakuItem) {
                                                DanmakuItem item = (DanmakuItem) child.getTag();
                                                if (item.getDanmakuUrl() != null &&
                                                        item.getDanmakuUrl().equals(DanmakuManager.lastDanmakuUrl)) {
                                                    // 请求焦点
                                                    child.requestFocus();
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                }
                            }, 100);
                        }
                    });
                }
            }
        } else {
            // 使用列表布局 (兼容旧版本或默认模板)
            for (int groupIndex = 0; groupIndex < animeTitles.size(); groupIndex++) {
                String animeTitle = animeTitles.get(groupIndex);
                List<DanmakuItem> animeItems = animeGroups.get(animeTitle);

                if (animeItems.size() == 1) {
                    DanmakuItem item = animeItems.get(0);
                    Button resultItem = createResultButton(activity, item, dialog, isTemplate3, highlightedDanmakuUrl, resultButtons);
                    resultContainer.addView(resultItem);
                } else {
                    Button groupBtn = new Button(activity);
                    groupBtn.setText(buildGroupHeaderTitle(animeTitle, animeItems.size(), false));
                    groupBtn.setPadding(dpToPx(activity, 20), dpToPx(activity, 10), dpToPx(activity, 20), dpToPx(activity, 10));
                    groupBtn.setTextSize(14);
                    groupBtn.setTypeface(null, android.graphics.Typeface.BOLD);
                    groupBtn.setGravity(Gravity.CENTER);
                    LinearLayout.LayoutParams groupParams = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    groupParams.setMargins(0, dpToPx(activity, 6), 0, dpToPx(activity, 3));
                    groupBtn.setLayoutParams(groupParams);

                    applyGroupHeaderStyle(groupBtn, groupsWithLastUrl.contains(animeTitle), isTemplate3, false);

                    groupBtn.setClickable(true);
                    groupBtn.setFocusable(true);

                    groupButtons.put(animeTitle, groupBtn);

                    groupBtn.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                        @Override
                        public void onFocusChange(View v, boolean hasFocus) {
                            Button button = (Button) v;
                            String title = null;

                            for (java.util.Map.Entry<String, Button> entry : groupButtons.entrySet()) {
                                if (entry.getValue() == v) {
                                    title = entry.getKey();
                                    break;
                                }
                            }

                            if (hasFocus) {
                                applyGroupHeaderStyle(button, true, isTemplate3, true);
                            } else {
                                applyGroupHeaderStyle(button, groupsWithLastUrl.contains(title), isTemplate3, false);
                            }
                        }
                    });

                    Object[] stateInfo = new Object[]{0, null};
                    groupBtn.setTag(stateInfo);

                    groupBtn.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (!suppressGroupSelectionClear[0]) {
                                highlightedDanmakuUrl[0] = "";
                                refreshResultButtonStyles(resultButtons, highlightedDanmakuUrl[0], isTemplate3);
                            }
                            for (java.util.Map.Entry<String, Button> entry : groupButtons.entrySet()) {
                                Button otherBtn = entry.getValue();
                                if (otherBtn == v) {
                                    applyGroupHeaderStyle(otherBtn, true, isTemplate3, false);
                                    groupsWithLastUrl.clear();
                                    groupsWithLastUrl.add(entry.getKey());
                                } else {
                                    applyGroupHeaderStyle(otherBtn, false, isTemplate3, false);
                                }
                            }

                            Object[] currentStateInfo = (Object[]) groupBtn.getTag();
                            boolean isExpanded = (Integer) currentStateInfo[0] == 1;

                            if (isExpanded) {
                                View childContainer = (View) currentStateInfo[1];
                                if (childContainer != null) {
                                    resultContainer.removeView(childContainer);
                                }
                                currentStateInfo[0] = 0;
                                currentStateInfo[1] = null;
                                groupBtn.setText(buildGroupHeaderTitle(animeTitle, animeItems.size(), false));
                            } else {
                                int buttonIndex = resultContainer.indexOfChild(groupBtn);
                                sortResults(animeItems, isReversed);
                                LinearLayout childContainer = createExpandedResultContainer(activity, isTemplate3);
                                for (int i = 0; i < animeItems.size(); i++) {
                                    DanmakuItem item = animeItems.get(i);
                                    Button subItem = createResultButton(activity, item, dialog, isTemplate3, highlightedDanmakuUrl, resultButtons);
                                    subItem.setPadding(dpToPx(activity, 14), dpToPx(activity, 8), dpToPx(activity, 14), dpToPx(activity, 8));
                                    LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                                    subParams.setMargins(0, dpToPx(activity, 4), 0, dpToPx(activity, 4));
                                    subItem.setLayoutParams(subParams);
                                    childContainer.addView(subItem);
                                }
                                resultContainer.addView(childContainer, buttonIndex + 1);
                                currentStateInfo[0] = 1;
                                currentStateInfo[1] = childContainer;
                                groupBtn.setText(buildGroupHeaderTitle(animeTitle, animeItems.size(), true));
                            }
                            groupBtn.setTag(currentStateInfo);

                            if (resultContainer.getParent() instanceof ScrollView) {
                                ScrollView scrollView = (ScrollView) resultContainer.getParent();
                                scrollView.post(() -> {
                                    int scrollY = resultContainer.getTop() + groupBtn.getTop();
                                    scrollView.smoothScrollTo(0, scrollY);
                                });
                            }
                        }
                    });

                    resultContainer.addView(groupBtn);

                    if (groupsWithLastUrl.contains(animeTitle)) {
                        groupBtn.post(() -> {
                            suppressGroupSelectionClear[0] = true;
                            groupBtn.performClick();
                            suppressGroupSelectionClear[0] = false;
                            resultContainer.post(() -> {
                                Object[] expandedState = (Object[]) groupBtn.getTag();
                                View expandedContainer = expandedState[1] instanceof View ? (View) expandedState[1] : resultContainer;
                                View targetView = findResultButtonByDanmakuUrl(expandedContainer, DanmakuManager.lastDanmakuUrl);
                                if (targetView != null) targetView.requestFocus();

                                if (resultContainer.getParent() instanceof ScrollView) {
                                    ScrollView scrollView = (ScrollView) resultContainer.getParent();
                                    View finalTargetView = targetView;
                                    scrollView.post(() -> {
                                        if (finalTargetView != null) {
                                            int scrollY = resultContainer.getTop() + getTopRelativeToAncestor(finalTargetView, resultContainer);
                                            scrollView.smoothScrollTo(0, scrollY);
                                        } else {
                                            int scrollY = resultContainer.getTop() + groupBtn.getTop();
                                            scrollView.smoothScrollTo(0, scrollY);
                                        }
                                    });
                                }
                            });
                        });
                    }
                }
            }
        }

        resultContainer.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
    }

    private static String buildGroupHeaderTitle(String title, int count, boolean expanded) {
        return (expanded ? "[-] " : "[+] ") + title + " (" + count + "集)";
    }

    private static void applyGroupHeaderStyle(Button button, boolean active, boolean isTemplate3, boolean focused) {
        if (isTemplate3) {
            button.setTextColor(DARK_TEXT_PRIMARY);
            int fillColor = focused || active ? 0x33007AFF : DARK_BG_SECONDARY;
            int strokeColor = focused ? DARK_HIGHLIGHT_LIGHT : (active ? DARK_HIGHLIGHT : DARK_BORDER);
            int strokeWidth = focused || active ? 4 : 2;
            button.setBackground(createRoundedStrokeBackgroundDrawable(fillColor, strokeColor, strokeWidth));
        } else {
            button.setTextColor(TEXT_PRIMARY);
            int fillColor = focused || active ? 0xFFEAF2FF : 0xFFE8E8E8;
            int strokeColor = focused ? PRIMARY_DARK : (active ? PRIMARY_COLOR : 0xFFD6D6D6);
            int strokeWidth = focused || active ? 4 : 2;
            button.setBackground(createRoundedStrokeBackgroundDrawable(fillColor, strokeColor, strokeWidth));
        }
    }

    private static LinearLayout createExpandedResultContainer(Activity activity, boolean isTemplate3) {
        LinearLayout childContainer = new LinearLayout(activity);
        childContainer.setOrientation(LinearLayout.VERTICAL);
        childContainer.setPadding(0, dpToPx(activity, 2), 0, dpToPx(activity, 2));
        childContainer.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(dpToPx(activity, 36), 0, dpToPx(activity, 36), 0);
        childContainer.setLayoutParams(params);
        return childContainer;
    }

    private static View findResultButtonByDanmakuUrl(View root, String danmakuUrl) {
        if (root == null || TextUtils.isEmpty(danmakuUrl)) return null;
        if (root instanceof Button && root.getTag() instanceof DanmakuItem) {
            DanmakuItem item = (DanmakuItem) root.getTag();
            if (danmakuUrl.equals(item.getDanmakuUrl())) return root;
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View result = findResultButtonByDanmakuUrl(group.getChildAt(i), danmakuUrl);
                if (result != null) return result;
            }
        }
        return null;
    }

    private static int getTopRelativeToAncestor(View view, ViewGroup ancestor) {
        int top = 0;
        View current = view;
        while (current != null && current != ancestor) {
            top += current.getTop();
            android.view.ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return top;
    }

    private static void sortResults(List<DanmakuItem> results, boolean reversed) {
        java.util.Collections.sort(results, new java.util.Comparator<DanmakuItem>() {
            @Override
            public int compare(DanmakuItem item1, DanmakuItem item2) {
                // 基于 epId 进行排序，假设 epId 是 String 或可比较类型 [1]
                if (item1.epId == null || item2.epId == null) return 0;
                int cmp = item1.epId.compareTo(item2.epId);
                return reversed ? -cmp : cmp; // 根据状态决定正序或倒序
            }
        });
    }

    private static void sortAnimeTitlesByKeyword(List<String> titles) {
        final String keyword = normalizeSearchText(currentSearchKeyword);
        if (TextUtils.isEmpty(keyword)) {
            java.util.Collections.sort(titles);
            return;
        }
        java.util.Collections.sort(titles, new java.util.Comparator<String>() {
            @Override
            public int compare(String left, String right) {
                double leftScore = calculateTitleMatchScore(left, keyword);
                double rightScore = calculateTitleMatchScore(right, keyword);
                int scoreCompare = Double.compare(rightScore, leftScore);
                if (scoreCompare != 0) return scoreCompare;

                int lengthCompare = Integer.compare(safeLength(left), safeLength(right));
                if (lengthCompare != 0) return lengthCompare;

                String leftText = left != null ? left : "";
                String rightText = right != null ? right : "";
                return leftText.compareTo(rightText);
            }
        });
    }

    private static double calculateTitleMatchScore(String title, String normalizedKeyword) {
        String normalizedTitle = normalizeSearchText(title);
        if (TextUtils.isEmpty(normalizedTitle) || TextUtils.isEmpty(normalizedKeyword)) return 0.0;
        if (normalizedTitle.equals(normalizedKeyword)) return 3.0;
        if (normalizedTitle.contains(normalizedKeyword)) return 2.0 + normalizedKeyword.length() / (double) normalizedTitle.length();
        if (normalizedKeyword.contains(normalizedTitle)) return 1.8 + normalizedTitle.length() / (double) normalizedKeyword.length();
        return calculateSimilarity(normalizedTitle, normalizedKeyword);
    }

    private static String normalizeSearchText(String text) {
        if (TextUtils.isEmpty(text)) return "";
        return text.replaceAll("(?i)\\s*from\\s*.*$", "")
                .replaceAll("[\\[【].*?[\\]】]", "")
                .replaceAll("\\(\\d{4}\\)", "")
                .replaceAll("\\s+", "")
                .trim()
                .toLowerCase();
    }

    private static double calculateSimilarity(String left, String right) {
        if (left == null) left = "";
        if (right == null) right = "";
        String longer = left;
        String shorter = right;
        if (left.length() < right.length()) {
            longer = right;
            shorter = left;
        }
        int longerLength = longer.length();
        if (longerLength == 0) return 1.0;
        return (longerLength - editDistance(longer, shorter)) / (double) longerLength;
    }

    private static int editDistance(String left, String right) {
        left = left.toLowerCase();
        right = right.toLowerCase();
        int[] costs = new int[right.length() + 1];
        for (int i = 0; i <= left.length(); i++) {
            int lastValue = i;
            for (int j = 0; j <= right.length(); j++) {
                if (i == 0) {
                    costs[j] = j;
                } else if (j > 0) {
                    int newValue = costs[j - 1];
                    if (left.charAt(i - 1) != right.charAt(j - 1)) {
                        newValue = Math.min(Math.min(newValue, lastValue), costs[j]) + 1;
                    }
                    costs[j - 1] = lastValue;
                    lastValue = newValue;
                }
            }
            if (i > 0) costs[right.length()] = lastValue;
        }
        return costs[right.length()];
    }

    private static int safeLength(String text) {
        return text != null ? text.length() : 0;
    }



    private static String buildSearchResultGroupName(Context context, DanmakuItem item) {
        String from = item != null && !TextUtils.isEmpty(item.from) ? item.from.trim() : "默认";
        if (!shouldShowApiSourceLabel(context)) return from;
        return buildApiSourceLabel(context, item) + " · " + from;
    }

    private static String buildResultTitleWithSource(Context context, DanmakuItem item) {
        return item != null ? item.getTitleWithEp() : "";
    }

    private static boolean shouldShowApiSourceLabel(Context context) {
        try {
            DanmakuConfig config = DanmakuConfigManager.getConfig(context);
            return config != null && config.getEnabledApiSources().size() > 1;
        } catch (Exception e) {
            return false;
        }
    }

    private static String buildApiSourceLabel(Context context, DanmakuItem item) {
        if (item != null && !TextUtils.isEmpty(item.apiSourceName)) {
            return item.apiSourceName.trim();
        }
        return buildApiSourceLabel(context, item != null ? item.apiBase : null);
    }

    private static String buildApiSourceLabel(Context context, String apiBase) {
        String normalized = DanmakuApiSource.normalizeUrl(apiBase);
        if (TextUtils.isEmpty(normalized)) return "未知源";

        try {
            DanmakuConfig config = DanmakuConfigManager.getConfig(context);
            if (config != null) {
                List<DanmakuApiSource> sources = config.getApiSources();
                for (int i = 0; i < sources.size(); i++) {
                    DanmakuApiSource source = sources.get(i);
                    if (source != null && normalized.equals(DanmakuApiSource.normalizeUrl(source.url))) {
                        return source.getDisplayName("源" + (i + 1));
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return buildShortApiSourceLabel(normalized);
    }

    private static String buildShortApiSourceLabel(String url) {
        if (TextUtils.isEmpty(url)) return "未知源";
        String label = url;
        int schemeIndex = label.indexOf("://");
        if (schemeIndex >= 0) label = label.substring(schemeIndex + 3);
        int slashIndex = label.indexOf('/');
        if (slashIndex > 0) label = label.substring(0, slashIndex);
        return TextUtils.isEmpty(label) ? "未知源" : label;
    }

    private static void applyResultButtonStyle(Button button, boolean selected, boolean isTemplate3, boolean focused) {
        if (isTemplate3) {
            int fillColor = selected || focused ? DARK_TERTIARY_COLOR : DARK_BG_TERTIARY;
            int strokeColor = focused ? DARK_TERTIARY_DARK : (selected ? DARK_TERTIARY_COLOR : DARK_BORDER);
            int strokeWidth = focused || selected ? 4 : 2;
            button.setTextColor(DARK_TEXT_PRIMARY);
            button.setBackground(createRoundedStrokeBackgroundDrawable(fillColor, strokeColor, strokeWidth));
        } else {
            int fillColor = selected ? 0xFFEAF8EF : 0xFFF0F0F0;
            int strokeColor = focused ? TERTIARY_DARK : (selected ? TERTIARY_COLOR : 0xFFD6D6D6);
            int strokeWidth = focused || selected ? 4 : 2;
            button.setTextColor(selected ? 0xFF1F7A3A : TEXT_PRIMARY);
            button.setBackground(createRoundedStrokeBackgroundDrawable(fillColor, strokeColor, strokeWidth));
        }
    }

    private static void refreshResultButtonStyles(List<Button> buttons, String highlightedUrl, boolean isTemplate3) {
        if (buttons == null) return;
        for (Button button : buttons) {
            if (button == null || !(button.getTag() instanceof DanmakuItem)) continue;
            DanmakuItem item = (DanmakuItem) button.getTag();
            String itemUrl = item.getDanmakuUrl();
            boolean selected = !TextUtils.isEmpty(highlightedUrl) && highlightedUrl.equals(itemUrl);
            applyResultButtonStyle(button, selected, isTemplate3, button.hasFocus());
        }
    }

    // 创建结果按钮的辅助方法 - 改进版本
    private static Button createResultButton(Activity activity,
                                             DanmakuItem item,
                                             AlertDialog dialog,
                                             boolean isTemplate3,
                                             String[] highlightedDanmakuUrl,
                                             List<Button> resultButtons) {
        Button resultItem = new Button(activity);
        resultItem.setFocusable(true);
        resultItem.setFocusableInTouchMode(true);
        resultItem.setClickable(true);
        resultItem.setText(buildResultTitleWithSource(activity, item));
        resultItem.setTextSize(13);
        resultItem.setPadding(dpToPx(activity, 14), dpToPx(activity, 10), dpToPx(activity, 14), dpToPx(activity, 10));
        resultItem.setGravity(Gravity.CENTER);

        String currentDanmakuUrl = item.getDanmakuUrl();
        String highlightedUrl = highlightedDanmakuUrl != null ? highlightedDanmakuUrl[0] : DanmakuManager.lastDanmakuUrl;
        boolean isSelected = currentDanmakuUrl != null && currentDanmakuUrl.equals(highlightedUrl);

        applyResultButtonStyle(resultItem, isSelected, isTemplate3, false);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dpToPx(activity, 6), 0, dpToPx(activity, 6));
        resultItem.setLayoutParams(params);

        resultItem.setTag(item);
        if (resultButtons != null) resultButtons.add(resultItem);

        resultItem.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                DanmakuItem item_tag = (DanmakuItem) v.getTag();
                String danmakuUrl = item_tag.getDanmakuUrl();
                String highlightedUrl = highlightedDanmakuUrl != null ? highlightedDanmakuUrl[0] : DanmakuManager.lastDanmakuUrl;
                boolean isCurrentlySelected = danmakuUrl != null && danmakuUrl.equals(highlightedUrl);

                if (hasFocus) {
                    applyResultButtonStyle((Button) v, isCurrentlySelected, isTemplate3, true);
                } else {
                    applyResultButtonStyle((Button) v, isCurrentlySelected, isTemplate3, false);
                }
            }
        });

        resultItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v1) {
                DanmakuItem selected = (DanmakuItem) v1.getTag();
                if (highlightedDanmakuUrl != null) {
                    highlightedDanmakuUrl[0] = selected.getDanmakuUrl();
                    refreshResultButtonStyles(resultButtons, highlightedDanmakuUrl[0], isTemplate3);
                }
                // 记录弹幕URL
                DanmakuSpider.recordDanmakuUrl(selected, false);
                LeoDanmakuService.pushDanmakuDirect(selected, activity, false);
                dialog.dismiss();
            }
        });

        return resultItem;
    }

    // 创建网格布局结果按钮的辅助方法
    private static Button createGridResultButton(Activity activity, DanmakuItem item, AlertDialog dialog) {
        Button resultItem = new Button(activity);
        resultItem.setFocusable(true);
        resultItem.setFocusableInTouchMode(true);
        resultItem.setClickable(true);

        // 缩短文本显示，适合网格布局
        String displayText = DanmakuScanner.extractEpisodeNum(item.epTitle);
        if (TextUtils.isEmpty(displayText)) {
            // 安全地获取分割后的第二部分，避免数组越界
            String[] parts = item.epTitle != null ? item.epTitle.split(" ") : new String[0];
            if (parts.length > 1) {
                displayText = parts[1];
            } else if (parts.length > 0) {
                displayText = parts[0]; // 如果只有一个部分，使用第一部分
            } else {
                displayText = item.epTitle != null ? item.epTitle : "未知"; // 如果没有分割部分，使用原字符串或默认值
            }
        }

        resultItem.setText(displayText);
        resultItem.setTextSize(13); // 增大字号

        // 设置内边距
        int padding = dpToPx(activity, 10);
        resultItem.setPadding(padding, padding, padding, padding);

        // 设置单行显示，超出部分...省略
        resultItem.setSingleLine(true);
        resultItem.setEllipsize(TextUtils.TruncateAt.END);

        // 设置文本居中
        resultItem.setGravity(Gravity.CENTER);

        // 安全设置工具提示（完整标题）- 仅在 API 26+ 可用
        // 当按钮获得焦点或长按时，会显示完整的标题
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            resultItem.setTooltipText(buildResultTitleWithSource(activity, item));
        }

        // 设置圆角背景 - 第三层级：绿色
        String currentDanmakuUrl = item.getDanmakuUrl();
        if (currentDanmakuUrl != null && currentDanmakuUrl.equals(DanmakuManager.lastDanmakuUrl)) {
            // 高亮显示 - 使用绿色背景
            resultItem.setBackground(createRoundedBackgroundDrawable(TERTIARY_COLOR));
            resultItem.setTextColor(Color.WHITE);
        } else {
            // 普通显示
            resultItem.setBackground(createRoundedBackgroundDrawable(0xFFF0F0F0));
            resultItem.setTextColor(TEXT_PRIMARY);
        }

        // 设置网格布局参数
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.height = GridLayout.LayoutParams.WRAP_CONTENT; // 高度自适应

        // 兼容安卓7.0以下版本
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            // 在Android 7.0 (API 24) 及以上版本使用权重实现等宽
            params.width = 0;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        } else {
            // 在旧版本上使用固定宽度，以兼容旧版GridLayout
            params.width = dpToPx(activity, 80); // 约4个字符宽度
        }

        // 设置外边距
        int margin = dpToPx(activity, 6);
        params.setMargins(margin, margin, margin, margin);

        resultItem.setLayoutParams(params);
        resultItem.setTag(item);

        resultItem.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    // 获得焦点时的高亮效果 - 深绿色
                    v.setBackground(createRoundedBackgroundDrawable(TERTIARY_DARK));
                    ((Button) v).setTextColor(Color.WHITE);
                    v.setScaleX(1.05f);
                    v.setScaleY(1.05f);
                } else {
                    // 失去焦点时的恢复逻辑
                    DanmakuItem item_tag = (DanmakuItem) v.getTag();
                    String danmakuUrl = item_tag.getDanmakuUrl();

                    // 检查是否为上次使用的弹幕URL，如果是则保持高亮状态
                    if (danmakuUrl != null && danmakuUrl.equals(DanmakuManager.lastDanmakuUrl)) {
                        v.setBackground(createRoundedBackgroundDrawable(TERTIARY_COLOR));
                        ((Button) v).setTextColor(Color.WHITE);
                    } else {
                        // 普通状态
                        v.setBackground(createRoundedBackgroundDrawable(0xFFF0F0F0));
                        ((Button) v).setTextColor(TEXT_PRIMARY);
                    }
                    v.setScaleX(1.0f);
                    v.setScaleY(1.0f);
                }
            }
        });

        resultItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v1) {
                DanmakuItem selected = (DanmakuItem) v1.getTag();
                // 记录弹幕URL
                DanmakuSpider.recordDanmakuUrl(selected, false);
                LeoDanmakuService.pushDanmakuDirect(selected, activity, false);
                dialog.dismiss();
            }
        });

        // 长按显示完整标题 - 这个功能在所有版本上都可用，作为低版本API的兼容
        resultItem.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                DanmakuItem item = (DanmakuItem) v.getTag();
                Utils.safeShowToast(activity, buildResultTitleWithSource(activity, item),  true);
                return true;
            }
        });

        return resultItem;
    }

    public static void showQRCodeDialog(Activity activity, String url) {
        activity.runOnUiThread(() -> {
            try {
                AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                LinearLayout mainLayout = new LinearLayout(activity);
                mainLayout.setOrientation(LinearLayout.VERTICAL);
                mainLayout.setGravity(Gravity.CENTER);
                mainLayout.setPadding(dpToPx(activity, 20), dpToPx(activity, 20), dpToPx(activity, 20), dpToPx(activity, 20));

                ImageView qrCodeView = new ImageView(activity);
                mainLayout.addView(qrCodeView);

                TextView urlView = new TextView(activity);
                urlView.setText(url);
                urlView.setGravity(Gravity.CENTER);
                urlView.setPadding(0, dpToPx(activity, 10), 0, 0);
                mainLayout.addView(urlView);

                builder.setView(mainLayout);
                AlertDialog dialog = builder.create();

                new Thread(() -> {
                    try {
                        String qrCodeUrl = "https://api.qrserver.com/v1/create-qr-code/?size=400x400&data=" + URLEncoder.encode(url, "UTF-8");
                        try (Response response = OkHttp.newCall(qrCodeUrl, "qrcode")) {
                            if (response.body() != null) {
                                InputStream in = response.body().byteStream();
                                Bitmap bitmap = BitmapFactory.decodeStream(in);
                                activity.runOnUiThread(() -> qrCodeView.setImageBitmap(bitmap));
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }).start();

                safeShowDialog(activity, dialog);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    // 创建透明圆角背景
    private static android.graphics.drawable.Drawable createRoundedTransparentDrawable(int color) {
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(12);
        return drawable;
    }

    // 创建深色主题网格布局结果按钮
    private static Button createDarkGridResultButton(Activity activity, DanmakuItem item, AlertDialog dialog) {
        Button resultItem = new Button(activity);
        resultItem.setFocusable(true);
        resultItem.setFocusableInTouchMode(true);
        resultItem.setClickable(true);

        // 缩短文本显示，适合网格布局 (统一为模板二的逻辑)
        String displayText = DanmakuScanner.extractEpisodeNum(item.epTitle);
        if (TextUtils.isEmpty(displayText)) {
            // 安全地获取分割后的第二部分，避免数组越界
            String[] parts = item.epTitle != null ? item.epTitle.split(" ") : new String[0];
            if (parts.length > 1) {
                displayText = parts[1];
            } else if (parts.length > 0) {
                displayText = parts[0]; // 如果只有一个部分，使用第一部分
            } else {
                displayText = item.epTitle != null ? item.epTitle : "未知"; // 如果没有分割部分，使用原字符串或默认值
            }
        }

        resultItem.setText(displayText);
        resultItem.setTextSize(13); // 增大字号
        resultItem.setTextColor(DARK_TEXT_PRIMARY); // 白色文字

        // 设置内边距
        int padding = dpToPx(activity, 10);
        resultItem.setPadding(padding, padding, padding, padding);

        // 设置单行显示，超出部分...省略 (统一为模板二的逻辑)
        resultItem.setSingleLine(true);
        resultItem.setEllipsize(TextUtils.TruncateAt.END);

        // 设置文本居中
        resultItem.setGravity(Gravity.CENTER);

        // 安全设置工具提示（完整标题）- 仅在 API 26+ 可用
        // 添加版本检查避免在低版本上崩溃
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            resultItem.setTooltipText(buildResultTitleWithSource(activity, item));
        }

        // 设置圆角背景 - 第三层级：绿色
        String currentDanmakuUrl = item.getDanmakuUrl();
        if (currentDanmakuUrl != null && currentDanmakuUrl.equals(DanmakuManager.lastDanmakuUrl)) {
            // 高亮显示 - 使用绿色半透明背景
            resultItem.setBackground(createRoundedTransparentDrawable(DARK_TERTIARY_COLOR));
            resultItem.setTextColor(DARK_TEXT_PRIMARY);
        } else {
            // 普通显示 - 深灰半透明背景
            resultItem.setBackground(createRoundedTransparentDrawable(DARK_BG_TERTIARY));
            resultItem.setTextColor(DARK_TEXT_PRIMARY);
        }

        // 设置网格布局参数
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.height = GridLayout.LayoutParams.WRAP_CONTENT; // 高度自适应

        // 兼容安卓7.0以下版本
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // 在Android 7.0 (API 24) 及以上版本使用权重实现等宽
            params.width = 0;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        } else {
            // 在旧版本上使用固定宽度，以兼容旧版GridLayout
            params.width = dpToPx(activity, 80); // 约4个字符宽度
        }

        // 设置外边距
        int margin = dpToPx(activity, 6);
        params.setMargins(margin, margin, margin, margin);

        resultItem.setLayoutParams(params);
        resultItem.setTag(item);

        resultItem.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    // 获得焦点时的高亮效果 - 深绿色
                    v.setBackground(createRoundedTransparentDrawable(DARK_TERTIARY_DARK));
                    ((Button) v).setTextColor(DARK_TEXT_PRIMARY);
                    v.setScaleX(1.05f);
                    v.setScaleY(1.05f);
                } else {
                    // 失去焦点时的恢复逻辑
                    DanmakuItem item_tag = (DanmakuItem) v.getTag();
                    String danmakuUrl = item_tag.getDanmakuUrl();

                    // 检查是否为上次使用的弹幕URL，如果是则保持高亮状态
                    if (danmakuUrl != null && danmakuUrl.equals(DanmakuManager.lastDanmakuUrl)) {
                        v.setBackground(createRoundedTransparentDrawable(DARK_TERTIARY_COLOR));
                        ((Button) v).setTextColor(DARK_TEXT_PRIMARY);
                    } else {
                        // 普通状态
                        v.setBackground(createRoundedTransparentDrawable(DARK_BG_TERTIARY));
                        ((Button) v).setTextColor(DARK_TEXT_PRIMARY);
                    }
                    v.setScaleX(1.0f);
                    v.setScaleY(1.0f);
                }
            }
        });

        resultItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v1) {
                DanmakuItem selected = (DanmakuItem) v1.getTag();
                // 记录弹幕URL
                DanmakuSpider.recordDanmakuUrl(selected, false);
                LeoDanmakuService.pushDanmakuDirect(selected, activity, false);
                dialog.dismiss();
            }
        });

        // 长按显示完整标题 - 这个功能在所有版本上都可用
        resultItem.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                DanmakuItem item = (DanmakuItem) v.getTag();
                Utils.safeShowToast(activity, buildResultTitleWithSource(activity, item),  true);
                return true;
            }
        });

        return resultItem;
    }

    // 创建深色主题实心按钮
    private static Button createDarkSolidButton(Activity activity, String text, int backgroundColor) {
        Button button = new Button(activity);
        button.setText(text);
        button.setTextColor(DARK_TEXT_PRIMARY);
        button.setBackground(createRoundedTransparentDrawable(backgroundColor));
        button.setTextSize(14);
        button.setTypeface(null, android.graphics.Typeface.BOLD);

        // 添加焦点效果
        button.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    int focusedColor = darkenColor(backgroundColor, 0.3f);
                    v.setBackground(createRoundedTransparentDrawable(focusedColor));
                    ((Button) v).setTextColor(DARK_TEXT_PRIMARY);
                    v.setScaleX(1.08f);
                    v.setScaleY(1.08f);
                } else {
                    ((Button) v).setBackground(createRoundedTransparentDrawable(backgroundColor));
                    v.setScaleX(1.0f);
                    v.setScaleY(1.0f);
                }
            }
        });

        return button;
    }

    // 颜色加深辅助方法
    private static int darkenColor(int color, float factor) {
        int a = (color >> 24) & 0xFF;
        int r = (int) (((color >> 16) & 0xFF) * (1 - factor));
        int g = (int) (((color >> 8) & 0xFF) * (1 - factor));
        int b = (int) ((color & 0xFF) * (1 - factor));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
