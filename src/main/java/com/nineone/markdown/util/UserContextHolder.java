package com.nineone.markdown.util;

import com.nineone.markdown.entity.User;
import com.nineone.markdown.enums.UserRoleEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 用户上下文持有者（基于 ThreadLocal 的 Request 级别缓存）
 * <p>
 * 用于在同一个 HTTP 请求的生命周期内缓存用户信息，
 * 避免在过滤器、拦截器、Service 层中重复查询数据库。
 * <p>
 * 使用方式：
 * 1. 在 JwtAuthenticationFilter 中认证成功后调用 {@link #setCurrentUser(User)}
 * 2. 在 Service 层中调用 {@link #getCurrentUser()} 获取缓存用户
 * 3. 在请求结束时（如 OncePerRequestFilter 的 finally 块）调用 {@link #clear()} 防止内存泄漏
 */
@Slf4j
public class UserContextHolder {

    private static final ThreadLocal<User> USER_HOLDER = new ThreadLocal<>();

    /**
     * 设置当前请求的用户信息
     */
    public static void setCurrentUser(User user) {
        if (user != null) {
            log.debug("UserContextHolder 缓存用户: id={}, username={}", user.getId(), user.getUsername());
        }
        USER_HOLDER.set(user);
    }

    /**
     * 获取当前请求的用户信息
     *
     * @return 缓存的 User 对象，可能为 null
     */
    public static User getCurrentUser() {
        return USER_HOLDER.get();
    }

    /**
     * 获取当前用户 ID（兼容旧版调用方使用的 getUserId 命名）
     *
     * @return 用户 ID，如果未登录返回 null
     */
    public static Long getUserId() {
        User user = USER_HOLDER.get();
        return user != null ? user.getId() : null;
    }

    /**
     * 获取当前用户 ID
     *
     * @return 用户 ID，如果未登录返回 null
     */
    public static Long getCurrentUserId() {
        return getUserId();
    }

    /**
     * 获取当前用户昵称
     *
     * @return 用户昵称，如果未登录返回 null
     */
    public static String getCurrentUserNickname() {
        User user = USER_HOLDER.get();
        return user != null ? user.getNickname() : null;
    }

    /**
     * 判断当前用户是否是管理员
     * 从 SecurityContext 的 authorities 中判断是否包含 ROLE_ADMIN
     *
     * @return true 表示当前用户是管理员
     */
    public static boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(UserRoleEnum.ADMIN.getRoleName()::equals);
    }

    /**
     * 判断当前是否已登录
     */
    public static boolean isLoggedIn() {
        return USER_HOLDER.get() != null;
    }

    /**
     * 清除当前请求的用户信息（防止 ThreadLocal 内存泄漏）
     * 必须在请求结束时调用，如在 Filter 的 finally 块中
     */
    public static void clear() {
        USER_HOLDER.remove();
    }
}
