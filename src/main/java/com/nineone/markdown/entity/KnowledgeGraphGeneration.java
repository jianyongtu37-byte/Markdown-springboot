package com.nineone.markdown.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 知识图谱生成状态实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("knowledge_graph_generation")
public class KnowledgeGraphGeneration {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("article_id")
    private Long articleId;

    /** 状态: 0=pending, 1=generating, 2=success, 3=failed */
    @TableField("status")
    private Integer status;

    @TableField("node_count")
    private Integer nodeCount;

    @TableField("edge_count")
    private Integer edgeCount;

    @TableField("error_message")
    private String errorMessage;

    @TableField("generate_time")
    private LocalDateTime generateTime;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
