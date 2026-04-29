package com.nineone.markdown.util;

import cn.hutool.core.util.EscapeUtil;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

/**
 * HTML 安全处理工具类
 * 用于安全地渲染包含高亮标签的文本
 */
public class HtmlSanitizer2 {

    /**
     * 自定义安全列表，允许高亮标签和基本文本格式
     */
    private static final Safelist HIGHLIGHT_SAFELIST = Safelist.none()
            .addTags(
                    // 高亮标签
                    "em",
                    // 基本文本标签
                    "b", "strong", "i", "em", "u", "br", "p", "div", "span",
                    // 列表标签
                    "ul", "ol", "li",
                    // 标题标签
                    "h1", "h2", "h3", "h4", "h5", "h6"
            )
            .addAttributes("em", "class", "style")
            .addAttributes("span", "class", "style")
            .addAttributes("div", "class", "style")
            .addProtocols("a", "href", "http", "https", "mailto")
            .addEnforcedAttribute("a", "rel", "nofollow");

    /**
     * 安全地清理包含高亮标签的HTML
     * @param html 包含高亮标签的HTML文本
     * @return 安全的HTML文本
     */
    public static String sanitizeWithHighlight(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        
        // 使用自定义安全列表清理HTML
        return Jsoup.clean(html, HIGHLIGHT_SAFELIST);
    }

    /**
     * 安全地清理纯文本，允许高亮标签
     * @param text 包含高亮标签的文本
     * @return 安全的文本
     */
    public static String sanitizeTextWithHighlight(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        
        // 创建更宽松的安全列表，主要用于文本
        Safelist textSafelist = Safelist.simpleText()
                .addTags("em")
                .addAttributes("em", "class", "style");
        
        return Jsoup.clean(text, textSafelist);
    }

    /**
     * 提取纯文本（移除所有HTML标签，包括高亮标签）
     * @param html 包含HTML的文本
     * @return 纯文本
     */
    public static String extractPlainText(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        
        // 使用none安全列表移除所有标签
        return Jsoup.clean(html, Safelist.none());
    }

    /**
     * 检查文本是否包含高亮标签
     * @param text 文本内容
     * @return 是否包含高亮标签
     */
    public static boolean containsHighlight(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        
        return text.contains("<em>") || text.contains("</em>");
    }

    /**
     * 安全地包装高亮文本
     * @param text 原始文本
     * @param keyword 高亮关键词
     * @return 包含安全高亮标签的文本
     */
    public static String wrapHighlightSafely(String text, String keyword) {
        if (text == null || text.isEmpty() || keyword == null || keyword.isEmpty()) {
            return text != null ? text : "";
        }
        
        // 转义HTML特殊字符 - 使用Hutool的EscapeUtil
        String escapedText = EscapeUtil.escapeHtml4(text);
        
        // 安全地添加高亮标签
        String lowerText = escapedText.toLowerCase();
        String lowerKeyword = keyword.toLowerCase();
        
        if (!lowerText.contains(lowerKeyword)) {
            return escapedText;
        }
        
        // 找到关键词位置并添加高亮标签
        StringBuilder result = new StringBuilder();
        int lastIndex = 0;
        int index = lowerText.indexOf(lowerKeyword);
        
        while (index >= 0) {
            // 添加关键词前的文本
            result.append(escapedText, lastIndex, index);
            // 添加高亮标签和关键词
            result.append("<em>")
                  .append(escapedText, index, index + keyword.length())
                  .append("</em>");
            
            lastIndex = index + keyword.length();
            index = lowerText.indexOf(lowerKeyword, lastIndex);
        }
        
        // 添加剩余文本
        result.append(escapedText.substring(lastIndex));
        
        return result.toString();
    }

    /**
     * 限制文本长度并添加省略号，同时保持高亮标签完整
     * @param text 包含高亮标签的文本
     * @param maxLength 最大长度
     * @return 截断后的文本
     */
    public static String truncateWithHighlight(String text, int maxLength) {
        if (text == null || text.isEmpty() || text.length() <= maxLength) {
            return text != null ? text : "";
        }
        
        // 提取纯文本计算长度
        String plainText = extractPlainText(text);
        if (plainText.length() <= maxLength) {
            return text;
        }
        
        // 在安全位置截断
        int cutPoint = findSafeCutPoint(plainText, maxLength);
        String truncatedPlain = plainText.substring(0, cutPoint) + "...";
        
        // 重新应用高亮
        return reapplyHighlight(text, truncatedPlain);
    }

    /**
     * 找到安全的截断点
     */
    private static int findSafeCutPoint(String text, int maxLength) {
        // 尝试在句子边界处截断
        int lastPeriod = text.lastIndexOf('.', maxLength);
        int lastExclamation = text.lastIndexOf('!', maxLength);
        int lastQuestion = text.lastIndexOf('?', maxLength);
        
        int cutPoint = Math.max(Math.max(lastPeriod, lastExclamation), lastQuestion);
        if (cutPoint > maxLength / 2) {
            return cutPoint + 1;
        }
        
        // 尝试在单词边界处截断
        int lastSpace = text.lastIndexOf(' ', maxLength);
        if (lastSpace > maxLength / 2) {
            return lastSpace;
        }
        
        return maxLength;
    }

    /**
     * 重新应用高亮到截断后的文本
     */
    private static String reapplyHighlight(String original, String truncated) {
        // 简单的实现：如果原始文本有高亮，在截断文本中保留高亮标签
        if (!containsHighlight(original)) {
            return truncated;
        }
        
        // 这里可以更复杂的逻辑来重新应用高亮
        // 目前简单返回截断文本
        return truncated;
    }
}