package com.nineone.markdown.client;

import com.nineone.markdown.client.fallback.RAGServiceClientFallbackFactory;
import com.nineone.markdown.dto.rag.IndexStatus;
import com.nineone.markdown.dto.rag.RAGQuestionRequest;
import com.nineone.markdown.dto.rag.RAGResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * RAG Agent 服务 Feign 客户端
 * 直连 Python RAG 服务（不经 Nacos）
 * 用户 ID 通过 X-User-Id 请求头传递（与 Gateway 保持一致）
 */
@FeignClient(name = "rag-agent", url = "${rag.agent.url:http://localhost:8084}", fallbackFactory = RAGServiceClientFallbackFactory.class)
public interface RAGServiceClient {

    /**
     * 跨文章知识问答（非流式）
     */
    @PostMapping("/api/rag/ask")
    Map<String, Object> ask(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody RAGQuestionRequest request);

    /**
     * 文章精读问答（非流式）
     */
    @PostMapping("/api/rag/article/{articleId}/ask")
    Map<String, Object> askAboutArticle(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable("articleId") Long articleId,
            @RequestBody RAGQuestionRequest request);

    /**
     * 全量重建索引
     */
    @PostMapping("/api/rag/reindex")
    Map<String, Object> rebuildIndex(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody Map<String, Object> request);

    /**
     * 增量同步文章
     */
    @PostMapping("/api/rag/article/sync")
    Map<String, Object> syncArticle(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody Map<String, Object> request);

    /**
     * 删除文章索引
     */
    @DeleteMapping("/api/rag/article/{articleId}")
    Map<String, Object> removeArticle(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable("articleId") Long articleId);

    /**
     * 获取索引状态
     */
    @GetMapping("/api/rag/status")
    Map<String, Object> getIndexStatus(@RequestHeader("X-User-Id") Long userId);

    // ==================== 全局公共索引管理 ====================

    /**
     * 同步单篇公开文章到全局公共索引
     */
    @PostMapping("/api/rag/global/sync")
    Map<String, Object> syncGlobalArticle(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody Map<String, Object> request);

    /**
     * 从全局公共索引移除文章
     */
    @DeleteMapping("/api/rag/global/article/{articleId}")
    Map<String, Object> removeGlobalArticle(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable("articleId") Long articleId);

    // ==================== 会话管理 ====================

    /**
     * 列出用户的所有活跃会话
     */
    @GetMapping("/api/rag/sessions")
    Map<String, Object> listSessions(@RequestHeader("X-User-Id") Long userId);

    /**
     * 获取会话的对话历史
     */
    @GetMapping("/api/rag/sessions/{sessionId}/history")
    Map<String, Object> getSessionHistory(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable("sessionId") String sessionId);

    /**
     * 清除会话
     */
    @DeleteMapping("/api/rag/sessions/{sessionId}")
    Map<String, Object> clearSession(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable("sessionId") String sessionId);

    // ==================== 智能分析 ====================

    /**
     * 知识缺口分析
     */
    @PostMapping("/api/rag/analysis/gap")
    Map<String, Object> analyzeKnowledgeGap(@RequestHeader("X-User-Id") Long userId);

    /**
     * 学习路径推荐
     */
    @PostMapping("/api/rag/analysis/learning-path")
    Map<String, Object> recommendLearningPath(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(value = "topic", defaultValue = "") String topic);

    // ==================== 索引对账接口 ====================

    /**
     * 获取用户私有索引中的所有文章 ID
     */
    @GetMapping("/api/rag/article-ids")
    Map<String, Object> getArticleIds(@RequestHeader("X-User-Id") Long userId);

    /**
     * 获取全局公共索引中的所有文章 ID
     */
    @GetMapping("/api/rag/global/article-ids")
    Map<String, Object> getGlobalArticleIds(@RequestHeader("X-User-Id") Long userId);

    /**
     * 获取所有拥有 FAISS 索引的用户 ID
     */
    @GetMapping("/api/rag/users")
    Map<String, Object> getIndexUsers(@RequestHeader("X-User-Id") Long userId);

    /**
     * 删除指定用户的整个 FAISS 索引
     */
    @DeleteMapping("/api/rag/user/{targetUserId}")
    Map<String, Object> deleteUserIndex(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable("targetUserId") Long targetUserId);

    /**
     * 全量重建全局公共索引
     */
    @PostMapping("/api/rag/global/reindex")
    Map<String, Object> rebuildGlobalIndex(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody Map<String, Object> request);
}
