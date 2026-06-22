package com.nineone.markdown.dto.rag;

import lombok.Data;

/**
 * RAG 索引状态
 */
@Data
public class IndexStatus {

    /** 用户 ID */
    private Long userId;

    /** 已索引文章数 */
    private Integer totalArticles;

    /** 总分块数 */
    private Integer totalChunks;

    /** 总向量数 */
    private Integer totalVectors;
}
