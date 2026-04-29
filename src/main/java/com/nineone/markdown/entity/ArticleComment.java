package com.nineone.markdown.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文章评论实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("article_comment")
public class ArticleComment {

    /**
     * 评论 ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 文章 ID
     */
    @TableField(value = "article_id")
    private Long articleId;

    /**
     * 评论用户 ID
     */
    @TableField(value = "user_id")
    private Long userId;

    /**
     * 父评论ID（支持二级回复，为NULL表示一级评论）
     */
    @TableField(value = "parent_id")
    private Long parentId;

    /**
     * 回复的目标用户ID
     */
    @TableField(value = "reply_to_user_id")
    private Long replyToUserId;

    /**
     * 回复的目标用户名
     */
    @TableField(value = "reply_to_username")
    private String replyToUsername;

    /**
     * 评论内容
     */
    @TableField(value = "content")
    private String content;

    /**
     * 评论状态：0-待审核，1-已通过，2-已拒绝
     */
    @TableField(value = "status")
    private Integer status;

    /**
     * 评论时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
