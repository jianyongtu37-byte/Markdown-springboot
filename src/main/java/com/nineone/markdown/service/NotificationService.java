package com.nineone.markdown.service;

import com.nineone.markdown.entity.Notification;

import java.util.List;

/**
 * 通知服务接口
 */
public interface NotificationService {

    /**
     * 创建通知
     * @param userId 接收通知的用户ID
     * @param type 通知类型：COMMENT-新评论, LIKE-新点赞, FAVORITE-新收藏, SYSTEM-系统通知
     * @param title 通知标题
     * @param content 通知内容
     * @param relatedArticleId 关联文章ID
     * @param relatedUserId 触发通知的用户ID
     * @param relatedUserName 触发通知的用户名
     */
    void createNotification(Long userId, String type, String title, String content,
                            Long relatedArticleId, Long relatedUserId, String relatedUserName);

    /**
     * 获取用户的未读通知列表
     * @param userId 用户ID
     * @return 未读通知列表
     */
    List<Notification> getUnreadNotifications(Long userId);

    /**
     * 获取用户的所有通知列表
     * @param userId 用户ID
     * @return 通知列表
     */
    List<Notification> getAllNotifications(Long userId);

    /**
     * 获取用户的未读通知数量
     * @param userId 用户ID
     * @return 未读通知数量
     */
    int getUnreadCount(Long userId);

    /**
     * 标记通知为已读
     * @param notificationId 通知ID
     * @param userId 用户ID
     */
    void markAsRead(Long notificationId, Long userId);

    /**
     * 将所有通知标记为已读
     * @param userId 用户ID
     */
    void markAllAsRead(Long userId);

    /**
     * 删除通知
     * @param notificationId 通知ID
     * @param userId 用户ID
     */
    void deleteNotification(Long notificationId, Long userId);
}
