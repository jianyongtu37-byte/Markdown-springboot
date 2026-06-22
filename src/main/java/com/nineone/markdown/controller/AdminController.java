package com.nineone.markdown.controller;

import com.nineone.common.result.Result;
import com.nineone.markdown.mapper.ArticleMapper;
import com.nineone.markdown.service.impl.IndexReconciliationService;
import com.nineone.markdown.util.UserContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理员功能控制器（计数修复、系统管理等）
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final ArticleMapper articleMapper;
    private final IndexReconciliationService indexReconciliationService;

    /**
     * 从源表重新计算所有文章的计数字段（like_count, comment_count, favorite_count）
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/articles/reconcile-counts")
    public Result<Map<String, Object>> reconcileAllCounts() {
        log.info("管理员 {} 触发全量计数修复", UserContextHolder.requireUserId());
        long start = System.currentTimeMillis();
        articleMapper.reconcileAllCounts();
        long elapsed = System.currentTimeMillis() - start;
        log.info("全量计数修复完成，耗时 {}ms", elapsed);
        return Result.success("计数修复完成", Map.of("elapsedMs", elapsed));
    }

    /**
     * 从源表重新计算单篇文章的计数字段
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/articles/{id}/reconcile-counts")
    public Result<String> reconcileArticleCounts(@PathVariable("id") Long id) {
        log.info("管理员 {} 触发文章 {} 计数修复", UserContextHolder.requireUserId(), id);
        articleMapper.reconcileCountsByArticleId(id);
        return Result.success("文章计数修复完成", null);
    }

    /**
     * 增量清理 RAG 向量库和 ES 中的孤儿索引（已删除但残留的文章）
     * 对比 FAISS/ES 与 MySQL，只删除 MySQL 中不存在的文章索引
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/index/reconcile")
    public Result<Map<String, Object>> reconcileIndexes() {
        log.info("管理员 {} 触发索引对账清理", UserContextHolder.requireUserId());
        Map<String, Object> result = indexReconciliationService.reconcileAll();
        return Result.success(result);
    }

    /**
     * 全量重建所有索引（清空 RAG + ES，从 MySQL 重新导入）
     * 比增量清理更彻底，但耗时更长
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/index/rebuild")
    public Result<Map<String, Object>> rebuildIndexes() {
        log.info("管理员 {} 触发全量索引重建", UserContextHolder.requireUserId());
        Map<String, Object> result = indexReconciliationService.rebuildAllIndexes();
        return Result.success(result);
    }
}
