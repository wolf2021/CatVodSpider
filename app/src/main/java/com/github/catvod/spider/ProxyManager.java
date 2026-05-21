package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;
import com.github.catvod.crawler.SpiderDebug;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Timer;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ProxyManager {

    public static final int PROXY_TYPE_NONE = 0;
    public static final int PROXY_TYPE_GO = 1;
    public static final int PROXY_TYPE_JAVA = 2;

    public static final int PROXY_PORT = 5575;
    private static final String HEALTH_CHECK_URL = "http://127.0.0.1:" + PROXY_PORT + "/health";

    private static final ExecutorService executor = Executors.newFixedThreadPool(5);
    private static final AtomicBoolean isProxyRunning = new AtomicBoolean(false);
    private static final AtomicInteger activeProxyType = new AtomicInteger(PROXY_TYPE_NONE);

    private static volatile int preferredProxyType = PROXY_TYPE_NONE;
    private static volatile JavaProxyServer javaProxyServer;

    private static final List<String> proxyLogBuffer = new CopyOnWriteArrayList<>();
    private static final int MAX_LOG_SIZE = 1000;

    private static Timer healthCheckTimer;
    private static final Object healthCheckLock = new Object();
    private static long lastSuccessTime = 0;
    private static final long RESTART_DELAY_THRESHOLD = 10000;

    public static void initialize(Context context) {
        int saved = DanmakuConfigManager.loadConfig(context).getProxyType();
        preferredProxyType = saved;

        if (GoProxyManager.isGoProxyAssetExists() && preferredProxyType != PROXY_TYPE_JAVA) {
            GoProxyManager.initialize(context);
            if (GoProxyManager.isProxyRunning.get()) {
                activeProxyType.set(PROXY_TYPE_GO);
                isProxyRunning.set(true);
                log("[初始化] Go代理启动成功");
                startHealthCheck(context);
                return;
            }
            GoProxyManager.killGoProxy();
            log("[降级] Go代理启动失败，自动切换到Java代理");
        }

        startJavaProxy(context);
    }

    public static synchronized boolean startJavaProxy(Context context) {
        stopJavaProxy();
        if (GoProxyManager.isProxyRunning.get()) {
            GoProxyManager.killGoProxy();
        }
        try {
            javaProxyServer = new JavaProxyServer(PROXY_PORT);
            boolean success = javaProxyServer.startServer();
            if (success) {
                activeProxyType.set(PROXY_TYPE_JAVA);
                isProxyRunning.set(true);
                log("[启动] Java代理成功，端口: " + PROXY_PORT);
                startHealthCheck(context);
            } else {
                activeProxyType.set(PROXY_TYPE_NONE);
                isProxyRunning.set(false);
                log("[启动] Java代理失败");
            }
            return success;
        } catch (Exception e) {
            log("[启动] Java代理异常: " + e.getMessage());
            activeProxyType.set(PROXY_TYPE_NONE);
            isProxyRunning.set(false);
            return false;
        }
    }

    public static synchronized void stopJavaProxy() {
        if (javaProxyServer != null) {
            javaProxyServer.stopServer();
            javaProxyServer = null;
        }
        if (activeProxyType.get() == PROXY_TYPE_JAVA) {
            activeProxyType.set(PROXY_TYPE_NONE);
            isProxyRunning.set(false);
        }
    }

    public static void switchToGoProxy(Context context) {
        log("[切换] → Go代理");
        stopJavaProxy();
        stopHealthCheck();
        GoProxyManager.startGoProxyOnce(context.getApplicationContext());
        if (GoProxyManager.isProxyRunning.get()) {
            activeProxyType.set(PROXY_TYPE_GO);
            isProxyRunning.set(true);
            startHealthCheck(context);
        } else {
            log("[降级] Go代理启动失败，回退到Java代理");
            startJavaProxy(context);
        }
        saveProxyType(context, PROXY_TYPE_GO);
    }

    public static void switchToJavaProxy(Context context) {
        log("[切换] → Java代理");
        stopHealthCheck();
        if (activeProxyType.get() == PROXY_TYPE_GO) {
            GoProxyManager.killGoProxy();
        }
        boolean success = startJavaProxy(context);
        if (success) {
            saveProxyType(context, PROXY_TYPE_JAVA);
        }
    }

    public static int getActiveProxyType() {
        return activeProxyType.get();
    }

    public static boolean isProxyRunning() {
        return isProxyRunning.get();
    }

    public static String getProxyTypeName() {
        int type = activeProxyType.get();
        if (type == PROXY_TYPE_GO) return "Go代理";
        if (type == PROXY_TYPE_JAVA) return "Java代理";
        return "未启动";
    }

    public static String getProxyTypeShortName() {
        int type = activeProxyType.get();
        if (type == PROXY_TYPE_GO) return "Go";
        if (type == PROXY_TYPE_JAVA) return "Java";
        return "无";
    }

    public static boolean canSwitchToGoProxy() {
        return GoProxyManager.isGoProxyAssetExists();
    }

    public static boolean canSwitchToJavaProxy() {
        return true;
    }

    private static void startHealthCheck(Context context) {
        synchronized (healthCheckLock) {
            if (healthCheckTimer != null) {
                healthCheckTimer.cancel();
            }
            lastSuccessTime = System.currentTimeMillis();
            healthCheckTimer = new java.util.Timer("ProxyHealthCheckTimer", true);
            healthCheckTimer.schedule(new java.util.TimerTask() {
                @Override
                public void run() {
                    try {
                        if (!isProxyHealthy()) {
                            long currentTime = System.currentTimeMillis();
                            long timeSinceLastSuccess = currentTime - lastSuccessTime;
                            log("[健康] 代理检查失败，距上次成功: " + timeSinceLastSuccess + "ms");
                            if (timeSinceLastSuccess >= RESTART_DELAY_THRESHOLD) {
                                if (isProxyRunning.get()) {
                                    isProxyRunning.set(false);
                                }
                                restartProxyWithFallback(context.getApplicationContext());
                            }
                        } else {
                            lastSuccessTime = System.currentTimeMillis();
                            if (!isProxyRunning.get()) {
                                isProxyRunning.set(true);
                                log("[健康] 检查成功，同步状态为运行中");
                            }
                        }
                    } catch (Exception e) {
                        log("[健康] 检查异常: " + e.getMessage());
                    }
                }
            }, 2000, 5000);
        }
    }

    private static void stopHealthCheck() {
        synchronized (healthCheckLock) {
            if (healthCheckTimer != null) {
                healthCheckTimer.cancel();
                healthCheckTimer = null;
            }
        }
    }

    private static void restartProxyWithFallback(Context context) {
        int currentType = activeProxyType.get();
        log("[重启] 当前类型: " + (currentType == PROXY_TYPE_GO ? "Go" : "Java"));

        if (currentType == PROXY_TYPE_GO) {
            log("[重启] Go代理不健康，尝试重启...");
            GoProxyManager.startGoProxyOnce(context);
            if (GoProxyManager.isProxyRunning.get()) {
                activeProxyType.set(PROXY_TYPE_GO);
                isProxyRunning.set(true);
                log("[重启] Go代理重启成功");
                return;
            }
            log("[降级] Go代理重启失败，切换到Java代理");
            GoProxyManager.killGoProxy();
            startJavaProxy(context);
        } else if (currentType == PROXY_TYPE_JAVA) {
            log("[重启] Java代理不健康，尝试重启...");
            boolean success = startJavaProxy(context);
            if (!success) {
                log("[重启] Java代理重启也失败");
            }
        }
    }

    public static synchronized boolean isProxyHealthy() {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            final AtomicBoolean result = new AtomicBoolean(false);
            final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            executor.execute(() -> {
                try {
                    result.set(performHealthCheck());
                } finally {
                    latch.countDown();
                }
            });
            try {
                latch.await(2000, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return result.get();
        } else {
            return performHealthCheck();
        }
    }

    private static boolean performHealthCheck() {
        int currentType = activeProxyType.get();

        if (currentType == PROXY_TYPE_JAVA && javaProxyServer != null) {
            boolean alive = javaProxyServer.isRunning();
            if (!alive) {
                log("[健康] Java代理: 服务未运行");
                return false;
            }
            try {
                String url = "http://127.0.0.1:" + PROXY_PORT + "/health";
                String response = com.github.catvod.net.OkHttp.string(url, 1000);
                return parseHealthResponse(response);
            } catch (Exception e) {
                log("[健康] Java代理异常: " + e.getMessage());
                return alive;
            }
        }

        return GoProxyManager.isProxyHealthy();
    }

    private static boolean parseHealthResponse(String response) {
        if (TextUtils.isEmpty(response)) return false;
        if ("ok".equalsIgnoreCase(response.trim())) return true;
        try {
            JsonObject json = new Gson().fromJson(response, JsonObject.class);
            if (json != null && json.has("status")) {
                return "healthy".equals(json.get("status").getAsString());
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static void saveProxyType(Context context, int type) {
        DanmakuConfig config = DanmakuConfigManager.getConfig(context);
        config.setProxyType(type);
        DanmakuConfigManager.saveConfig(context, config);
        preferredProxyType = type;
    }

    public static void log(String msg) {
        SpiderDebug.log(msg);
        String time = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
        String newLogEntry = time + " " + Thread.currentThread().getName() + " " + msg;
        proxyLogBuffer.add(newLogEntry);
        if (proxyLogBuffer.size() > MAX_LOG_SIZE) {
            proxyLogBuffer.remove(0);
        }
    }

    public static String getLogContent(boolean reverse) {
        StringBuilder sb = new StringBuilder();
        if (reverse) {
            for (int i = proxyLogBuffer.size() - 1; i >= 0; i--) {
                sb.append(proxyLogBuffer.get(i)).append("\n");
            }
        } else {
            for (String s : proxyLogBuffer) {
                sb.append(s).append("\n");
            }
        }
        return sb.toString();
    }

    public static String getLogContent() {
        return getLogContent(false);
    }

    public static void clearLogs() {
        proxyLogBuffer.clear();
    }
}
