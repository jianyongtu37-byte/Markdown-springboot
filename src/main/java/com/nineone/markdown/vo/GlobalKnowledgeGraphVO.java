package com.nineone.markdown.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 全局知识图谱视图对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GlobalKnowledgeGraphVO {
    private Integer totalNodes;
    private Integer totalEdges;
    private Integer totalArticles;
    private List<KnowledgeGraphVO.NodeVO> nodes;
    private List<KnowledgeGraphVO.EdgeVO> edges;
}
