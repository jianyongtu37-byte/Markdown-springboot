package com.nineone.markdown.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nineone.markdown.entity.ArticleTimestamp;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 文章时间戳目录表 Mapper 接口
 */
@Mapper
public interface ArticleTimestampMapper extends BaseMapper<ArticleTimestamp> {

    /**
     * 根据文章ID删除所有时间戳
     * @param articleId 文章ID
     */
    @Delete("DELETE FROM article_timestamp WHERE article_id = #{articleId}")
    void deleteByArticleId(@Param("articleId") Long articleId);

    /**
     * 根据文章ID查询时间戳列表，按秒数排序
     * @param articleId 文章ID
     * @return 时间戳列表
     */
    @Select("SELECT * FROM article_timestamp WHERE article_id = #{articleId} ORDER BY seconds")
    List<ArticleTimestamp> findByArticleId(@Param("articleId") Long articleId);
}