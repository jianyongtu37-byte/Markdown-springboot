package com.nineone.markdown.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nineone.markdown.entity.Tag;
import org.apache.ibatis.annotations.Mapper;

/**
 * 标签表 Mapper 接口
 */
@Mapper
public interface TagMapper extends BaseMapper<Tag> {
}