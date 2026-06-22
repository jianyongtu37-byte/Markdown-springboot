package com.nineone.markdown.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 知识图谱边（关系）实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("knowledge_edge")
public class KnowledgeEdge {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("article_id")
    private Long articleId;

    @TableField("source_node_id")
    private Long sourceNodeId;

    @TableField("target_node_id")
    private Long targetNodeId;

    @TableField("relation")
    private String relation;

    @TableField("weight")
    private Double weight;

    @TableField("description")
    private String description;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
