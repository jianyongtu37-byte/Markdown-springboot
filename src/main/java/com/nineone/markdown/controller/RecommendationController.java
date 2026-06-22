package com.nineone.markdown.controller;

import com.nineone.common.result.Result;
import com.nineone.markdown.service.RecommendationService;
import com.nineone.markdown.vo.ArticleVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 文章推荐控制器
 * 基于标签相似度的文章推荐和 AI 标签生成
 */
@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
@Slf4j
public class RecommendationController {

    private final RecommendationService recommendationService;

    /**
     * 根据文章ID推荐相关文章
     * @param articleId 当前文章ID
     * @param limit 推荐数量，默认6
     */
    @GetMapping("/article/{articleId}")
    public Result<List<ArticleVO>> getByArticleId(
            @PathVariable Long articleId,
            @RequestParam(value = "limit", defaultValue = "6") int limit) {
        List<ArticleVO> recommendations = recommendationService.getRecommendationsByArticleId(articleId, limit);
        return Result.success(recommendations);
    }

    /**
     * 根据标签列表推荐文章
     * @param tagNames 标签名称列表
     * @param limit 推荐数量，默认6
     */
    @PostMapping("/by-tags")
    public Result<List<ArticleVO>> getByTags(
            @RequestBody List<String> tagNames,
            @RequestParam(value = "limit", defaultValue = "6") int limit) {
        List<ArticleVO> recommendations = recommendationService.getRecommendationsByTags(tagNames, limit);
        return Result.success(recommendations);
    }

    /**
     * AI 生成标签
     * @param request 包含 content 字段的请求体
     */
    @PostMapping("/generate-tags")
    public Result<List<String>> generateTags(@RequestBody Map<String, String> request) {
        String content = request.get("content");
        if (content == null || content.isBlank()) {
            return Result.badRequest("文章内容不能为空");
        }
        List<String> tags = recommendationService.generateTags(content);
        return Result.success("标签生成成功", tags);
    }
}
