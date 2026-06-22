package com.nineone.markdown.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nineone.markdown.entity.KnowledgeEdge;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识图谱边（关系） Mapper
 */
@Mapper
public interface KnowledgeEdgeMapper extends BaseMapper<KnowledgeEdge> {
}
