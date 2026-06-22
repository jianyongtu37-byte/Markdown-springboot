package com.nineone.markdown.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nineone.common.result.Result;
import com.nineone.markdown.client.RAGServiceClient;
import com.nineone.markdown.dto.rag.IndexStatus;
import com.nineone.markdown.dto.rag.RAGQuestionRequest;
import com.nineone.markdown.dto.rag.RAGResponse;
import com.nineone.markdown.entity.Article;
import com.nineone.markdown.mapper.ArticleMapper;
import com.nineone.markdown.util.UserContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * RAG 知识问答控制器
 */
@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
@Slf4j
public class RAGController {

    private final RAGServiceClient ragServiceClient;
    private final ArticleMapper articleMapper;
    private final Executor sseExecutor;

    @Value("${rag.agent.url:http://localhost:8084}")
    private String ragAgentUrl;

    // ==================== 问答接口 ====================

    /**
     * 跨文章知识问答（非流式）
     */
    @PostMapping("/ask")
    public Result<Map<String, Object>> ask(@RequestBody RAGQuestionRequest request) {
        Long userId = UserContextHolder.getUserId();
        request.setUserId(userId);
        try {
            Map<String, Object> response = ragServiceClient.ask(userId, request);
            return Result.success(response);
        } catch (Exception e) {
            log.error("RAG 问答失败: {}", e.getMessage(), e);
            return Result.error("RAG 服务暂时不可用，请稍后重试");
        }
    }

    /**
     * 跨文章知识问答（流式 SSE）
     */
    @PostMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter askStream(@RequestBody RAGQuestionRequest request) {
        Long userId = UserContextHolder.getUserId();
        request.setUserId(userId);
        return proxySSE(ragAgentUrl + "/api/rag/ask/stream", request);
    }

    /**
     * 文章精读问答（非流式）
     */
    @PostMapping("/article/{articleId}/ask")
    public Result<Map<String, Object>> askAboutArticle(
            @PathVariable Long articleId,
            @RequestBody RAGQuestionRequest request) {
        Long userId = UserContextHolder.getUserId();
        request.setUserId(userId);
        try {
            Map<String, Object> response = ragServiceClient.askAboutArticle(userId, articleId, request);
            return Result.success(response);
        } catch (Exception e) {
            log.error("RAG 文章问答失败: {}", e.getMessage(), e);
            return Result.error("RAG 服务暂时不可用，请稍后重试");
        }
    }

    /**
     * 文章精读问答（流式 SSE）
     */
    @PostMapping(value = "/article/{articleId}/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter askAboutArticleStream(
            @PathVariable Long articleId,
            @RequestBody RAGQuestionRequest request) {
        Long userId = UserContextHolder.getUserId();
        request.setUserId(userId);
        return proxySSE(ragAgentUrl + "/api/rag/article/" + articleId + "/ask/stream", request);
    }

    // ==================== 健康检查 ====================

    /**
     * RAG 服务健康检查（供前端判断服务是否可用）
     */
    @GetMapping("/health")
    public Result<Map<String, Object>> healthCheck() {
        try {
            Map<String, Object> status = ragServiceClient.getIndexStatus(0L);
            return Result.success(Map.of("status", "up", "detail", status));
        } catch (Exception e) {
            log.warn("RAG 服务健康检查失败: {}", e.getMessage());
            return Result.success(Map.of("status", "down", "message", "RAG 服务未启动"));
        }
    }

    // ==================== 索引管理接口 ====================

    /**
     * 重建当前用户的索引（分批从数据库拉取文章，避免 OOM）
     */
    @PostMapping("/reindex")
    public Result<Map<String, Object>> rebuildIndex() {
        Long userId = UserContextHolder.getUserId();
        try {
            int batchSize = 50;
            int pageNum = 1;
            int totalProcessed = 0;
            int totalArticles = 0;

            // 先统计文章总数
            Long count = articleMapper.selectCount(
                new LambdaQueryWrapper<Article>()
                    .eq(Article::getUserId, userId)
                    .eq(Article::getDeleted, 0)
            );
            totalArticles = count.intValue();

            if (totalArticles == 0) {
                return Result.success(Map.of("status", "no_articles", "message", "没有可索引的文章"));
            }

            // 分批查询并发送给 RAG 服务
            while (true) {
                Page<Article> page = new Page<>(pageNum, batchSize);
                Page<Article> result = articleMapper.selectPage(
                    page,
                    new LambdaQueryWrapper<Article>()
                        .eq(Article::getUserId, userId)
                        .eq(Article::getDeleted, 0)
                        .select(Article::getId, Article::getTitle, Article::getContent)
                );

                List<Article> articles = result.getRecords();
                if (articles.isEmpty()) {
                    break;
                }

                // 构建当前批次的请求
                List<Map<String, Object>> articleList = articles.stream()
                    .map(a -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("id", a.getId());
                        m.put("title", a.getTitle());
                        m.put("content", a.getContent());
                        return m;
                    })
                    .toList();

                Map<String, Object> request = new HashMap<>();
                request.put("user_id", userId);
                request.put("articles", articleList);

                ragServiceClient.rebuildIndex(userId, request);
                totalProcessed += articles.size();
                log.info("RAG 索引重建进度: userId={}, 已处理={}/{}", userId, totalProcessed, totalArticles);

                if (articles.size() < batchSize) {
                    break;
                }
                pageNum++;
            }

            log.info("RAG 全量索引重建完成: userId={}, 文章数={}", userId, totalProcessed);
            return Result.success(Map.of(
                "status", "success",
                "message", "索引重建完成",
                "totalArticles", totalArticles,
                "processedArticles", totalProcessed
            ));
        } catch (Exception e) {
            log.error("RAG 索引重建失败: {}", e.getMessage(), e);
            return Result.error("索引重建失败");
        }
    }

    /**
     * 获取当前用户的索引状态
     */
    @GetMapping("/status")
    public Result<Map<String, Object>> getIndexStatus() {
        Long userId = UserContextHolder.getUserId();
        try {
            Map<String, Object> status = ragServiceClient.getIndexStatus(userId);
            return Result.success(status);
        } catch (Exception e) {
            log.error("获取 RAG 索引状态失败: {}", e.getMessage(), e);
            return Result.error("获取索引状态失败");
        }
    }

    // ==================== 会话管理接口 ====================

    /**
     * 列出当前用户的所有活跃会话
     */
    @GetMapping("/sessions")
    public Result<Map<String, Object>> listSessions() {
        Long userId = UserContextHolder.getUserId();
        try {
            Map<String, Object> result = ragServiceClient.listSessions(userId);
            return Result.success(result);
        } catch (Exception e) {
            log.error("获取会话列表失败: {}", e.getMessage(), e);
            return Result.error("获取会话列表失败");
        }
    }

    /**
     * 获取会话的对话历史
     */
    @GetMapping("/sessions/{sessionId}/history")
    public Result<Map<String, Object>> getSessionHistory(@PathVariable String sessionId) {
        Long userId = UserContextHolder.getUserId();
        try {
            Map<String, Object> result = ragServiceClient.getSessionHistory(userId, sessionId);
            return Result.success(result);
        } catch (Exception e) {
            log.error("获取对话历史失败: {}", e.getMessage(), e);
            return Result.error("获取对话历史失败");
        }
    }

    /**
     * 清除会话
     */
    @DeleteMapping("/sessions/{sessionId}")
    public Result<Map<String, Object>> clearSession(@PathVariable String sessionId) {
        Long userId = UserContextHolder.getUserId();
        try {
            Map<String, Object> result = ragServiceClient.clearSession(userId, sessionId);
            return Result.success(result);
        } catch (Exception e) {
            log.error("清除会话失败: {}", e.getMessage(), e);
            return Result.error("清除会话失败");
        }
    }

    // ==================== 智能分析接口 ====================

    /**
     * 知识缺口分析
     */
    @PostMapping("/analysis/gap")
    public Result<Map<String, Object>> analyzeKnowledgeGap() {
        Long userId = UserContextHolder.getUserId();
        try {
            Map<String, Object> result = ragServiceClient.analyzeKnowledgeGap(userId);
            return Result.success(result);
        } catch (Exception e) {
            log.error("知识缺口分析失败: {}", e.getMessage(), e);
            return Result.error("分析失败，请稍后重试");
        }
    }

    /**
     * 学习路径推荐
     */
    @PostMapping("/analysis/learning-path")
    public Result<Map<String, Object>> recommendLearningPath(
            @RequestParam(value = "topic", defaultValue = "") String topic) {
        Long userId = UserContextHolder.getUserId();
        try {
            Map<String, Object> result = ragServiceClient.recommendLearningPath(userId, topic);
            return Result.success(result);
        } catch (Exception e) {
            log.error("学习路径推荐失败: {}", e.getMessage(), e);
            return Result.error("推荐失败，请稍后重试");
        }
    }

    // ==================== 内部方法 ====================

    /**
     * 代理 SSE 请求到 Python RAG 服务
     * 使用 HttpURLConnection 读取 Python 服务的 SSE 流，再通过 SseEmitter 转发给前端
     */
    private SseEmitter proxySSE(String urlStr, RAGQuestionRequest request) {
        SseEmitter emitter = new SseEmitter(120_000L); // 2 分钟超时

        sseExecutor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = URI.create(urlStr).toURL();
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Accept", "text/event-stream");
                conn.setRequestProperty("X-User-Id", String.valueOf(request.getUserId()));
                conn.setDoOutput(true);
                conn.setConnectTimeout(10_000);
                conn.setReadTimeout(120_000);

                // 写入请求体
                String jsonBody = String.format(
                        "{\"question\":\"%s\",\"user_id\":%d,\"scope\":\"%s\",\"max_sources\":%d%s%s%s}",
                        escapeJson(request.getQuestion()),
                        request.getUserId(),
                        request.getScope() != null ? request.getScope() : "all",
                        request.getMaxSources() != null ? request.getMaxSources() : 5,
                        request.getArticleId() != null ? ",\"article_id\":" + request.getArticleId() : "",
                        request.getSessionId() != null ? ",\"session_id\":\"" + request.getSessionId() + "\"" : "",
                        request.getHighlight() != null ? ",\"highlight\":\"" + escapeJson(request.getHighlight()) + "\"" : ""
                );
                conn.getOutputStream().write(jsonBody.getBytes(StandardCharsets.UTF_8));
                conn.getOutputStream().flush();

                // 读取 SSE 流并转发
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        log.debug("SSE 原始行: {}", line);
                        // 只转发 data: 行，保持标准 SSE 格式
                        if (line.startsWith("data:")) {
                            String payload = line.substring(5).trim();
                            log.debug("SSE 转发: {}", payload);
                            // 直接用 data() 发送，不加 event name，前端用 data: 行解析
                            emitter.send(payload);
                        }
                    }
                }

                emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                emitter.complete();

            } catch (Exception e) {
                log.error("SSE 代理错误: {}", e.getMessage(), e);
                try {
                    String errorMsg = "RAG 服务暂时不可用，请稍后重试";
                    if (e.getMessage() != null && e.getMessage().contains("Connection refused")) {
                        errorMsg = "RAG 服务未启动，请联系管理员";
                    }
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("{\"error\":\"" + escapeJson(errorMsg) + "\"}"));
                    emitter.send(SseEmitter.event()
                            .name("done")
                            .data("[DONE]"));
                    emitter.complete();
                } catch (Exception ignored) {
                    try {
                        emitter.complete();
                    } catch (Exception ignored2) {
                    }
                }
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });

        emitter.onTimeout(() -> log.warn("SSE 连接超时"));
        emitter.onError(e -> log.warn("SSE 连接错误: {}", e.getMessage()));

        return emitter;
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
