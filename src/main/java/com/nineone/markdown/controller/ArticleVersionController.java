package com.nineone.markdown.controller;

import com.nineone.common.result.Result;
import com.nineone.markdown.entity.ArticleVersion;
import com.nineone.markdown.service.ArticleVersionService;
import com.nineone.markdown.util.UserContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 文章版本历史控制器
 */
@RestController
@RequestMapping("/api/articles/{articleId}/versions")
@RequiredArgsConstructor
public class ArticleVersionController {

    private final ArticleVersionService articleVersionService;

    /**
     * 获取文章的所有版本列表
     */
    @GetMapping
    public Result<List<ArticleVersion>> getVersions(@PathVariable("articleId") Long articleId) {
        List<ArticleVersion> versions = articleVersionService.getVersionsByArticleId(articleId);
        return Result.success(versions);
    }

    /**
     * 获取指定版本的详情
     */
    @GetMapping("/{versionId}")
    public Result<ArticleVersion> getVersion(@PathVariable("articleId") Long articleId, @PathVariable("versionId") Long versionId) {
        ArticleVersion version = articleVersionService.getVersionById(versionId);
        return Result.success(version);
    }

    /**
     * 回滚到指定版本
     */
    @PostMapping("/{versionId}/rollback")
    public Result<Void> rollback(@PathVariable("articleId") Long articleId, @PathVariable("versionId") Long versionId,
                                 @RequestParam(value = "changeNote", required = false) String changeNote) {
        Long userId = UserContextHolder.requireUserId();
        String nickname = UserContextHolder.getCurrentUserNickname();
        articleVersionService.rollbackToVersion(articleId, versionId, userId, nickname);
        return Result.success("文章已回滚到版本" + versionId, null);
    }

    /**
     * 比较两个版本的差异
     */
    @GetMapping("/diff")
    public Result<String> diff(@PathVariable("articleId") Long articleId,
                               @RequestParam(value = "versionId1") Long versionId1,
                               @RequestParam(value = "versionId2") Long versionId2) {
        String diff = articleVersionService.diffVersions(versionId1, versionId2);
        return Result.success(diff);
    }
}
