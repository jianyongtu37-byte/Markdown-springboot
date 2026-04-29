package com.nineone.markdown.controller;

import com.nineone.markdown.common.PageResult;
import com.nineone.markdown.common.Result;
import com.nineone.markdown.entity.ArticleComment;
import com.nineone.markdown.exception.AuthenticationException;
import com.nineone.markdown.security.CustomUserDetails;
import com.nineone.markdown.service.InteractionService;
import com.nineone.markdown.vo.ArticleVO;
import com.nineone.markdown.vo.CommentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 文章互动控制器（点赞、收藏、评论）
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class InteractionController {

    private final InteractionService interactionService;

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

    // ==================== 点赞 ====================

    /**
     * 点赞/取消点赞文章
     */
    @PostMapping("/articles/{articleId}/like")
    public Result<Map<String, Object>> toggleLike(@PathVariable Long articleId) {
        Long userId = getCurrentUserId();
        boolean liked = interactionService.toggleLike(articleId, userId);
        int likeCount = interactionService.getLikeCount(articleId);
        return Result.success(Map.of("liked", liked, "likeCount", likeCount));
    }

    /**
     * 查询用户是否已点赞
     */
    @GetMapping("/articles/{articleId}/like/status")
    public Result<Map<String, Object>> getLikeStatus(@PathVariable Long articleId) {
        Long userId = getCurrentUserId();
        boolean liked = interactionService.isLiked(articleId, userId);
        int likeCount = interactionService.getLikeCount(articleId);
        return Result.success(Map.of("liked", liked, "likeCount", likeCount));
    }

    /**
     * 获取文章的点赞数
     */
    @GetMapping("/articles/{articleId}/like/count")
    public Result<Map<String, Object>> getLikeCount(@PathVariable Long articleId) {
        int likeCount = interactionService.getLikeCount(articleId);
        return Result.success(Map.of("likeCount", likeCount));
    }

    // ==================== 收藏 ====================

    /**
     * 收藏/取消收藏文章
     */
    @PostMapping("/articles/{articleId}/favorite")
    public Result<Map<String, Object>> toggleFavorite(@PathVariable Long articleId,
                                                       @RequestParam(required = false, defaultValue = "默认收藏夹") String folderName) {
        Long userId = getCurrentUserId();
        boolean favorited = interactionService.toggleFavorite(articleId, userId, folderName);
        return Result.success(Map.of("favorited", favorited));
    }

    /**
     * 查询用户是否已收藏
     */
    @GetMapping("/articles/{articleId}/favorite/status")
    public Result<Map<String, Object>> getFavoriteStatus(@PathVariable Long articleId) {
        Long userId = getCurrentUserId();
        boolean favorited = interactionService.isFavorited(articleId, userId);
        return Result.success(Map.of("favorited", favorited));
    }

    /**
     * 获取我的收藏列表
     */
    @GetMapping("/favorites")
    public Result<PageResult<ArticleVO>> getMyFavorites(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String folderName) {
        Long userId = getCurrentUserId();
        PageResult<ArticleVO> result = interactionService.getMyFavorites(userId, pageNum, pageSize, folderName);
        return Result.success(result);
    }

    /**
     * 获取我的收藏夹名称列表（旧接口，兼容使用）
     */
    @GetMapping("/favorites/folder-names")
    public Result<List<String>> getMyFolderNames() {
        Long userId = getCurrentUserId();
        List<String> folders = interactionService.getMyFolderNames(userId);
        return Result.success(folders);
    }

    // ==================== 评论 ====================

    /**
     * 添加评论
     */
    @PostMapping("/articles/{articleId}/comments")
    public Result<Map<String, Object>> addComment(@PathVariable Long articleId,
                                                   @RequestBody Map<String, String> body) {
        Long userId = getCurrentUserId();
        String content = body.get("content");
        Long parentId = body.containsKey("parentId") && body.get("parentId") != null
                ? Long.parseLong(body.get("parentId")) : null;
        Long commentId = interactionService.addComment(articleId, userId, content, parentId);
        return Result.success("评论成功", Map.of("commentId", commentId));
    }

    /**
     * 获取文章的评论列表
     */
    @GetMapping("/articles/{articleId}/comments")
    public Result<PageResult<CommentVO>> getComments(
            @PathVariable Long articleId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        PageResult<CommentVO> result = interactionService.getComments(articleId, pageNum, pageSize);
        return Result.success(result);
    }

    /**
     * 删除评论
     */
    @DeleteMapping("/comments/{commentId}")
    public Result<Void> deleteComment(@PathVariable Long commentId) {
        Long userId = getCurrentUserId();
        interactionService.deleteComment(commentId, userId);
        return Result.success("评论已删除", null);
    }

    /**
     * 审核评论（管理员功能）
     */
    @PutMapping("/comments/{commentId}/review")
    public Result<Void> reviewComment(@PathVariable Long commentId, @RequestParam Integer status) {
        interactionService.reviewComment(commentId, status);
        return Result.success("评论审核完成", null);
    }

    /**
     * 获取待审核评论列表（管理员功能）
     */
    @GetMapping("/comments/pending")
    public Result<List<ArticleComment>> getPendingComments() {
        List<ArticleComment> comments = interactionService.getPendingComments();
        return Result.success(comments);
    }

    // ==================== 热门文章 ====================

    /**
     * 获取热门文章排行榜
     */
    @GetMapping("/articles/hot")
    public Result<List<ArticleVO>> getHotArticles(
            @RequestParam(defaultValue = "views") String type,
            @RequestParam(defaultValue = "10") int limit) {
        List<ArticleVO> articles = interactionService.getHotArticles(type, limit);
        return Result.success(articles);
    }
}
