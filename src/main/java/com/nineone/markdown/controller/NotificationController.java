package com.nineone.markdown.controller;

import com.nineone.markdown.common.Result;
import com.nineone.markdown.entity.Notification;
import com.nineone.markdown.exception.AuthenticationException;
import com.nineone.markdown.security.CustomUserDetails;
import com.nineone.markdown.service.NotificationService;
import com.nineone.markdown.service.SseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * 通知控制器
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final SseService sseService;

    /**
     * 获取当前登录用户的ID
     */
    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthenticationException("用户未认证", "UNAUTHENTICATED");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof CustomUserDetails) {
            return ((CustomUserDetails) principal).getId();
        }
        throw new AuthenticationException("用户未登录或登录已过期", "TOKEN_EXPIRED");
    }

    /**
     * SSE 订阅接口 - 建立实时通知连接
     * 前端通过 EventSource 连接此接口，即可实时接收新通知推送
     * <p>
     * 注意：此方法在返回 SseEmitter 之前会先完成认证校验，
     * 避免因认证失败导致 "Response is already committed" 错误。
     */
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe() {
        // 先完成认证校验，确保用户已登录
        // 如果认证失败会抛出 AuthenticationException，此时响应尚未提交
        Long userId = getCurrentUserId();
        return sseService.connect(userId);
    }

    /**
     * 获取未读通知列表
     */
    @GetMapping("/unread")
    public Result<List<Notification>> getUnreadNotifications() {
        Long userId = getCurrentUserId();
        List<Notification> notifications = notificationService.getUnreadNotifications(userId);
        return Result.success(notifications);
    }

    /**
     * 获取所有通知列表（含历史已读通知）
     */
    @GetMapping
    public Result<List<Notification>> getAllNotifications() {
        Long userId = getCurrentUserId();
        List<Notification> notifications = notificationService.getAllNotifications(userId);
        return Result.success(notifications);
    }

    /**
     * 获取未读通知数量
     */
    @GetMapping("/unread/count")
    public Result<Map<String, Object>> getUnreadCount() {
        Long userId = getCurrentUserId();
        int count = notificationService.getUnreadCount(userId);
        return Result.success(Map.of("count", count));
    }

    /**
     * 标记通知为已读
     */
    @PutMapping("/{notificationId}/read")
    public Result<Void> markAsRead(@PathVariable Long notificationId) {
        Long userId = getCurrentUserId();
        notificationService.markAsRead(notificationId, userId);
        return Result.success("已标记为已读", null);
    }

    /**
     * 将所有通知标记为已读
     */
    @PutMapping("/read-all")
    public Result<Void> markAllAsRead() {
        Long userId = getCurrentUserId();
        notificationService.markAllAsRead(userId);
        return Result.success("所有通知已标记为已读", null);
    }

    /**
     * 删除通知
     */
    @DeleteMapping("/{notificationId}")
    public Result<Void> deleteNotification(@PathVariable Long notificationId) {
        Long userId = getCurrentUserId();
        notificationService.deleteNotification(notificationId, userId);
        return Result.success("通知已删除", null);
    }
}
