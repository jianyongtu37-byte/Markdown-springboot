package com.nineone.markdown.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nineone.markdown.entity.ArticleComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 文章评论 Mapper 接口
 */
@Mapper
public interface ArticleCommentMapper extends BaseMapper<ArticleComment> {

    /**
     * 获取文章的一级评论列表（按时间倒序）
     * @param articleId 文章ID
     * @return 一级评论列表
     */
    @Select("SELECT * FROM article_comment WHERE article_id = #{articleId} AND parent_id IS NULL AND status = 1 ORDER BY create_time DESC")
    List<ArticleComment> findRootCommentsByArticleId(@Param("articleId") Long articleId);

    /**
     * 获取评论的回复列表（按时间正序）
     * @param parentId 父评论ID
     * @return 回复列表
     */
    @Select("SELECT * FROM article_comment WHERE parent_id = #{parentId} AND status = 1 ORDER BY create_time ASC")
    List<ArticleComment> findRepliesByParentId(@Param("parentId") Long parentId);

    /**
     * 获取文章的评论总数（仅统计已通过的）
     * @param articleId 文章ID
     * @return 评论总数
     */
    @Select("SELECT COUNT(*) FROM article_comment WHERE article_id = #{articleId} AND status = 1")
    int countApprovedByArticleId(@Param("articleId") Long articleId);

    /**
     * 获取待审核的评论列表
     * @return 待审核评论列表
     */
    @Select("SELECT * FROM article_comment WHERE status = 0 ORDER BY create_time ASC")
    List<ArticleComment> findPendingComments();
}
