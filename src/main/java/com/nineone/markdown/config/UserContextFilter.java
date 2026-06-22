package com.nineone.markdown.config;

import com.nineone.common.context.UserContextHolder;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class UserContextFilter implements Filter {

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;

        String userId = request.getHeader("X-User-Id");
        String username = request.getHeader("X-User-Name");
        String email = request.getHeader("X-User-Email");
        String role = request.getHeader("X-User-Role");
        String authorities = request.getHeader("X-User-Authorities");
        String nickname = request.getHeader("X-User-Nickname");

        // 防止字符串 "null" 导致解析异常
        if (userId != null && !"null".equalsIgnoreCase(userId) && !userId.isBlank()) {
            try {
                UserContextHolder.UserInfo userInfo = new UserContextHolder.UserInfo(
                        Long.parseLong(userId),
                        nickname,
                        username,
                        authorities
                );
                UserContextHolder.set(userInfo);
            } catch (NumberFormatException e) {
                log.warn("无效的用户ID标头: {}", userId);
            }
            if (email != null && !"null".equalsIgnoreCase(email) && !email.isBlank()) {
                UserContextHolder.setEmail(email);
            }
            if (role != null && !"null".equalsIgnoreCase(role) && !role.isBlank()) {
                try {
                    UserContextHolder.setRole(Integer.parseInt(role));
                } catch (NumberFormatException e) {
                    log.warn("无效的角色标头: {}", role);
                }
            }
            log.debug("用户上下文已设置: userId={}, username={}, nickname={}", userId, username, nickname);
        }

        try {
            filterChain.doFilter(servletRequest, servletResponse);
        } finally {
            UserContextHolder.clear();
        }
    }
}
