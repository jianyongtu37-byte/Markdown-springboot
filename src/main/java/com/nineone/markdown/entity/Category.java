package com.nineone.markdown.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 分类表实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("category")
public class Category {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID，null表示系统默认分类
     */
    @TableField(value = "user_id")
    private Long userId;

    @TableField(value = "name")
    private String name;

    /**
     * 描述
     */
    @TableField(value = "description")
    private String description;

    @TableField(value = "sort_order")
    private Integer sortOrder;

    /**
     * 是否为系统默认分类（不可删除）
     */
    @TableField(value = "is_default")
    private Boolean isDefault;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
