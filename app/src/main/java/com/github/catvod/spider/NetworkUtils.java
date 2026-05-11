package com.github.catvod.spider;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;

public class NetworkUtils {

    public static class HttpResult {
        public int code = -1;
        public String body = "";
        public String error = "";
        public long latencyMs = 0;

        public boolean isOk() {
            return code >= 200 && code < 300;
        }
    }

    public static String robustHttpGet(String urlStr) {
        return robustHttpGet(urlStr, 30000, 2, 500);
    }

    public static String robustHttpGet(String urlStr, int timeoutMs, int maxRetries, int retryDelayMs) {
        int attempts = Math.max(1, maxRetries);
        for (int retry = 0; retry < attempts; retry++) {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(urlStr);
                conn = (HttpURLConnection) url.openConnection();

                // 处理HTTPS
                if (conn instanceof HttpsURLConnection) {
                    HttpsURLConnection httpsConn = (HttpsURLConnection) conn;
                    try {
                        httpsConn.setSSLSocketFactory(new TLSSocketFactory());
                    } catch (Exception e) {}
                }

                conn.setRequestMethod("GET");
                conn.setConnectTimeout(timeoutMs);
                conn.setReadTimeout(timeoutMs);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                conn.setInstanceFollowRedirects(true);
                // 添加这些请求头，模拟浏览器
                conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
                conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
                conn.setRequestProperty("Connection", "keep-alive");
                conn.setRequestProperty("Cache-Control", "no-cache");

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    InputStream is = conn.getInputStream();
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = is.read(buffer)) != -1) {
                        baos.write(buffer, 0, len);
                    }
                    is.close();
                    return new String(baos.toByteArray(), "UTF-8");
                } else if (responseCode == 404 || responseCode == 403) {
                    DanmakuSpider.log("HTTP " + responseCode + ": " + urlStr);
                    break; // 不重试
                } else {
                    // 其他错误码也记录一下
                    DanmakuSpider.log("HTTP " + responseCode + ": " + urlStr);
                }
            } catch (Exception e) {
                DanmakuSpider.log("网络请求失败(" + retry + "): " + urlStr + " - " +
                        (e.getMessage() != null ? e.getMessage() : e.getClass().getName()));
                if (retry < attempts - 1) {
                    try {
                        Thread.sleep(retryDelayMs);
                    } catch (InterruptedException ie) {}
                }
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
        return "";
    }

    public static HttpResult httpGet(String urlStr, int timeoutMs) {
        HttpResult result = new HttpResult();
        long start = System.currentTimeMillis();
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();

            if (conn instanceof HttpsURLConnection) {
                HttpsURLConnection httpsConn = (HttpsURLConnection) conn;
                try {
                    httpsConn.setSSLSocketFactory(new TLSSocketFactory());
                } catch (Exception e) {}
            }

            conn.setRequestMethod("GET");
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("Accept", "application/json,text/plain,*/*");
            conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
            conn.setRequestProperty("Connection", "close");
            conn.setRequestProperty("Cache-Control", "no-cache");

            result.code = conn.getResponseCode();
            InputStream is = result.isOk() ? conn.getInputStream() : conn.getErrorStream();
            if (is != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int len;
                while ((len = is.read(buffer)) != -1) {
                    baos.write(buffer, 0, len);
                }
                is.close();
                result.body = new String(baos.toByteArray(), "UTF-8");
            }
        } catch (Exception e) {
            result.error = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
        } finally {
            result.latencyMs = System.currentTimeMillis() - start;
            if (conn != null) conn.disconnect();
        }
        return result;
    }

    public static String getLocalIpAddress() {
        try {
            java.util.Enumeration<java.net.NetworkInterface> en =
                    java.net.NetworkInterface.getNetworkInterfaces();
            while (en.hasMoreElements()) {
                java.net.NetworkInterface intf = en.nextElement();
                java.util.Enumeration<java.net.InetAddress> enumIpAddr =
                        intf.getInetAddresses();
                while (enumIpAddr.hasMoreElements()) {
                    java.net.InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() &&
                            inetAddress instanceof java.net.Inet4Address) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "127.0.0.1";
    }
}
