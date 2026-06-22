package com.nineone.markdown.util;

import com.nineone.markdown.entity.User;
import com.nineone.markdown.enums.UserRoleEnum;
import com.nineone.markdown.exception.AuthenticationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 用户上下文持有者（基于 ThreadLocal 的 Request 级别缓存）
 * <p>
 * 双数据源设计：
 * 1. 本地 ThreadLocal&lt;User&gt; — JwtAuthenticationFilter 解析 JWT 后查库填充
 * 2. 回退到 com.nineone.common.context.UserContextHolder — UserContextFilter 从网关 X-User-* 请求头填充
 * <p>
 * 使用方式：
 * 1. 在 JwtAuthenticationFilter 中认证成功后调用 {@link #setCurrentUser(User)}
 * 2. 在 Service 层中调用 {@link #getCurrentUser()} 获取缓存用户
 * 3. 在请求结束时调用 {@link #clear()} 防止内存泄漏
 */
@Slf4j
public class UserContextHolder {

    private static final ThreadLocal<User> USER_HOLDER = new ThreadLocal<>();

    public static void setCurrentUser(User user) {
        if (user != null) {
            log.debug("UserContextHolder 缓存用户: id={}, username={}", user.getId(), user.getUsername());
        }
        USER_HOLDER.set(user);
    }

    public static User getCurrentUser() {
        return USER_HOLDER.get();
    }

    public static Long getUserId() {
        User user = USER_HOLDER.get();
        if (user != null) return user.getId();
        return com.nineone.common.context.UserContextHolder.getUserId();
    }

    public static Long getCurrentUserId() {
        return getUserId();
    }

    public static String getCurrentUserNickname() {
        User user = USER_HOLDER.get();
        if (user != null) return user.getNickname();
        return com.nineone.common.context.UserContextHolder.getNickname();
    }

    public static boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(UserRoleEnum.ADMIN.getRoleName()::equals);
    }

    public static Long requireUserId() {
        Long userId = getUserId();
        if (userId == null) {
            throw new AuthenticationException("用户未认证", "UNAUTHENTICATED");
        }
        return userId;
    }

    public static boolean isLoggedIn() {
        return getUserId() != null;
    }

    public static void clear() {
        USER_HOLDER.remove();
        com.nineone.common.context.UserContextHolder.clear();
    }
}
