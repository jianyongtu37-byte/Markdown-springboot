package com.nineone.markdown.dto.rag;

import lombok.Data;

/**
 * RAG 来源引用
 */
@Data
public class RAGSource {

    /** 文章 ID */
    private Long articleId;

    /** 文章标题 */
    private String articleTitle;

    /** 匹配的段落内容 */
    private String chunkContent;

    /** 相关性分数 */
    private Double relevanceScore;

    /** 段落在文章中的位置 */
    private Integer chunkIndex;
}
