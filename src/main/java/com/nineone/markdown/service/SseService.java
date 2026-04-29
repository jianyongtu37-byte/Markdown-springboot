package com.nineone.markdown.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE 连接管理服务
 * 负责维护每个在线用户的长连接，并提供实时推送通知的能力
 */
@Service
@Slf4j
public class SseService {

    /**
     * 存储所有在线用户的 SseEmitter，Key 是 userId
     */
    private final Map<Long, SseEmitter> emitterMap = new ConcurrentHashMap<>();

    /**
     * 建立 SSE 连接
     *
     * @param userId 用户ID
     * @return SseEmitter
     */
    public SseEmitter connect(Long userId) {
        // 如果该用户已有连接，先关闭旧的
        SseEmitter existing = emitterMap.remove(userId);
        if (existing != null) {
            existing.complete();
        }

        // 设置超时时间，0 表示永不超时
        SseEmitter emitter = new SseEmitter(0L);

        // 注册回调：连接完成、超时、出错时自动清理
        emitter.onCompletion(() -> {
            emitterMap.remove(userId);
            log.debug("SSE连接完成，已移除用户: {}", userId);
        });
        emitter.onTimeout(() -> {
            emitterMap.remove(userId);
            log.debug("SSE连接超时，已移除用户: {}", userId);
        });
        emitter.onError(e -> {
            emitterMap.remove(userId);
            log.debug("SSE连接异常，已移除用户: {}, 错误: {}", userId, e.getMessage());
        });

        emitterMap.put(userId, emitter);
        log.debug("用户 {} 建立SSE连接，当前在线用户数: {}", userId, emitterMap.size());

        // 发送初始连接成功事件
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("SSE连接建立成功"));
        } catch (IOException e) {
            log.error("用户 {} 发送初始连接事件失败", userId, e);
            emitterMap.remove(userId);
        }

        return emitter;
    }

    /**
     * 发送通知给指定用户
     *
     * @param userId  接收通知的用户ID
     * @param message 通知内容（JSON字符串）
     */
    public void sendNotification(Long userId, String message) {
        SseEmitter emitter = emitterMap.get(userId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name("new_notification")
                        .data(message));
                log.debug("实时通知推送成功, 用户ID: {}", userId);
            } catch (IOException e) {
                // 发送失败说明连接已断开，移除之
                emitterMap.remove(userId);
                log.warn("实时通知推送失败，用户 {} 连接已断开", userId);
            }
        } else {
            log.debug("用户 {} 不在线，跳过实时推送", userId);
        }
    }

    /**
     * 断开用户的 SSE 连接
     *
     * @param userId 用户ID
     */
    public void disconnect(Long userId) {
        SseEmitter emitter = emitterMap.remove(userId);
        if (emitter != null) {
            emitter.complete();
            log.debug("用户 {} 的SSE连接已主动断开", userId);
        }
    }

    /**
     * 获取当前在线用户数
     *
     * @return 在线用户数
     */
    public int getOnlineCount() {
        return emitterMap.size();
    }
}
