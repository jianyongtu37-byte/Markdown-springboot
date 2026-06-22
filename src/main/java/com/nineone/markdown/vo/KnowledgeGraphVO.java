package com.nineone.markdown.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 知识图谱视图对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeGraphVO {
    private Long articleId;
    private String articleTitle;
    /** 状态: 0=pending, 1=generating, 2=success, 3=failed */
    private Integer status;
    private Integer nodeCount;
    private Integer edgeCount;
    private List<NodeVO> nodes;
    private List<EdgeVO> edges;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NodeVO {
        private Long id;
        private String name;
        private String type;
        private String description;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EdgeVO {
        private Long id;
        private Long sourceNodeId;
        private Long targetNodeId;
        private String sourceName;
        private String targetName;
        private String relation;
        private Double weight;
        private String description;
    }
}
