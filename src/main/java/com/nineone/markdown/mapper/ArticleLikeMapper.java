package com.nineone.markdown.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nineone.markdown.entity.ArticleLike;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 文章点赞 Mapper 接口
 */
@Mapper
public interface ArticleLikeMapper extends BaseMapper<ArticleLike> {

    /**
     * 查询用户是否已点赞某文章
     * @param articleId 文章ID
     * @param userId 用户ID
     * @return 点赞记录数（0或1）
     */
    @Select("SELECT COUNT(*) FROM article_like WHERE article_id = #{articleId} AND user_id = #{userId}")
    int countByArticleIdAndUserId(@Param("articleId") Long articleId, @Param("userId") Long userId);
}
