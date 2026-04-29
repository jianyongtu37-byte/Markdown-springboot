package com.nineone.markdown.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文章版本历史实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("article_version")
public class ArticleVersion {

    /**
     * 版本 ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 文章 ID
     */
    @TableField(value = "article_id")
    private Long articleId;

    /**
     * 版本号（从1开始递增）
     */
    @TableField(value = "version")
    private Integer version;

    /**
     * 版本标题
     */
    @TableField(value = "title")
    private String title;

    /**
     * 版本内容（Markdown格式）
     */
    @TableField(value = "content")
    private String content;

    /**
     * 版本摘要
     */
    @TableField(value = "summary")
    private String summary;

    /**
     * 修改备注
     */
    @TableField(value = "change_note")
    private String changeNote;

    /**
     * 修改者ID
     */
    @TableField(value = "operator_id")
    private Long operatorId;

    /**
     * 修改者名称
     */
    @TableField(value = "operator_name")
    private String operatorName;

    /**
     * 版本创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
