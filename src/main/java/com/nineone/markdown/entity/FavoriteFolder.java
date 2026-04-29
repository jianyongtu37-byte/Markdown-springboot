package com.nineone.markdown.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 收藏夹分类实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("favorite_folder")
public class FavoriteFolder {

    /**
     * 主键 ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户 ID
     */
    @TableField(value = "user_id")
    private Long userId;

    /**
     * 收藏夹名称
     */
    @TableField(value = "name")
    private String name;

    /**
     * 收藏夹描述
     */
    @TableField(value = "description")
    private String description;

    /**
     * 排序字段（数字越小越靠前）
     */
    @TableField(value = "sort_order")
    private Integer sortOrder;

    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
