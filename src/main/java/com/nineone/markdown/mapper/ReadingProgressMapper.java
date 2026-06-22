package com.nineone.markdown.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nineone.markdown.entity.ReadingProgress;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ReadingProgressMapper extends BaseMapper<ReadingProgress> {

    @Select("SELECT * FROM reading_progress WHERE user_id = #{userId} AND article_id = #{articleId}")
    ReadingProgress findByUserIdAndArticleId(@Param("userId") Long userId, @Param("articleId") Long articleId);
}
