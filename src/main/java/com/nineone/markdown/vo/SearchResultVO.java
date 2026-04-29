package com.nineone.markdown.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 搜索结果VO
 * 包含高亮信息和相关元数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResultVO {

    /**
     * 文章ID
     */
    private Long id;

    /**
     * 作者ID
     */
    private Long userId;

    /**
     * 作者名称
     */
    private String authorName;

    /**
     * 分类ID
     */
    private Long categoryId;

    /**
     * 分类名称
     */
    private String categoryName;

    /**
     * 文章标题（可能包含高亮标签）
     */
    private String title;

    /**
     * 文章内容摘要（可能包含高亮标签）
     */
    private String contentSnippet;

    /**
     * AI生成的摘要
     */
    private String summary;

    /**
     * 高亮标题（包含<em>标签）
     */
    private String highlightedTitle;

    /**
     * 高亮内容片段（包含<em>标签）
     */
    private String highlightedContent;

    /**
     * 标签列表
     */
    private List<TagVO> tags;

    /**
     * 标签名称字符串（逗号分隔）
     */
    private String tagNames;

    /**
     * 阅读量
     */
    private Integer viewCount;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 相关性分数
     */
    private Float score;

    /**
     * 是否包含高亮
     */
    private boolean hasHighlight;

    /**
     * 获取安全的高亮标题（如果没有高亮则返回普通标题）
     */
    public String getSafeHighlightedTitle() {
        return hasHighlight && highlightedTitle != null && !highlightedTitle.isEmpty() 
                ? highlightedTitle : title;
    }

    /**
     * 获取安全的高亮内容（如果没有高亮则返回内容摘要）
     */
    public String getSafeHighlightedContent() {
        return hasHighlight && highlightedContent != null && !highlightedContent.isEmpty() 
                ? highlightedContent : contentSnippet;
    }

    /**
     * 获取纯文本标题（移除高亮标签）
     */
    public String getPlainTitle() {
        if (title == null) return "";
        return title.replaceAll("<em>", "").replaceAll("</em>", "");
    }

    /**
     * 获取纯文本内容（移除高亮标签）
     */
    public String getPlainContentSnippet() {
        if (contentSnippet == null) return "";
        return contentSnippet.replaceAll("<em>", "").replaceAll("</em>", "");
    }

    /**
     * 获取简短的内容摘要（限制长度）
     */
    public String getShortContentSnippet(int maxLength) {
        String plainContent = getPlainContentSnippet();
        if (plainContent.length() <= maxLength) {
            return plainContent;
        }
        return plainContent.substring(0, maxLength) + "...";
    }

    /**
     * 获取简短的标题（限制长度）
     */
    public String getShortTitle(int maxLength) {
        String plainTitle = getPlainTitle();
        if (plainTitle.length() <= maxLength) {
            return plainTitle;
        }
        return plainTitle.substring(0, maxLength) + "...";
    }
}