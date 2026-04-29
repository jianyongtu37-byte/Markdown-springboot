package com.nineone.markdown.controller;

import com.nineone.markdown.common.Result;
import com.nineone.markdown.entity.ArticleVersion;
import com.nineone.markdown.exception.AuthenticationException;
import com.nineone.markdown.security.CustomUserDetails;
import com.nineone.markdown.service.ArticleVersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
     * 获取当前登录用户的ID
     */
    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthenticationException("用户未认证", "UNAUTHENTICATED");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof CustomUserDetails) {
            return ((CustomUserDetails) principal).getId();
        }
        throw new AuthenticationException("用户未登录或登录已过期", "TOKEN_EXPIRED");
    }

    /**
     * 获取当前登录用户的昵称
     */
    private String getCurrentUserNickname() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return "未知用户";
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof CustomUserDetails) {
            return ((CustomUserDetails) principal).getNickname();
        }
        return "未知用户";
    }

    /**
     * 获取文章的所有版本列表
     */
    @GetMapping
    public Result<List<ArticleVersion>> getVersions(@PathVariable Long articleId) {
        List<ArticleVersion> versions = articleVersionService.getVersionsByArticleId(articleId);
        return Result.success(versions);
    }

    /**
     * 获取指定版本的详情
     */
    @GetMapping("/{versionId}")
    public Result<ArticleVersion> getVersion(@PathVariable Long articleId, @PathVariable Long versionId) {
        ArticleVersion version = articleVersionService.getVersionById(versionId);
        return Result.success(version);
    }

    /**
     * 回滚到指定版本
     */
    @PostMapping("/{versionId}/rollback")
    public Result<Void> rollback(@PathVariable Long articleId, @PathVariable Long versionId,
                                 @RequestParam(required = false) String changeNote) {
        Long userId = getCurrentUserId();
        String nickname = getCurrentUserNickname();
        articleVersionService.rollbackToVersion(articleId, versionId, userId, nickname);
        return Result.success("文章已回滚到版本" + versionId, null);
    }

    /**
     * 比较两个版本的差异
     */
    @GetMapping("/diff")
    public Result<String> diff(@PathVariable Long articleId,
                               @RequestParam Long versionId1,
                               @RequestParam Long versionId2) {
        String diff = articleVersionService.diffVersions(versionId1, versionId2);
        return Result.success(diff);
    }
}
