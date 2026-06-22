package com.nineone.markdown.controller;

import com.nineone.common.result.PageResult;
import com.nineone.common.result.Result;
import com.nineone.markdown.service.ArticleSeriesService;
import com.nineone.markdown.util.UserContextHolder;
import com.nineone.markdown.vo.ArticleSeriesVO;
import com.nineone.markdown.vo.SeriesSelectableArticleVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/series")
@RequiredArgsConstructor
public class ArticleSeriesController {

    private final ArticleSeriesService seriesService;

    @PostMapping
    public Result<ArticleSeriesVO> createSeries(@RequestBody Map<String, Object> body) {
        Long userId = UserContextHolder.requireUserId();
        String title = (String) body.get("title");
        String description = (String) body.get("description");
        Boolean isPublic = body.containsKey("isPublic") ? (Boolean) body.get("isPublic") : true;
        @SuppressWarnings("unchecked")
        List<Long> articleIds = body.containsKey("articleIds") ?
                ((List<?>) body.get("articleIds")).stream()
                        .map(item -> item instanceof Number ? ((Number) item).longValue() : Long.valueOf(item.toString()))
                        .toList() : null;
        ArticleSeriesVO vo = seriesService.createSeries(userId, title, description, isPublic, articleIds);
        return Result.success("系列创建成功", vo);
    }

    @PutMapping("/{id}")
    public Result<ArticleSeriesVO> updateSeries(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        Long userId = UserContextHolder.requireUserId();
        String title = (String) body.get("title");
        String description = (String) body.get("description");
        Boolean isPublic = body.containsKey("isPublic") ? (Boolean) body.get("isPublic") : null;
        ArticleSeriesVO vo = seriesService.updateSeries(id, userId, title, description, isPublic);
        return Result.success(vo);
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteSeries(@PathVariable("id") Long id) {
        Long userId = UserContextHolder.requireUserId();
        seriesService.deleteSeries(id, userId);
        return Result.success("系列已删除", null);
    }

    @GetMapping("/{id}")
    public Result<ArticleSeriesVO> getSeriesDetail(@PathVariable("id") Long id) {
        return Result.success(seriesService.getSeriesDetail(id));
    }

    @GetMapping
    public Result<PageResult<ArticleSeriesVO>> getMySeries(
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        Long userId = UserContextHolder.requireUserId();
        return Result.success(seriesService.getMySeries(userId, pageNum, pageSize));
    }

    @GetMapping("/user/{userId}")
    public Result<PageResult<ArticleSeriesVO>> getUserPublicSeries(
            @PathVariable("userId") Long userId,
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return Result.success(seriesService.getUserPublicSeries(userId, pageNum, pageSize));
    }

    @GetMapping("/{id}/available-articles")
    public Result<PageResult<SeriesSelectableArticleVO>> getSelectableArticles(
            @PathVariable("id") Long id,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        Long userId = UserContextHolder.requireUserId();
        return Result.success(seriesService.getSelectableArticles(id, userId, keyword, pageNum, pageSize));
    }

    @PostMapping("/{id}/articles")
    public Result<Void> addArticleToSeries(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        Long userId = UserContextHolder.requireUserId();
        Long articleId = Long.valueOf(body.get("articleId").toString());
        Integer sortOrder = body.containsKey("sortOrder") ? Integer.valueOf(body.get("sortOrder").toString()) : null;
        seriesService.addArticleToSeries(id, userId, articleId, sortOrder);
        return Result.success("文章已添加到系列", null);
    }

    @DeleteMapping("/{id}/articles/{articleId}")
    public Result<Void> removeArticleFromSeries(@PathVariable("id") Long id, @PathVariable("articleId") Long articleId) {
        Long userId = UserContextHolder.requireUserId();
        seriesService.removeArticleFromSeries(id, userId, articleId);
        return Result.success("文章已从系列中移除", null);
    }

    @PutMapping("/{id}/articles/sort")
    public Result<Void> updateArticleSort(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        Long userId = UserContextHolder.requireUserId();
        @SuppressWarnings("unchecked")
        List<Long> articleIds = ((List<?>) body.get("articleIds")).stream()
                .map(item -> item instanceof Number ? ((Number) item).longValue() : Long.valueOf(item.toString()))
                .toList();
        seriesService.updateArticleSort(id, userId, articleIds);
        return Result.success("排序已更新", null);
    }
}
