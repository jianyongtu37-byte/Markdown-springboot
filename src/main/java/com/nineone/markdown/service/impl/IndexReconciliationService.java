package com.nineone.markdown.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nineone.markdown.client.RAGServiceClient;
import com.nineone.markdown.entity.Article;
import com.nineone.markdown.entity.User;
import com.nineone.markdown.mapper.ArticleMapper;
import com.nineone.markdown.mapper.UserMapper;
import com.nineone.markdown.service.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 索引对账清理服务
 * 定期对比 FAISS/ES 索引与 MySQL 数据库，清理孤儿数据
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IndexReconciliationService {

    private final RAGServiceClient ragServiceClient;
    private final ArticleMapper articleMapper;
    private final UserMapper userMapper;
    private final SearchService searchService;

    @Value("${rag.agent.sync-on-save:true}")
    private boolean ragEnabled;

    /**
     * 定时执行索引对账（默认每天凌晨 3:00）
     */
    @Scheduled(cron = "${app.index.reconcile.cron:0 0 3 * * ?}")
    public void scheduledReconcile() {
        if (!ragEnabled) {
            log.info("RAG 同步已关闭，跳过索引对账");
            return;
        }
        log.info("========== 开始定时索引对账任务 ==========");
        try {
            reconcileAll();
        } catch (Exception e) {
            log.error("索引对账任务执行失败", e);
        }
        log.info("========== 定时索引对账任务结束 ==========");
    }

    /**
     * 执行完整对账流程（可被 Admin 端点手动调用）
     */
    public Map<String, Object> reconcileAll() {
        Map<String, Object> result = new LinkedHashMap<>();
        Long systemUserId = getSystemUserId();
        if (systemUserId == null) {
            result.put("error", "无法获取系统用户 ID，对账终止");
            return result;
        }

        // 1. 清理孤儿用户索引
        try {
            Map<String, Object> userCleanup = cleanOrphanedUserIndexes(systemUserId);
            result.put("user_index_cleanup", userCleanup);
        } catch (Exception e) {
            log.error("清理孤儿用户索引失败", e);
            result.put("user_index_cleanup", Map.of("error", e.getMessage()));
        }

        // 2. 清理用户私有索引中的孤儿文章
        try {
            Map<String, Object> privateCleanup = cleanOrphanedPrivateArticles(systemUserId);
            result.put("private_article_cleanup", privateCleanup);
        } catch (Exception e) {
            log.error("清理私有索引孤儿文章失败", e);
            result.put("private_article_cleanup", Map.of("error", e.getMessage()));
        }

        // 3. 清理全局公共索引中的孤儿文章
        try {
            Map<String, Object> globalCleanup = cleanOrphanedGlobalArticles(systemUserId);
            result.put("global_article_cleanup", globalCleanup);
        } catch (Exception e) {
            log.error("清理全局索引孤儿文章失败", e);
            result.put("global_article_cleanup", Map.of("error", e.getMessage()));
        }

        // 4. 清理 ES 中的孤儿文档
        try {
            Map<String, Object> esCleanup = cleanOrphanedESDocuments();
            result.put("es_cleanup", esCleanup);
        } catch (Exception e) {
            log.error("清理 ES 孤儿文档失败", e);
            result.put("es_cleanup", Map.of("error", e.getMessage()));
        }

        log.info("索引对账完成: {}", result);
        return result;
    }

    /**
     * 清理 RAG 中已无文章的用户索引目录
     */
    private Map<String, Object> cleanOrphanedUserIndexes(Long systemUserId) {
        // 获取 RAG 中所有用户 ID
        Map<String, Object> response = ragServiceClient.getIndexUsers(systemUserId);
        @SuppressWarnings("unchecked")
        List<Integer> ragUserIdsRaw = (List<Integer>) response.getOrDefault("user_ids", Collections.emptyList());
        List<Long> ragUserIds = ragUserIdsRaw.stream().map(Integer::longValue).collect(Collectors.toList());

        if (ragUserIds.isEmpty()) {
            log.info("RAG 中无用户索引，跳过用户索引清理");
            return Map.of("checked", 0, "cleaned", 0);
        }

        // 获取 MySQL 中有文章的所有用户 ID（查所有非删除文章）
        List<Article> allArticles = articleMapper.selectList(
                new LambdaQueryWrapper<Article>().eq(Article::getDeleted, 0)
        );
        Set<Long> mysqlUserIds = allArticles.stream()
                .map(Article::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        int cleaned = 0;
        for (Long ragUserId : ragUserIds) {
            if (ragUserId == -1) continue; // 跳过全局公共索引
            if (!mysqlUserIds.contains(ragUserId)) {
                try {
                    ragServiceClient.deleteUserIndex(systemUserId, ragUserId);
                    log.info("已清理孤儿用户索引: userId={}", ragUserId);
                    cleaned++;
                } catch (Exception e) {
                    log.warn("清理孤儿用户索引失败: userId={}", ragUserId, e);
                }
            }
        }

        log.info("用户索引清理完成: 检查 {} 个, 清理 {} 个", ragUserIds.size(), cleaned);
        return Map.of("checked", ragUserIds.size(), "cleaned", cleaned);
    }

    /**
     * 清理用户私有索引中的孤儿文章
     */
    private Map<String, Object> cleanOrphanedPrivateArticles(Long systemUserId) {
        // 获取 RAG 中所有用户 ID
        Map<String, Object> usersResponse = ragServiceClient.getIndexUsers(systemUserId);
        @SuppressWarnings("unchecked")
        List<Integer> ragUserIdsRaw = (List<Integer>) usersResponse.getOrDefault("user_ids", Collections.emptyList());

        int totalChecked = 0;
        int totalCleaned = 0;

        for (Integer userIdRaw : ragUserIdsRaw) {
            Long userId = userIdRaw.longValue();
            if (userId == -1) continue; // 跳过全局索引

            try {
                Map<String, Object> idsResponse = ragServiceClient.getArticleIds(userId);
                @SuppressWarnings("unchecked")
                List<Integer> articleIdsRaw = (List<Integer>) idsResponse.getOrDefault("article_ids", Collections.emptyList());
                List<Long> ragArticleIds = articleIdsRaw.stream().map(Integer::longValue).collect(Collectors.toList());

                if (ragArticleIds.isEmpty()) continue;

                totalChecked += ragArticleIds.size();

                // 查询 MySQL 中该用户的所有非删除文章 ID
                List<Article> userArticles = articleMapper.selectList(
                        new LambdaQueryWrapper<Article>()
                                .eq(Article::getUserId, userId)
                                .eq(Article::getDeleted, 0)
                );
                Set<Long> mysqlArticleIds = userArticles.stream()
                        .map(Article::getId)
                        .collect(Collectors.toSet());

                // 找出孤儿 ID 并删除
                List<Long> orphanIds = ragArticleIds.stream()
                        .filter(id -> !mysqlArticleIds.contains(id))
                        .collect(Collectors.toList());

                for (Long articleId : orphanIds) {
                    try {
                        ragServiceClient.removeArticle(userId, articleId);
                        log.info("已清理私有索引孤儿文章: userId={}, articleId={}", userId, articleId);
                        totalCleaned++;
                    } catch (Exception e) {
                        log.warn("清理私有索引孤儿文章失败: userId={}, articleId={}", userId, articleId, e);
                    }
                }
            } catch (Exception e) {
                log.warn("查询用户 {} 私有索引文章 ID 失败", userId, e);
            }
        }

        log.info("私有索引文章清理完成: 检查 {} 篇, 清理 {} 篇", totalChecked, totalCleaned);
        return Map.of("checked", totalChecked, "cleaned", totalCleaned);
    }

    /**
     * 清理全局公共索引中的孤儿文章
     */
    private Map<String, Object> cleanOrphanedGlobalArticles(Long systemUserId) {
        Map<String, Object> idsResponse = ragServiceClient.getGlobalArticleIds(systemUserId);
        @SuppressWarnings("unchecked")
        List<Integer> articleIdsRaw = (List<Integer>) idsResponse.getOrDefault("article_ids", Collections.emptyList());
        List<Long> ragArticleIds = articleIdsRaw.stream().map(Integer::longValue).collect(Collectors.toList());

        if (ragArticleIds.isEmpty()) {
            log.info("全局公共索引为空，跳过清理");
            return Map.of("checked", 0, "cleaned", 0);
        }

        // 查询 MySQL 中所有公开的非删除文章 ID
        List<Article> publicArticles = articleMapper.selectList(
                new LambdaQueryWrapper<Article>()
                        .eq(Article::getDeleted, 0)
        );
        // 只保留公开文章
        Set<Long> mysqlPublicArticleIds = publicArticles.stream()
                .filter(a -> a.getStatus() != null && a.getStatus().isPublic())
                .map(Article::getId)
                .collect(Collectors.toSet());

        int cleaned = 0;
        for (Long articleId : ragArticleIds) {
            if (!mysqlPublicArticleIds.contains(articleId)) {
                try {
                    ragServiceClient.removeGlobalArticle(systemUserId, articleId);
                    log.info("已清理全局索引孤儿文章: articleId={}", articleId);
                    cleaned++;
                } catch (Exception e) {
                    log.warn("清理全局索引孤儿文章失败: articleId={}", articleId, e);
                }
            }
        }

        log.info("全局索引文章清理完成: 检查 {} 篇, 清理 {} 篇", ragArticleIds.size(), cleaned);
        return Map.of("checked", ragArticleIds.size(), "cleaned", cleaned);
    }

    /**
     * 清理 ES 中的孤儿文档
     */
    private Map<String, Object> cleanOrphanedESDocuments() {
        List<Long> esArticleIds = searchService.getAllIndexedArticleIds();
        if (esArticleIds.isEmpty()) {
            log.info("ES 索引为空，跳过清理");
            return Map.of("checked", 0, "cleaned", 0);
        }

        // 查询 MySQL 中所有公开的非删除文章 ID
        List<Article> publicArticles = articleMapper.selectList(
                new LambdaQueryWrapper<Article>().eq(Article::getDeleted, 0)
        );
        Set<Long> mysqlPublicArticleIds = publicArticles.stream()
                .filter(a -> a.getStatus() != null && a.getStatus().isPublic())
                .map(Article::getId)
                .collect(Collectors.toSet());

        int cleaned = 0;
        for (Long articleId : esArticleIds) {
            if (!mysqlPublicArticleIds.contains(articleId)) {
                try {
                    searchService.deleteArticleIndex(articleId);
                    log.info("已清理 ES 孤儿文档: articleId={}", articleId);
                    cleaned++;
                } catch (Exception e) {
                    log.warn("清理 ES 孤儿文档失败: articleId={}", articleId, e);
                }
            }
        }

        log.info("ES 文档清理完成: 检查 {} 个, 清理 {} 个", esArticleIds.size(), cleaned);
        return Map.of("checked", esArticleIds.size(), "cleaned", cleaned);
    }

    /**
     * 全量重建所有索引（RAG 私有 + RAG 全局 + ES）
     * 先清空再重建，保证与 MySQL 完全一致
     */
    public Map<String, Object> rebuildAllIndexes() {
        Map<String, Object> result = new LinkedHashMap<>();
        Long systemUserId = getSystemUserId();
        if (systemUserId == null) {
            result.put("error", "无法获取系统用户 ID，重建终止");
            return result;
        }

        // 收集所有非删除文章
        List<Article> allArticles = articleMapper.selectList(
                new LambdaQueryWrapper<Article>().eq(Article::getDeleted, 0)
        );

        // 按用户分组
        Map<Long, List<Article>> articlesByUser = allArticles.stream()
                .collect(Collectors.groupingBy(Article::getUserId));

        // 公开文章（用于全局索引和 ES）
        List<Article> publicArticles = allArticles.stream()
                .filter(a -> a.getStatus() != null && a.getStatus().isPublic())
                .collect(Collectors.toList());

        // 1. 清理不在 MySQL 中的孤儿用户索引
        try {
            Map<String, Object> userCleanup = cleanOrphanedUserIndexes(systemUserId);
            result.put("orphaned_users_cleaned", userCleanup);
        } catch (Exception e) {
            log.error("清理孤儿用户索引失败", e);
            result.put("orphaned_users_cleaned", Map.of("error", e.getMessage()));
        }

        // 2. 为每个用户重建私有索引
        int usersRebuilt = 0;
        int privateArticlesRebuilt = 0;
        for (Map.Entry<Long, List<Article>> entry : articlesByUser.entrySet()) {
            Long userId = entry.getKey();
            List<Article> userArticles = entry.getValue();
            try {
                List<Map<String, Object>> articleList = userArticles.stream()
                        .map(a -> {
                            Map<String, Object> m = new HashMap<>();
                            m.put("id", a.getId());
                            m.put("title", a.getTitle());
                            m.put("content", a.getContent() != null ? a.getContent() : "");
                            return m;
                        })
                        .collect(Collectors.toList());

                Map<String, Object> request = new HashMap<>();
                request.put("user_id", userId);
                request.put("articles", articleList);
                ragServiceClient.rebuildIndex(userId, request);
                usersRebuilt++;
                privateArticlesRebuilt += userArticles.size();
                log.info("用户私有索引重建完成: userId={}, 文章数={}", userId, userArticles.size());
            } catch (Exception e) {
                log.warn("用户私有索引重建失败: userId={}", userId, e);
            }
        }
        result.put("private_index", Map.of("users_rebuilt", usersRebuilt,
                "articles_rebuilt", privateArticlesRebuilt));

        // 3. 重建全局公共索引
        try {
            List<Map<String, Object>> globalArticleList = publicArticles.stream()
                    .map(a -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("id", a.getId());
                        m.put("title", a.getTitle());
                        m.put("content", a.getContent() != null ? a.getContent() : "");
                        return m;
                    })
                    .collect(Collectors.toList());

            Map<String, Object> globalRequest = new HashMap<>();
            globalRequest.put("articles", globalArticleList);
            ragServiceClient.rebuildGlobalIndex(systemUserId, globalRequest);
            log.info("全局公共索引重建完成: 文章数={}", publicArticles.size());
            result.put("global_index", Map.of("articles_rebuilt", publicArticles.size()));
        } catch (Exception e) {
            log.error("全局公共索引重建失败", e);
            result.put("global_index", Map.of("error", e.getMessage()));
        }

        // 4. 重建 ES 索引
        try {
            int esCount = searchService.rebuildAllIndexes();
            log.info("ES 索引重建完成: 文章数={}", esCount);
            result.put("es_index", Map.of("articles_rebuilt", esCount));
        } catch (Exception e) {
            log.error("ES 索引重建失败", e);
            result.put("es_index", Map.of("error", e.getMessage()));
        }

        log.info("全量索引重建完成: {}", result);
        return result;
    }

    /**
     * 获取系统用户 ID（用于 RAG API 调用的身份标识）
     * 优先取第一个管理员用户，否则取第一个普通用户
     */
    private Long getSystemUserId() {
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<>());
        if (users.isEmpty()) return null;
        return users.stream()
                .filter(u -> "ROLE_ADMIN".equalsIgnoreCase(u.getRole()))
                .findFirst()
                .orElse(users.get(0))
                .getId();
    }
}
