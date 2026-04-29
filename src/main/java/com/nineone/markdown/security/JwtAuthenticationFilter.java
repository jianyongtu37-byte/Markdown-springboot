package com.nineone.markdown.security;

import com.nineone.markdown.entity.User;
import com.nineone.markdown.mapper.UserMapper;
import com.nineone.markdown.util.UserContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JWT 认证过滤器（优化版，减少数据库查询）
 * <p>
 * 优化点：
 * 1. 认证成功后，将 User 实体存入 {@link UserContextHolder}（ThreadLocal），
 *    后续 Service 层可直接从内存获取用户信息，避免重复查询数据库。
 * 2. 请求结束时在 finally 块中清理 ThreadLocal，防止内存泄漏。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;
    private final UserMapper userMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        final String authHeader = request.getHeader("Authorization");
        
        // 如果没有 Authorization 头，直接放行（无需清理 ThreadLocal，因为没设置过）
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        
        final String jwt = authHeader.substring(7);
        
        try {
            // 验证Token是否有效（不查询数据库）
            if (!jwtUtil.validateToken(jwt)) {
                log.debug("JWT Token已过期: {}", jwt);
                filterChain.doFilter(request, response);
                return;
            }
            
            final String username = jwtUtil.extractUsername(jwt);
            
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                // 尝试从Token中提取用户ID、昵称和权限信息（零数据库查询）
                Long userId = jwtUtil.extractUserId(jwt);
                String nickname = jwtUtil.extractNickname(jwt);
                List<String> authorityStrings = jwtUtil.extractAuthorities(jwt);
                
                    if (userId != null && authorityStrings != null && !authorityStrings.isEmpty()) {
                        // 从Token中提取权限信息，避免查询数据库
                        List<GrantedAuthority> authorities = authorityStrings.stream()
                                .map(SimpleGrantedAuthority::new)
                                .collect(Collectors.toList());
                        
                        // ========== 关键优化：只查一次数据库，后续全部走内存 ==========
                        // 这里查询一次 User 实体，同时完成两件事：
                        // 1. 存入 ThreadLocal（UserContextHolder），供 Service 层通过 getCurrentUser() 获取
                        // 2. 存入 CustomUserDetails，供 SecurityContext 中的 getCurrentUserId() 获取
                        // 后续所有拦截器、Service 层直接从内存获取，砍掉所有重复的 userMapper.selectById 调用
                        User user = userMapper.selectById(userId);
                        
                        // 创建CustomUserDetails（完整版，包含user实体、nickname、authorities）
                        // 所有高频信息（userId, nickname, authorities）均从JWT payload解析，
                        // 同时持有完整 User 实体，供 getUser() 方法返回
                        CustomUserDetails userDetails = CustomUserDetails.builder()
                                .user(user)  // 传入完整 User 实体，让 getUser() 有值返回
                                .id(userId)
                                .username(username)
                                .nickname(nickname)
                                .authorities(authorities)
                                .build();
                        
                        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                        
                        if (user != null) {
                            UserContextHolder.setCurrentUser(user);
                            log.debug("UserContextHolder 已缓存用户: id={}, username={}", userId, username);
                        }
                        
                        log.debug("JWT认证成功（零数据库查询）: 用户={}, ID={}, 昵称={}", username, userId, nickname);
                } else {
                    // 回退到原方案：查询数据库获取用户详细信息
                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                    
                    if (jwtUtil.validateToken(jwt, userDetails)) {
                        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                        
                        // 回退方案也存入 ThreadLocal
                        if (userDetails instanceof CustomUserDetails) {
                            CustomUserDetails customUserDetails = (CustomUserDetails) userDetails;
                            User user = customUserDetails.getUser();
                            if (user != null) {
                                UserContextHolder.setCurrentUser(user);
                            }
                        }
                        
                        log.debug("JWT认证成功（有数据库查询）: 用户={}", username);
                    }
                }
            }
        } catch (Exception e) {
            // 捕获 JWT 验证异常，记录日志但不中断请求处理
            // 这样公开接口（如注册、登录）不会因为无效的 JWT 而失败
            log.warn("JWT 验证失败: {}", e.getMessage());
            // 继续处理请求，不清除 SecurityContext
        }
        
        try {
            filterChain.doFilter(request, response);
        } finally {
            // ========== 关键清理：防止 ThreadLocal 内存泄漏 ==========
            // 确保每个请求结束时清理 ThreadLocal，避免线程复用导致的数据错乱
            UserContextHolder.clear();
        }
    }
}
