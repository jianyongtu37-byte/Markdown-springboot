package com.nineone.markdown.dto.rag;

import lombok.Data;

import java.util.List;

/**
 * RAG 问答响应
 */
@Data
public class RAGResponse {

    /** 生成的回答 */
    private String answer;

    /** 引用来源 */
    private List<RAGSource> sources;

    /** 会话 ID */
    private String sessionId;

    /** 置信度 0-1 */
    private Double confidence;

    /** 重写后的查询（如果有） */
    private String queryRewritten;
}
