package com.github.catvod.spider;

import android.text.TextUtils;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DanmakuUtils {

    public static String applyTimeOffset(String xmlData, int offsetMs) {
        if (offsetMs == 0 || TextUtils.isEmpty(xmlData)) return xmlData;

        try {
            int shiftedCount = 0;
            double offsetSeconds = offsetMs / 1000.0;
            StringBuilder output = new StringBuilder(xmlData.length() + 32);
            int index = 0;

            while (index < xmlData.length()) {
                int tagStart = xmlData.indexOf("<d", index);
                if (tagStart < 0) {
                    output.append(xmlData, index, xmlData.length());
                    break;
                }

                if (!isDanmakuTag(xmlData, tagStart)) {
                    output.append(xmlData, index, tagStart + 2);
                    index = tagStart + 2;
                    continue;
                }

                int tagEnd = xmlData.indexOf('>', tagStart + 2);
                if (tagEnd < 0) {
                    output.append(xmlData, index, xmlData.length());
                    break;
                }

                output.append(xmlData, index, tagStart);
                ShiftResult result = shiftTagTime(xmlData, tagStart, tagEnd + 1, offsetSeconds);
                if (result.changed) {
                    shiftedCount++;
                }
                output.append(result.tag);
                index = tagEnd + 1;
            }

            if (shiftedCount == 0) return xmlData;
            DanmakuSpider.log("⏱ 已应用弹幕时间偏移: " + formatOffsetLabel(offsetMs) + "，处理 " + shiftedCount + " 条");
            return output.toString();
        } catch (Exception e) {
            DanmakuSpider.log("弹幕时间偏移处理失败，使用原始弹幕: " + e.getMessage());
            return xmlData;
        }
    }

    public static int countItems(String xmlData) {
        if (TextUtils.isEmpty(xmlData)) return 0;
        int count = 0;
        int index = 0;
        while (index < xmlData.length()) {
            int tagStart = xmlData.indexOf("<d", index);
            if (tagStart < 0) break;
            if (isDanmakuTag(xmlData, tagStart)) count++;
            index = tagStart + 2;
        }
        return count;
    }

    private static boolean isDanmakuTag(String text, int tagStart) {
        int next = tagStart + 2;
        if (next >= text.length()) return false;
        char c = text.charAt(next);
        return Character.isWhitespace(c) || c == '>' || c == '/';
    }

    private static ShiftResult shiftTagTime(String xmlData, int tagStart, int tagEnd, double offsetSeconds) {
        int attrIndex = findPAttribute(xmlData, tagStart + 2, tagEnd);
        if (attrIndex < 0) return new ShiftResult(xmlData.substring(tagStart, tagEnd), false);

        int equalsIndex = attrIndex + 1;
        while (equalsIndex < tagEnd && Character.isWhitespace(xmlData.charAt(equalsIndex))) equalsIndex++;
        if (equalsIndex >= tagEnd || xmlData.charAt(equalsIndex) != '=') {
            return new ShiftResult(xmlData.substring(tagStart, tagEnd), false);
        }

        int quoteIndex = equalsIndex + 1;
        while (quoteIndex < tagEnd && Character.isWhitespace(xmlData.charAt(quoteIndex))) quoteIndex++;
        if (quoteIndex >= tagEnd) return new ShiftResult(xmlData.substring(tagStart, tagEnd), false);

        char quote = xmlData.charAt(quoteIndex);
        if (quote != '"' && quote != '\'') return new ShiftResult(xmlData.substring(tagStart, tagEnd), false);

        int valueStart = quoteIndex + 1;
        int valueEnd = xmlData.indexOf(quote, valueStart);
        if (valueEnd < 0 || valueEnd > tagEnd) return new ShiftResult(xmlData.substring(tagStart, tagEnd), false);

        int commaIndex = xmlData.indexOf(',', valueStart);
        if (commaIndex < 0 || commaIndex > valueEnd) return new ShiftResult(xmlData.substring(tagStart, tagEnd), false);

        try {
            double originalSeconds = Double.parseDouble(xmlData.substring(valueStart, commaIndex));
            String shiftedSeconds = formatSeconds(Math.max(0, originalSeconds + offsetSeconds));
            StringBuilder tag = new StringBuilder(tagEnd - tagStart + shiftedSeconds.length());
            tag.append(xmlData, tagStart, valueStart);
            tag.append(shiftedSeconds);
            tag.append(xmlData, commaIndex, tagEnd);
            return new ShiftResult(tag.toString(), true);
        } catch (NumberFormatException ignored) {
            return new ShiftResult(xmlData.substring(tagStart, tagEnd), false);
        }
    }

    private static int findPAttribute(String text, int start, int end) {
        for (int i = start; i < end; i++) {
            if (text.charAt(i) != 'p') continue;
            if (i > start && !Character.isWhitespace(text.charAt(i - 1))) continue;
            int next = i + 1;
            while (next < end && Character.isWhitespace(text.charAt(next))) next++;
            if (next < end && text.charAt(next) == '=') return i;
        }
        return -1;
    }

    private static class ShiftResult {
        final String tag;
        final boolean changed;

        ShiftResult(String tag, boolean changed) {
            this.tag = tag;
            this.changed = changed;
        }
    }

    private static String formatSeconds(double seconds) {
        String text = String.format(Locale.US, "%.3f", seconds);
        while (text.contains(".") && text.endsWith("0")) {
            text = text.substring(0, text.length() - 1);
        }
        if (text.endsWith(".")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    public static String formatOffsetSeconds(int offsetMs) {
        return formatSeconds(offsetMs / 1000.0);
    }

    public static String formatOffsetLabel(int offsetMs) {
        if (offsetMs > 600000) offsetMs = 600000;
        if (offsetMs < -600000) offsetMs = -600000;
        if (offsetMs == 0) return "未启用";
        String prefix = offsetMs > 0 ? "延后 " : "提前 ";
        return prefix + formatOffsetSeconds(Math.abs(offsetMs)) + " 秒";
    }

    // 提取集数
    public static float extractEpisodeNum(String text) {
        if (TextUtils.isEmpty(text)) return -1;

        // 尝试匹配 "第X集"
        Pattern pattern = Pattern.compile("第\\s*(\\d+)\\s*集");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            try {
                return Float.parseFloat(matcher.group(1));
            } catch (Exception e) {}
        }

        // 尝试匹配 "EP01" 或 "E01"
        pattern = Pattern.compile("[Ee][Pp]?\\s*(\\d+)");
        matcher = pattern.matcher(text);
        if (matcher.find()) {
            try {
                return Float.parseFloat(matcher.group(1));
            } catch (Exception e) {}
        }

        return -1;
    }

    // 提取标题（简化版）
    public static String extractTitle2(String src) {
        if (TextUtils.isEmpty(src)) return "";

        String result = src.trim();

        // 移除集数信息（更彻底），但不要误删标题里的普通数字，例如“20岁”
        result = result.replaceAll("第\\s*[0-9零一二三四五六七八九十百千万]+\\s*[集話话期]\\s*[上中下]?", " ");
        result = result.replaceAll("(?i)\\b[Ee][Pp]?\\s*\\d+\\b", " ");
        result = result.replaceAll("(?i)\\bS\\d{1,2}(?:E\\d{1,3})?\\b", " ");
        result = result.replaceAll("\\d+[Kk]", "");
        // 移除文件大小信息
        result = result.replaceAll("\\[\\d+[\\.\\d]*[MGT]\\]", "");
        // 移除分辨率信息
        result = result.replaceAll("\\d+[Pp]", "");
        result = result.replaceAll("4K", "");
        // 移除文件扩展名
        result = result.replaceAll("\\.(mp4|mkv|avi|rmvb|flv|web|dl|h265|h264|hevc)$", "");
        // 移除括号内容
        result = result.replaceAll("【.*?】", "");
        result = result.replaceAll("\\[.*?\\]", "");
        result = result.replaceAll("\\(.*?\\)", "");
        // 移除特殊字符
        result = result.replaceAll("[\\\\/:*\"<>|丨]", " ");
        // 清理中文标点
        result = result.replaceAll("[:：]", " ");

        String compact = result
                .replaceAll("[^\\u4e00-\\u9fffA-Za-z0-9]+", " ")
                .replaceAll("\\s+", " ")
                .trim();

        // 如果有中文，保留中文、字母和数字，避免把“怦然心动20岁”清成“岁”
        if (compact.matches(".*[\\u4e00-\\u9fff].*")) {
            result = compact;
        } else {
            // 否则清理多余空格
            result = result.replaceAll("\\s+", " ").trim();
        }

        if (!src.equals(result)) {
            DanmakuSpider.log("🧹 清理标题: " + src + " -> " + result);
        }

        return result;
    }

    public static String extractEpisodeDateCode(String text) {
        if (TextUtils.isEmpty(text)) return "";

        Pattern pattern = Pattern.compile(
                "(?<!\\d)((?:19|20)\\d{2})\\s*[年./_\\-]?\\s*([01]?\\d)\\s*[月./_\\-]?\\s*([0-3]?\\d)\\s*日?(?!\\d)");
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String code = normalizeDateCode(matcher.group(1), matcher.group(2), matcher.group(3));
            if (!TextUtils.isEmpty(code)) return code;
        }

        return "";
    }

    private static String normalizeDateCode(String yearText, String monthText, String dayText) {
        try {
            int year = Integer.parseInt(yearText);
            int month = Integer.parseInt(monthText);
            int day = Integer.parseInt(dayText);
            if (year < 1900 || year > 2099) return "";
            if (month < 1 || month > 12) return "";
            if (day < 1 || day > 31) return "";
            return String.format(Locale.US, "%04d%02d%02d", year, month, day);
        } catch (Exception e) {
            return "";
        }
    }

    public static String extractEpisodePartSuffix(String text) {
        if (TextUtils.isEmpty(text)) return "";

        Pattern episodePartPattern = Pattern.compile("第\\s*[零一二三四五六七八九十百千万0-9]+\\s*[期集]\\s*([上中下])");
        Matcher matcher = episodePartPattern.matcher(text);
        if (matcher.find()) return matcher.group(1);

        Pattern specialPartPattern = Pattern.compile(
                "(?:先导片|纯享(?:版)?|加更(?:版)?|花絮|特别篇|特别企划|番外|SP|OVA|OAD)\\s*([上中下])",
                Pattern.CASE_INSENSITIVE);
        matcher = specialPartPattern.matcher(text);
        if (matcher.find()) return matcher.group(1);

        return "";
    }
}
