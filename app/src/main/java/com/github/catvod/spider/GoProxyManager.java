package com.github.catvod.spider;

import android.content.Context;
import android.os.Build;
import android.text.TextUtils;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class GoProxyManager {

    private static final ExecutorService executor = Executors.newFixedThreadPool(5);
    static final AtomicBoolean isProxyRunning = new AtomicBoolean(false);
    private static final int PROXY_PORT = 5575;
    private static final String HEALTH_CHECK_URL = "http://127.0.0.1:" + PROXY_PORT + "/health";
    private static String goProxyExecutableName = "";

    public static void initialize(Context context) {
        startGoProxyOnce(context);
    }

    static void startGoProxyOnce(Context context) {
        execute(() -> startGoProxyOnceSync(context));
    }

    static void startGoProxyOnceSync(Context context) {
        synchronized (isProxyRunning) {
            if (isProxyRunning.get()) {
                isProxyRunning.set(false);
            }

            boolean hasSoAssets = hasSoAssets();
            boolean hasBinaryAssets = hasBinaryAssets();

            if (hasSoAssets) {
                boolean soAvailable = GoProxyLibrary.loadLibrary();
                if (!soAvailable) {
                    log("[SO] SO资产存在，但加载失败");
                    isProxyRunning.set(false);
                    return;
                }

                log("[SO] 使用JNI方式启动Go代理");
                try {
                    int result = GoProxyLibrary.startProxy(PROXY_PORT);
                    if (result == 0) {
                        isProxyRunning.set(true);
                        log("[SO] Go代理JNI启动成功");
                        return;
                    }
                    log("[SO] Go代理JNI启动失败, 返回码: " + result);
                } catch (UnsatisfiedLinkError e) {
                    log("[SO] JNI调用失败: " + e.getMessage());
                }
                isProxyRunning.set(false);
                return;
            }

            if (hasBinaryAssets) {
                startGoProxyBinarySync(context);
            } else {
                log("[启动] Go代理所有方式均不可用");
                isProxyRunning.set(false);
            }
        }
    }

    private static void startGoProxyBinarySync(Context context) {
        try {
            if (TextUtils.isEmpty(goProxyExecutableName)) {
                List<String> abs = Arrays.asList(Build.SUPPORTED_ABIS);
                goProxyExecutableName = abs.contains("arm64-v8a") ? "goProxy-arm64" : "goProxy-arm";
            }

            java.io.File file = new java.io.File(context.getCacheDir(), goProxyExecutableName);

            java.lang.Process exec = Runtime.getRuntime().exec("/system/bin/sh");
            try (java.io.DataOutputStream dos = new java.io.DataOutputStream(exec.getOutputStream())) {
                if (!file.exists()) {
                    if (!file.createNewFile()) throw new Exception("创建文件失败 " + file);

                    java.io.InputStream is = java.util.Objects.requireNonNull(Init.get().getClass().getClassLoader()).getResourceAsStream("assets/" + goProxyExecutableName);
                    if (is == null) {
                        throw new Exception("资源文件不存在: assets/" + goProxyExecutableName);
                    }

                    try (java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) {
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = is.read(buffer)) != -1) fos.write(buffer, 0, read);
                    }
                    if (!file.setExecutable(true)) throw new Exception(goProxyExecutableName + " setExecutable is false");
                    dos.writeBytes("chmod 777 " + file.getAbsolutePath() + "\n");
                    dos.flush();
                }

                log("[二进制] 启动 " + file);
                dos.writeBytes("kill $(ps -ef | grep '" + goProxyExecutableName + "' | grep -v grep | awk '{print $2}') 2>/dev/null\n");
                dos.flush();
                dos.writeBytes("sleep 1\n");
                dos.flush();
                java.io.File logFile = new java.io.File(context.getCacheDir(), "goproxy_output.log");
                dos.writeBytes("nohup " + file.getAbsolutePath() + " > " + logFile.getAbsolutePath() + " 2>&1 &\n");
                dos.flush();
                dos.writeBytes("exit\n");
                dos.flush();
            }

            Thread.sleep(3000);

            if (isProxyHealthy()) {
                log("[二进制] Go代理启动成功");
                isProxyRunning.set(true);
            } else {
                log("[二进制] Go代理健康检查失败");
                isProxyRunning.set(false);
            }

        } catch (Exception e) {
            log("[二进制] 启动异常: " + e.getMessage());
            isProxyRunning.set(false);
        }
    }

    public static synchronized boolean isProxyHealthy() {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            final java.util.concurrent.atomic.AtomicBoolean result = new java.util.concurrent.atomic.AtomicBoolean(false);
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
        if (hasSoAssets() && GoProxyLibrary.isLoaded()) {
            try {
                if (GoProxyLibrary.isProxyRunning() == 1) {
                    return true;
                }
            } catch (UnsatisfiedLinkError ignored) {
            }
        }

        try {
            long startTime = System.currentTimeMillis();
            String response = OkHttp.string(HEALTH_CHECK_URL, 1000);
            long elapsed = System.currentTimeMillis() - startTime;

            if (TextUtils.isEmpty(response) || elapsed >= 1000) return false;
            if ("ok".equalsIgnoreCase(response.trim())) return true;

            try {
                JsonObject json = new Gson().fromJson(response, JsonObject.class);
                if (json != null && json.has("status")) {
                    return "healthy".equals(json.get("status").getAsString());
                }
            } catch (Exception ignored) {
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public static void killGoProxy() {
        if (hasSoAssets() && GoProxyLibrary.isLoaded()) {
            try {
                GoProxyLibrary.stopProxy();
                log("[SO] Go代理JNI停止");
            } catch (UnsatisfiedLinkError e) {
                log("[SO] JNI停止失败: " + e.getMessage());
            }
        }

        try {
            if (hasBinaryAssets() && !TextUtils.isEmpty(goProxyExecutableName)) {
                java.lang.Process exec = Runtime.getRuntime().exec("/system/bin/sh");
                try (java.io.DataOutputStream dos = new java.io.DataOutputStream(exec.getOutputStream())) {
                    dos.writeBytes("kill $(ps -ef | grep '" + goProxyExecutableName + "' | grep -v grep | awk '{print $2}') 2>/dev/null\n");
                    dos.flush();
                    dos.writeBytes("exit\n");
                    dos.flush();
                }
                exec.waitFor();
            }
        } catch (Exception e) {
            log("[停止] 二进制停止异常: " + e.getMessage());
        }

        isProxyRunning.set(false);
        log("[停止] Go代理已终止");
    }

    public static boolean isGoProxyAssetExists() {
        return hasSoAssets() || hasBinaryAssets();
    }

    public static boolean hasSoAssets() {
        return checkAssetExists("arm64-v8a/libgoproxy.so") ||
                checkAssetExists("armeabi-v7a/libgoproxy.so");
    }

    public static boolean hasBinaryAssets() {
        try {
            if (TextUtils.isEmpty(goProxyExecutableName)) {
                List<String> abs = Arrays.asList(Build.SUPPORTED_ABIS);
                goProxyExecutableName = abs.contains("arm64-v8a") ? "goProxy-arm64" : "goProxy-arm";
            }
            java.io.InputStream is = java.util.Objects.requireNonNull(Init.get().getClass().getClassLoader()).getResourceAsStream("assets/" + goProxyExecutableName);
            if (is != null) {
                is.close();
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static boolean checkAssetExists(String path) {
        try {
            java.io.InputStream is = Init.get().getClass().getClassLoader().getResourceAsStream("assets/" + path);
            if (is != null) {
                is.close();
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    public static void execute(Runnable runnable) {
        executor.execute(runnable);
    }

    public static void log(String msg) {
        SpiderDebug.log(msg);
    }
}
