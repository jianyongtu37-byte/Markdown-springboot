package com.nineone.markdown.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评论展示对象（树形结构）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentVO {

    /**
     * 评论 ID
     */
    private Long id;

    /**
     * 文章 ID
     */
    private Long articleId;

    /**
     * 评论用户 ID
     */
    private Long userId;

    /**
     * 评论用户昵称
     */
    private String userNickname;

    /**
     * 评论用户头像（预留）
     */
    private String userAvatar;

    /**
     * 父评论ID
     */
    private Long parentId;

    /**
     * 回复的目标用户ID
     */
    private Long replyToUserId;

    /**
     * 回复的目标用户名
     */
    private String replyToUsername;

    /**
     * 评论内容
     */
    private String content;

    /**
     * 评论状态：0-待审核，1-已通过，2-已拒绝
     */
    private Integer status;

    /**
     * 子回复列表
     */
    private List<CommentVO> replies;

    /**
     * 回复数量
     */
    private Integer replyCount;

    /**
     * 评论时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
