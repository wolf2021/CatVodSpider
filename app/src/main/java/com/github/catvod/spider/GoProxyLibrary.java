package com.github.catvod.spider;

public class GoProxyLibrary {

    private static boolean libraryLoaded = false;
    private static String loadedAbi = "";

    public static boolean loadLibrary() {
        if (libraryLoaded) return true;

        try {
            String abi = getPreferredAbi();
            if (abi == null) {
                ProxyManager.log("[JNI] 无支持的ABI，无法加载Go代理SO");
                return false;
            }

            String libName = abi + "/libgoproxy.so";
            java.io.InputStream is = Init.get().getClass().getClassLoader().getResourceAsStream("assets/" + libName);
            if (is == null) {
                ProxyManager.log("[JNI] SO文件不存在: assets/" + libName);
                return false;
            }

            java.io.File outDir = Init.context().getDir("goproxy_lib", android.content.Context.MODE_PRIVATE);
            java.io.File outFile = new java.io.File(outDir, "libgoproxy.so");
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(outFile)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) != -1) fos.write(buf, 0, n);
            }
            is.close();

            System.load(outFile.getAbsolutePath());
            libraryLoaded = true;
            loadedAbi = abi;
            ProxyManager.log("[JNI] 加载成功: " + abi + "/libgoproxy.so");
            return true;
        } catch (UnsatisfiedLinkError e) {
            ProxyManager.log("[JNI] 链接错误: " + e.getMessage());
        } catch (Exception e) {
            ProxyManager.log("[JNI] 加载异常: " + e.getMessage());
        }
        return false;
    }

    private static String getPreferredAbi() {
        String[] supportedAbis = android.os.Build.SUPPORTED_ABIS;
        for (String abi : supportedAbis) {
            if ("arm64-v8a".equals(abi) || "armeabi-v7a".equals(abi)) {
                return abi;
            }
        }
        return null;
    }

    public static boolean isLoaded() {
        return libraryLoaded;
    }

    public static native int startProxy(int port);
    public static native int stopProxy();
    public static native int isProxyRunning();
}
