package com.nineone.markdown.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文章时间戳目录实体类
 * 用于标记文章中的关键时间点
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("article_timestamp")
public class ArticleTimestamp {

    /**
     * 主键 ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 文章 ID（逻辑外键，关联 article 表）
     */
    @TableField(value = "article_id")
    private Long articleId;

    /**
     * 时间标签（如 "01:27"）
     */
    @TableField(value = "label")
    private String label;

    /**
     * 时间戳对应的秒数（如 87）
     */
    @TableField(value = "seconds")
    private Integer seconds;

    /**
     * 时间点对应的内容摘要
     */
    @TableField(value = "excerpt")
    private String excerpt;

    /**
     * 在文章内容中的行号
     */
    @TableField(value = "line_no")
    private Integer lineNo;
}