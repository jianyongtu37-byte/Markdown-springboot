package com.nineone.markdown.controller;

import com.nineone.common.result.PageResult;
import com.nineone.common.result.Result;
import com.nineone.markdown.entity.ArticleComment;
import com.nineone.markdown.service.InteractionService;
import com.nineone.markdown.util.UserContextHolder;
import com.nineone.markdown.vo.ArticleVO;
import com.nineone.markdown.vo.CommentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
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

    // ==================== 点赞 ====================

    /**
     * 点赞/取消点赞文章
     */
    @PostMapping("/articles/{articleId}/like")
    public Result<Map<String, Object>> toggleLike(@PathVariable("articleId") Long articleId) {
        Long userId = UserContextHolder.requireUserId();
        boolean liked = interactionService.toggleLike(articleId, userId);
        int likeCount = interactionService.getLikeCount(articleId);
        return Result.success(Map.of("liked", liked, "likeCount", likeCount));
    }

    /**
     * 查询用户是否已点赞
     */
    @GetMapping("/articles/{articleId}/like/status")
    public Result<Map<String, Object>> getLikeStatus(@PathVariable("articleId") Long articleId) {
        Long userId = UserContextHolder.requireUserId();
        boolean liked = interactionService.isLiked(articleId, userId);
        int likeCount = interactionService.getLikeCount(articleId);
        return Result.success(Map.of("liked", liked, "likeCount", likeCount));
    }

    /**
     * 获取文章的点赞数
     */
    @GetMapping("/articles/{articleId}/like/count")
    public Result<Map<String, Object>> getLikeCount(@PathVariable("articleId") Long articleId) {
        int likeCount = interactionService.getLikeCount(articleId);
        return Result.success(Map.of("likeCount", likeCount));
    }

    /**
     * 获取我的点赞列表
     */
    @GetMapping("/likes")
    public Result<PageResult<ArticleVO>> getMyLikes(
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        Long userId = UserContextHolder.requireUserId();
        PageResult<ArticleVO> result = interactionService.getMyLikes(userId, pageNum, pageSize);
        return Result.success(result);
    }

    // ==================== 收藏 ====================

    /**
     * 收藏/取消收藏文章
     */
    @PostMapping("/articles/{articleId}/favorite")
    public Result<Map<String, Object>> toggleFavorite(@PathVariable("articleId") Long articleId,
                                                       @RequestParam(value = "folderName", required = false, defaultValue = "默认收藏夹") String folderName) {
        Long userId = UserContextHolder.requireUserId();
        boolean favorited = interactionService.toggleFavorite(articleId, userId, folderName);
        return Result.success(Map.of("favorited", favorited));
    }

    /**
     * 查询用户是否已收藏
     */
    @GetMapping("/articles/{articleId}/favorite/status")
    public Result<Map<String, Object>> getFavoriteStatus(@PathVariable("articleId") Long articleId) {
        Long userId = UserContextHolder.requireUserId();
        boolean favorited = interactionService.isFavorited(articleId, userId);
        return Result.success(Map.of("favorited", favorited));
    }

    /**
     * 获取我的收藏列表
     */
    @GetMapping("/favorites")
    public Result<PageResult<ArticleVO>> getMyFavorites(
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(value = "folderName", required = false) String folderName) {
        Long userId = UserContextHolder.requireUserId();
        PageResult<ArticleVO> result = interactionService.getMyFavorites(userId, pageNum, pageSize, folderName);
        return Result.success(result);
    }

    /**
     * 获取我的收藏夹名称列表（旧接口，兼容使用）
     */
    @GetMapping("/favorites/folder-names")
    public Result<List<String>> getMyFolderNames() {
        Long userId = UserContextHolder.requireUserId();
        List<String> folders = interactionService.getMyFolderNames(userId);
        return Result.success(folders);
    }

    // ==================== 评论 ====================

    /**
     * 添加评论
     */
    @PostMapping("/articles/{articleId}/comments")
    public Result<Map<String, Object>> addComment(@PathVariable("articleId") Long articleId,
                                                    @RequestBody Map<String, String> body) {
        Long userId = UserContextHolder.requireUserId();
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
            @PathVariable("articleId") Long articleId,
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        PageResult<CommentVO> result = interactionService.getComments(articleId, pageNum, pageSize);
        return Result.success(result);
    }

    /**
     * 删除评论
     */
    @DeleteMapping("/comments/{commentId}")
    public Result<Void> deleteComment(@PathVariable("commentId") Long commentId) {
        Long userId = UserContextHolder.requireUserId();
        interactionService.deleteComment(commentId, userId);
        return Result.success("评论已删除", null);
    }

    /**
     * 审核评论（管理员功能）
     */
    @PutMapping("/comments/{commentId}/review")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> reviewComment(@PathVariable("commentId") Long commentId, @RequestParam("status") Integer status) {
        interactionService.reviewComment(commentId, status);
        String message = status == 1 ? "审核通过" : "审核拒绝";
        return Result.success(message, null);
    }

    /**
     * 获取待审核评论列表（管理员功能）
     */
    @GetMapping("/comments/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<ArticleComment>> getPendingComments() {
        List<ArticleComment> comments = interactionService.getPendingComments();
        return Result.success(comments);
    }

    /**
     * 获取当前用户的评论历史
     */
    @GetMapping("/comments/my")
    public Result<PageResult<CommentVO>> getMyComments(
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        Long userId = UserContextHolder.requireUserId();
        PageResult<CommentVO> result = interactionService.getMyComments(userId, pageNum, pageSize);
        return Result.success(result);
    }

    // ==================== 热门文章 ====================

    /**
     * 获取热门文章排行榜
     */
    @GetMapping("/articles/hot")
    public Result<List<ArticleVO>> getHotArticles(
            @RequestParam(value = "type", defaultValue = "views") String type,
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        List<ArticleVO> articles = interactionService.getHotArticles(type, limit);
        return Result.success(articles);
    }
}
