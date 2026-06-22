package com.nineone.markdown.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nineone.markdown.entity.KnowledgeGraphGeneration;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识图谱生成状态 Mapper
 */
@Mapper
public interface KnowledgeGraphGenerationMapper extends BaseMapper<KnowledgeGraphGeneration> {
}
