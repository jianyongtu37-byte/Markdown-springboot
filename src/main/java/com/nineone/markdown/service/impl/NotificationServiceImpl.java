package com.nineone.markdown.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nineone.markdown.entity.Notification;
import com.nineone.markdown.exception.BizException;
import com.nineone.markdown.exception.PermissionDeniedException;
import com.nineone.markdown.mapper.NotificationMapper;
import com.nineone.markdown.service.NotificationService;
import com.nineone.markdown.service.SseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 通知服务实现类
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;
    private final SseService sseService;
    private final ObjectMapper objectMapper;

    @Override
    @Async("taskExecutor")
    public void createNotification(Long userId, String type, String title, String content,
                                   Long relatedArticleId, Long relatedUserId, String relatedUserName) {
        try {
            Notification notification = Notification.builder()
                    .userId(userId)
                    .type(type)
                    .title(title)
                    .content(content)
                    .relatedArticleId(relatedArticleId)
                    .relatedUserId(relatedUserId)
                    .relatedUserName(relatedUserName)
                    .isRead(0)
                    .build();

            notificationMapper.insert(notification);
            log.debug("通知创建成功, 用户ID: {}, 类型: {}, 标题: {}", userId, type, title);

            // 创建成功后，通过 SSE 实时推送给接收用户
            try {
                Map<String, Object> message = new HashMap<>();
                message.put("id", notification.getId());
                message.put("type", notification.getType());
                message.put("title", notification.getTitle());
                message.put("content", notification.getContent());
                message.put("relatedArticleId", notification.getRelatedArticleId());
                message.put("relatedUserName", notification.getRelatedUserName());
                message.put("createTime", notification.getCreateTime());
                message.put("isRead", 0);

                String jsonMessage = objectMapper.writeValueAsString(message);
                sseService.sendNotification(userId, jsonMessage);
            } catch (Exception e) {
                log.warn("SSE实时推送通知失败, 用户ID: {}", userId, e);
            }
        } catch (Exception e) {
            log.error("通知创建失败, 用户ID: {}, 类型: {}", userId, type, e);
        }
    }

    @Override
    public List<Notification> getUnreadNotifications(Long userId) {
        return notificationMapper.findUnreadByUserId(userId);
    }

    @Override
    public List<Notification> getAllNotifications(Long userId) {
        return notificationMapper.findByUserId(userId);
    }

    @Override
    public int getUnreadCount(Long userId) {
        return notificationMapper.countUnreadByUserId(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAsRead(Long notificationId, Long userId) {
        Notification notification = notificationMapper.selectById(notificationId);
        if (notification == null) {
            throw new BizException("通知不存在");
        }
        if (!notification.getUserId().equals(userId)) {
            throw new PermissionDeniedException("您没有权限操作此通知");
        }

        notification.setIsRead(1);
        notificationMapper.updateById(notification);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAllAsRead(Long userId) {
        notificationMapper.markAllAsRead(userId);
        log.info("用户{}的所有通知已标记为已读", userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteNotification(Long notificationId, Long userId) {
        Notification notification = notificationMapper.selectById(notificationId);
        if (notification == null) {
            throw new BizException("通知不存在");
        }
        if (!notification.getUserId().equals(userId)) {
            throw new PermissionDeniedException("您没有权限删除此通知");
        }

        notificationMapper.deleteById(notificationId);
    }
}
