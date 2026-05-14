package com.github.catvod.spider;

import android.text.TextUtils;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

public class DanmakuUtils {

    public static String applyTimeOffset(String xmlData, int offsetMs) {
        if (offsetMs == 0 || TextUtils.isEmpty(xmlData)) return xmlData;

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            disableExternalEntities(factory);
            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xmlData)));
            NodeList nodes = document.getElementsByTagName("d");
            int shiftedCount = 0;
            double offsetSeconds = offsetMs / 1000.0;

            for (int i = 0; i < nodes.getLength(); i++) {
                if (!(nodes.item(i) instanceof Element)) continue;
                Element element = (Element) nodes.item(i);
                String p = element.getAttribute("p");
                if (TextUtils.isEmpty(p)) continue;

                String[] parts = p.split(",", -1);
                if (parts.length == 0 || TextUtils.isEmpty(parts[0])) continue;

                try {
                    double originalSeconds = Double.parseDouble(parts[0]);
                    double shiftedSeconds = Math.max(0, originalSeconds + offsetSeconds);
                    parts[0] = formatSeconds(shiftedSeconds);
                    element.setAttribute("p", joinComma(parts));
                    shiftedCount++;
                } catch (NumberFormatException ignored) {
                }
            }

            if (shiftedCount == 0) return xmlData;

            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(writer));
            DanmakuSpider.log("⏱ 已应用弹幕时间偏移: " + formatOffsetLabel(offsetMs) + "，处理 " + shiftedCount + " 条");
            return writer.toString();
        } catch (Exception e) {
            DanmakuSpider.log("弹幕时间偏移处理失败，使用原始弹幕: " + e.getMessage());
            return xmlData;
        }
    }

    private static void disableExternalEntities(DocumentBuilderFactory factory) {
        try {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        } catch (Exception ignored) {
        }
        try {
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        } catch (Exception ignored) {
        }
        try {
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (Exception ignored) {
        }
        factory.setExpandEntityReferences(false);
    }

    private static String joinComma(String[] parts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(parts[i]);
        }
        return sb.toString();
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
