package com.nineone.markdown.service;

import com.nineone.markdown.client.RAGServiceClient;
import com.nineone.markdown.entity.Article;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 文章索引同步服务
 * 在文章创建/更新/删除时，异步同步到 RAG 向量索引
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ArticleIndexSyncService {

    private final RAGServiceClient ragServiceClient;

    /**
     * 同步文章到 RAG 索引（创建/更新时调用）
     * 异步执行，失败仅记录 warn 日志，不影响主流程
     * - 用户私有索引：始终同步
     * - 全局公共索引：仅 PUBLIC 文章同步
     * @param tagNames 文章的标签名称列表，会被追加到索引内容中以支持标签搜索
     */
    @Async("aiTaskExecutor")
    public void syncArticleToRAG(Article article, Long userId, List<String> tagNames) {
        String enrichedContent = enrichContentWithTags(article.getContent(), tagNames);

        // 1. 同步到用户私有索引
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("user_id", userId);
            request.put("article_id", article.getId());
            request.put("article_title", article.getTitle());
            request.put("content", enrichedContent);

            Map<String, Object> result = ragServiceClient.syncArticle(userId, request);
            log.info("RAG 用户索引同步成功: articleId={}, result={}", article.getId(), result);
        } catch (Exception e) {
            log.warn("RAG 用户索引同步失败 (articleId={}): {}", article.getId(), e.getMessage());
        }

        // 2. 公开文章同步到全局公共索引
        if (article.getStatus() != null && article.getStatus().isPublic()) {
            try {
                Map<String, Object> globalRequest = new HashMap<>();
                globalRequest.put("article_id", article.getId());
                globalRequest.put("article_title", article.getTitle());
                globalRequest.put("content", enrichedContent);

                Map<String, Object> result = ragServiceClient.syncGlobalArticle(userId, globalRequest);
                log.info("RAG 全局索引同步成功: articleId={}, result={}", article.getId(), result);
            } catch (Exception e) {
                log.warn("RAG 全局索引同步失败 (articleId={}): {}", article.getId(), e.getMessage());
            }
        }
    }

    /**
     * 将标签名追加到文章内容前面，使标签可被向量检索命中
     */
    private String enrichContentWithTags(String content, List<String> tagNames) {
        if (tagNames == null || tagNames.isEmpty()) {
            return content;
        }
        String tagPrefix = tagNames.stream()
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.joining("、"));
        if (tagPrefix.isEmpty()) {
            return content;
        }
        return "标签: " + tagPrefix + "\n\n" + (content != null ? content : "");
    }

    /**
     * 从 RAG 索引中删除文章（删除文章时调用）
     * 异步执行，失败仅记录 warn 日志
     * 同时从用户私有索引和全局公共索引中删除
     */
    @Async("aiTaskExecutor")
    public void removeArticleFromRAG(Long articleId, Long userId) {
        try {
            Map<String, Object> result = ragServiceClient.removeArticle(userId, articleId);
            log.info("RAG 用户索引删除成功: articleId={}, result={}", articleId, result);
        } catch (Exception e) {
            log.warn("RAG 用户索引删除失败 (articleId={}): {}", articleId, e.getMessage());
        }

        try {
            Map<String, Object> result = ragServiceClient.removeGlobalArticle(userId, articleId);
            log.info("RAG 全局索引删除成功: articleId={}, result={}", articleId, result);
        } catch (Exception e) {
            log.warn("RAG 全局索引删除失败 (articleId={}): {}", articleId, e.getMessage());
        }
    }
}
