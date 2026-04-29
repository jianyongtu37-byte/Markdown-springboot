package com.nineone.markdown.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nineone.markdown.entity.ArticleVideo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文章视频关联表 Mapper 接口
 */
@Mapper
public interface ArticleVideoMapper extends BaseMapper<ArticleVideo> {
}