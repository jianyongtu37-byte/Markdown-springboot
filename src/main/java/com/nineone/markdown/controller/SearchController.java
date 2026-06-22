package com.nineone.markdown.controller;

import com.nineone.common.result.Result;
import com.nineone.markdown.service.SearchService;
import com.nineone.markdown.vo.SearchResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 搜索控制器
 * 提供全文检索和高亮搜索功能
 */
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@Validated
public class SearchController {

    private final SearchService searchService;

    /**
     * 全文搜索文章（带高亮）
     */
    @GetMapping("/articles")
    public Result<List<SearchResultVO>> searchArticles(
            @RequestParam("keyword") String keyword,
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        
        List<SearchResultVO> results = searchService.searchArticles(keyword, pageNum, pageSize);
        return Result.success("搜索完成", results);
    }

    /**
     * 根据作者搜索文章
     */
    @GetMapping("/author")
    public Result<List<SearchResultVO>> searchByAuthor(
            @RequestParam("authorName") String authorName,
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        
        List<SearchResultVO> results = searchService.searchByAuthor(authorName, pageNum, pageSize);
        return Result.success("搜索完成", results);
    }

    /**
     * 根据分类搜索文章
     */
    @GetMapping("/category")
    public Result<List<SearchResultVO>> searchByCategory(
            @RequestParam("categoryName") String categoryName,
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        
        List<SearchResultVO> results = searchService.searchByCategory(categoryName, pageNum, pageSize);
        return Result.success("搜索完成", results);
    }

    /**
     * 根据标签搜索文章
     */
    @GetMapping("/tag")
    public Result<List<SearchResultVO>> searchByTag(
            @RequestParam("tag") String tag,
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        
        List<SearchResultVO> results = searchService.searchByTag(tag, pageNum, pageSize);
        return Result.success("搜索完成", results);
    }

    /**
     * 获取搜索建议（自动补全）
     */
    @GetMapping("/suggestions")
    public Result<List<String>> getSearchSuggestions(
            @RequestParam("prefix") String prefix,
            @RequestParam(value = "limit", defaultValue = "10") Integer limit) {
        
        List<String> suggestions = searchService.getSearchSuggestions(prefix, limit);
        return Result.success("获取搜索建议成功", suggestions);
    }

    /**
     * 重建所有文章索引（管理员功能）
     */
    @PostMapping("/rebuild-indexes")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Integer> rebuildAllIndexes() {
        int count = searchService.rebuildAllIndexes();
        return Result.success("索引重建完成", count);
    }

    /**
     * 获取索引统计信息
     */
    @GetMapping("/stats")
    public Result<Long> getIndexStats() {
        long count = searchService.getIndexStats();
        return Result.success("获取索引统计成功", count);
    }

    /**
     * 索引单篇文章（管理员功能）
     */
    @PostMapping("/index/{articleId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Boolean> indexArticle(@PathVariable("articleId") Long articleId) {
        boolean success = searchService.indexArticle(articleId);
        if (success) {
            return Result.success("文章索引成功", true);
        } else {
            return Result.failure("文章索引失败", false);
        }
    }

    /**
     * 删除文章索引（管理员功能）
     */
    @DeleteMapping("/index/{articleId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Boolean> deleteArticleIndex(@PathVariable("articleId") Long articleId) {
        boolean success = searchService.deleteArticleIndex(articleId);
        if (success) {
            return Result.success("文章索引删除成功", true);
        } else {
            return Result.failure("文章索引删除失败", false);
        }
    }
}