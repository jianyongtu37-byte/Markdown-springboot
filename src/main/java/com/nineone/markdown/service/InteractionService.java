package com.nineone.markdown.service;

import com.nineone.markdown.common.PageResult;
import com.nineone.markdown.entity.ArticleComment;
import com.nineone.markdown.vo.ArticleVO;
import com.nineone.markdown.vo.CommentVO;

import java.util.List;
import java.util.Map;

/**
 * 文章互动服务接口（点赞、收藏、评论）
 */
public interface InteractionService {

    // ==================== 点赞 ====================

    /**
     * 点赞/取消点赞文章（切换操作）
     * @param articleId 文章ID
     * @param userId 用户ID
     * @return true-已点赞, false-已取消点赞
     */
    boolean toggleLike(Long articleId, Long userId);

    /**
     * 查询用户是否已点赞
     * @param articleId 文章ID
     * @param userId 用户ID
     * @return 是否已点赞
     */
    boolean isLiked(Long articleId, Long userId);

    /**
     * 获取文章的点赞数
     * @param articleId 文章ID
     * @return 点赞数
     */
    int getLikeCount(Long articleId);

    // ==================== 收藏 ====================

    /**
     * 收藏/取消收藏文章（切换操作）
     * @param articleId 文章ID
     * @param userId 用户ID
     * @param folderName 收藏夹名称
     * @return true-已收藏, false-已取消收藏
     */
    boolean toggleFavorite(Long articleId, Long userId, String folderName);

    /**
     * 查询用户是否已收藏
     * @param articleId 文章ID
     * @param userId 用户ID
     * @return 是否已收藏
     */
    boolean isFavorited(Long articleId, Long userId);

    /**
     * 获取用户的收藏列表
     * @param userId 用户ID
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @param folderName 收藏夹名称（可选）
     * @return 分页结果
     */
    PageResult<ArticleVO> getMyFavorites(Long userId, Integer pageNum, Integer pageSize, String folderName);

    /**
     * 获取用户的收藏夹列表
     * @param userId 用户ID
     * @return 收藏夹名称列表
     */
    List<String> getMyFolderNames(Long userId);

    // ==================== 评论 ====================

    /**
     * 添加评论
     * @param articleId 文章ID
     * @param userId 用户ID
     * @param content 评论内容
     * @param parentId 父评论ID（可选，用于回复）
     * @return 评论ID
     */
    Long addComment(Long articleId, Long userId, String content, Long parentId);

    /**
     * 获取文章的评论列表（树形结构）
     * @param articleId 文章ID
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @return 分页结果
     */
    PageResult<CommentVO> getComments(Long articleId, Integer pageNum, Integer pageSize);

    /**
     * 删除评论（仅评论作者或文章作者可删除）
     * @param commentId 评论ID
     * @param userId 用户ID
     */
    void deleteComment(Long commentId, Long userId);

    /**
     * 审核评论（管理员功能）
     * @param commentId 评论ID
     * @param status 审核状态：1-通过，2-拒绝
     */
    void reviewComment(Long commentId, Integer status);

    /**
     * 获取待审核评论列表
     * @return 待审核评论列表
     */
    List<ArticleComment> getPendingComments();

    // ==================== 热门文章 ====================

    /**
     * 获取热门文章排行榜
     * @param type 排行类型：views-阅读量, likes-点赞数, favorites-收藏数
     * @param limit 限制数量
     * @return 文章列表
     */
    List<ArticleVO> getHotArticles(String type, int limit);
}
