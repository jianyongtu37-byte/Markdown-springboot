package com.nineone.markdown.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 知识图谱节点实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("knowledge_node")
public class KnowledgeNode {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("article_id")
    private Long articleId;

    @TableField("name")
    private String name;

    @TableField("type")
    private String type;

    @TableField("description")
    private String description;

    @TableField(value = "properties", insertStrategy = FieldStrategy.ALWAYS)
    private String properties;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
