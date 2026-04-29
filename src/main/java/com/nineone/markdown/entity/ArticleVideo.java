package com.nineone.markdown.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.nineone.markdown.enums.VideoSource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文章视频关联实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("article_video")
public class ArticleVideo {

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
     * 视频URL
     */
    @TableField(value = "video_url")
    private String videoUrl;

    /**
     * 视频来源
     */
    @TableField(value = "video_source")
    private VideoSource videoSource;

    /**
     * 视频ID（YouTube videoId 或 B站 BV号）
     */
    @TableField(value = "video_id")
    private String videoId;

    /**
     * 视频总时长（秒）
     */
    @TableField(value = "duration")
    private Integer duration;

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