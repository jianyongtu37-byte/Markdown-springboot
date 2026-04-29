    package com.nineone.markdown.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.nineone.markdown.enums.ArticleStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章核心表实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("article")
public class Article {

    /**
     * 文章 ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 作者 ID (外键关联 sys_user)
     */
    @TableField(value = "user_id")
    private Long userId;

    /**
     * 分类 ID (外键关联 category)
     */
    @TableField(value = "category_id")
    private Long categoryId;

    /**
     * 文章标题
     */
    @TableField(value = "title")
    private String title;

    /**
     * Markdown 格式的纯文本内容
     */
    @TableField(value = "content")
    private String content;

    /**
     * 视频URL
     */
    @TableField(value = "video_url")
    private String videoUrl;

    /**
     * AI 自动生成的摘要内容
     */
    @TableField(value = "summary", insertStrategy = FieldStrategy.ALWAYS, updateStrategy = FieldStrategy.NOT_EMPTY)
    private String summary;

    /**
     * AI 摘要生成状态: 0-未生成, 1-生成中, 2-已生成, 3-生成失败
     */
    @TableField(value = "ai_status")
    private Integer aiStatus;

    /**
     * 文章状态：使用枚举 DRAFT(0), PRIVATE(1), PUBLIC(2)
     */
    @TableField(value = "status")
    private ArticleStatusEnum status;

    /**
     * 阅读量统计
     */
    @TableField(value = "view_count")
    private Integer viewCount;

    /**
     * 点赞数
     */
    @TableField(value = "like_count")
    private Integer likeCount;

    /**
     * 评论数
     */
    @TableField(value = "comment_count")
    private Integer commentCount;

    /**
     * 收藏数
     */
    @TableField(value = "favorite_count")
    private Integer favoriteCount;

    /**
     * 软删除标记：0-未删除，1-已删除
     */
    @TableField(value = "deleted")
    private Integer deleted;

    /**
     * 允许他人导出：0-禁止，1-允许（默认允许）
     */
    @TableField(value = "allow_export")
    private Integer allowExport;

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
