package com.nineone.markdown.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nineone.markdown.entity.Tag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 标签表 Mapper 接口
 */
@Mapper
public interface TagMapper extends BaseMapper<Tag> {

    /**
     * 查询热门标签（按使用次数降序）
     * 使用 LEFT JOIN + GROUP BY 一次查询替代 N+1 次 COUNT
     */
    @Select("SELECT t.id, t.name, t.create_time, COUNT(at.article_id) AS use_count " +
            "FROM tag t LEFT JOIN article_tag at ON t.id = at.tag_id " +
            "GROUP BY t.id, t.name, t.create_time " +
            "ORDER BY use_count DESC LIMIT #{limit}")
    List<Map<String, Object>> selectPopularTags(@Param("limit") int limit);
}