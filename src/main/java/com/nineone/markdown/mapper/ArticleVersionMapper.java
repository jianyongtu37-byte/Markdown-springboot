package com.nineone.markdown.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nineone.markdown.entity.ArticleVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 文章版本历史 Mapper 接口
 */
@Mapper
public interface ArticleVersionMapper extends BaseMapper<ArticleVersion> {

    /**
     * 获取文章的最大版本号
     * @param articleId 文章ID
     * @return 最大版本号，如果没有版本则返回0
     */
    @Select("SELECT COALESCE(MAX(version), 0) FROM article_version WHERE article_id = #{articleId}")
    Integer getMaxVersion(@Param("articleId") Long articleId);
}
