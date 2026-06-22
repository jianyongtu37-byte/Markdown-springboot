package com.nineone.markdown.dto.rag;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * RAG 问答请求
 */
@Data
public class RAGQuestionRequest {

    /** 用户问题 */
    private String question;

    /** 用户 ID（由 Controller 自动填充） */
    private Long userId;

    /** 指定文章 ID（精读模式，可选） */
    @JsonProperty("article_id")
    private Long articleId;

    /** 会话 ID（多轮对话，可选） */
    @JsonProperty("session_id")
    private String sessionId;

    /** 检索范围: "all" | "article" | "category" | "tag" */
    private String scope = "all";

    /** 用户选中的文本（可选） */
    private String highlight;

    /** 最大来源数 */
    @JsonProperty("max_sources")
    private Integer maxSources = 5;
}
