package com.nineone.markdown.security;

import com.nineone.markdown.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 自定义 UserDetails 实现
 */
@Builder
@AllArgsConstructor
public class CustomUserDetails implements UserDetails {

    private final User user;
    private final Long id;
    private final String username;
    private final String nickname;
    private final String password;
    private final List<GrantedAuthority> authorities;

    // 完整构造函数（基于User实体）
    public CustomUserDetails(User user) {
        this.user = user;
        this.id = user != null ? user.getId() : null;
        this.username = user != null ? user.getUsername() : null;
        this.nickname = user != null ? user.getNickname() : null;
        this.password = user != null ? user.getPassword() : null;
        this.authorities = user != null ? 
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")) : 
                Collections.emptyList();
    }

    // Builder构造函数由 Lombok @Builder 自动生成
    // 参数顺序与字段声明顺序一致：user, id, username, nickname, password, authorities

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (authorities != null && !authorities.isEmpty()) {
            return authorities;
        }
        // 默认角色
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() {
        if (password != null) {
            return password;
        }
        return user != null ? user.getPassword() : null;
    }

    @Override
    public String getUsername() {
        if (username != null) {
            return username;
        }
        return user != null ? user.getUsername() : null;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    public Long getId() {
        if (id != null) {
            return id;
        }
        return user != null ? user.getId() : null;
    }

    public String getNickname() {
        // 优先使用独立的 nickname 字段（通过 Builder 从 JWT payload 解析时）
        if (nickname != null) {
            return nickname;
        }
        // 回退到从 User 实体获取（通过 User 构造函数构造时）
        if (user != null) {
            return user.getNickname();
        }
        // 最终回退
        return username != null ? username : "未知用户";
    }

    public User getUser() {
        if (user != null) {
            return user;
        }
        return null;
    }
}
