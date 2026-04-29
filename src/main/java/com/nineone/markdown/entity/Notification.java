package com.nineone.markdown.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 通知实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("notification")
public class Notification {

    /**
     * 通知 ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 接收通知的用户 ID
     */
    @TableField(value = "user_id")
    private Long userId;

    /**
     * 通知类型：COMMENT-新评论, LIKE-新点赞, FAVORITE-新收藏, SYSTEM-系统通知
     */
    @TableField(value = "type")
    private String type;

    /**
     * 通知标题
     */
    @TableField(value = "title")
    private String title;

    /**
     * 通知内容
     */
    @TableField(value = "content")
    private String content;

    /**
     * 关联文章ID
     */
    @TableField(value = "related_article_id")
    private Long relatedArticleId;

    /**
     * 触发通知的用户ID
     */
    @TableField(value = "related_user_id")
    private Long relatedUserId;

    /**
     * 触发通知的用户名
     */
    @TableField(value = "related_user_name")
    private String relatedUserName;

    /**
     * 是否已读：0-未读，1-已读
     */
    @TableField(value = "is_read")
    private Integer isRead;

    /**
     * 通知创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
