package com.nineone.markdown.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nineone.markdown.entity.KnowledgeNode;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识图谱节点 Mapper
 */
@Mapper
public interface KnowledgeNodeMapper extends BaseMapper<KnowledgeNode> {
}
