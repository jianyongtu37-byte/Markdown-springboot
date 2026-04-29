package com.nineone.markdown.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nineone.markdown.entity.Image;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 图片资源 Mapper 接口
 */
@Mapper
public interface ImageMapper extends BaseMapper<Image> {

    /**
     * 获取用户上传的图片列表
     * @param userId 用户ID
     * @return 图片列表
     */
    @Select("SELECT * FROM image WHERE user_id = #{userId} ORDER BY create_time DESC")
    List<Image> findByUserId(@Param("userId") Long userId);

    /**
     * 获取文章的关联图片列表
     * @param articleId 文章ID
     * @return 图片列表
     */
    @Select("SELECT * FROM image WHERE article_id = #{articleId} ORDER BY create_time DESC")
    List<Image> findByArticleId(@Param("articleId") Long articleId);
}
