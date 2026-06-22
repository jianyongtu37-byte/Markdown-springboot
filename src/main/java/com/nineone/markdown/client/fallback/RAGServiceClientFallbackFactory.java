package com.nineone.markdown.client.fallback;

import com.nineone.markdown.client.RAGServiceClient;
import com.nineone.markdown.dto.rag.RAGQuestionRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

/**
 * RAG 服务 Feign 客户端降级工厂
 * 当 Python RAG 服务不可用时返回友好的错误信息
 */
@Slf4j
@Component
public class RAGServiceClientFallbackFactory implements FallbackFactory<RAGServiceClient> {

    private static final String ERROR_MSG = "RAG 服务暂时不可用，请稍后重试";

    @Override
    public RAGServiceClient create(Throwable cause) {
        log.error("RAG 服务调用失败，触发降级: {}", cause.getMessage());
        return new RAGServiceClient() {

            @Override
            public Map<String, Object> ask(Long userId, RAGQuestionRequest request) {
                return errorMap();
            }

            @Override
            public Map<String, Object> askAboutArticle(Long userId, Long articleId, RAGQuestionRequest request) {
                return errorMap();
            }

            @Override
            public Map<String, Object> rebuildIndex(Long userId, Map<String, Object> request) {
                return errorMap();
            }

            @Override
            public Map<String, Object> syncArticle(Long userId, Map<String, Object> request) {
                return errorMap();
            }

            @Override
            public Map<String, Object> removeArticle(Long userId, Long articleId) {
                return errorMap();
            }

            @Override
            public Map<String, Object> getIndexStatus(Long userId) {
                return Map.of("error", true, "message", ERROR_MSG,
                        "totalArticles", 0, "totalChunks", 0, "totalVectors", 0);
            }

            @Override
            public Map<String, Object> syncGlobalArticle(Long userId, Map<String, Object> request) {
                return errorMap();
            }

            @Override
            public Map<String, Object> removeGlobalArticle(Long userId, Long articleId) {
                return errorMap();
            }

            @Override
            public Map<String, Object> listSessions(Long userId) {
                return Map.of("sessions", Collections.emptyList());
            }

            @Override
            public Map<String, Object> getSessionHistory(Long userId, String sessionId) {
                return Map.of("history", Collections.emptyList());
            }

            @Override
            public Map<String, Object> clearSession(Long userId, String sessionId) {
                return errorMap();
            }

            @Override
            public Map<String, Object> analyzeKnowledgeGap(Long userId) {
                return Map.of("error", true, "message", ERROR_MSG,
                        "topics", Collections.emptyList(), "suggestions", Collections.emptyList());
            }

            @Override
            public Map<String, Object> recommendLearningPath(Long userId, String topic) {
                return Map.of("error", true, "message", ERROR_MSG,
                        "path", Collections.emptyList());
            }

            @Override
            public Map<String, Object> getArticleIds(Long userId) {
                return Map.of("error", true, "message", ERROR_MSG,
                        "article_ids", Collections.emptyList(), "count", 0);
            }

            @Override
            public Map<String, Object> getGlobalArticleIds(Long userId) {
                return Map.of("error", true, "message", ERROR_MSG,
                        "article_ids", Collections.emptyList(), "count", 0);
            }

            @Override
            public Map<String, Object> getIndexUsers(Long userId) {
                return Map.of("error", true, "message", ERROR_MSG,
                        "user_ids", Collections.emptyList(), "count", 0);
            }

            @Override
            public Map<String, Object> deleteUserIndex(Long userId, Long targetUserId) {
                return Map.of("error", true, "message", ERROR_MSG, "deleted", false);
            }

            @Override
            public Map<String, Object> rebuildGlobalIndex(Long userId, Map<String, Object> request) {
                return errorMap();
            }

            private Map<String, Object> errorMap() {
                return Map.of("error", true, "message", ERROR_MSG);
            }
        };
    }
}
