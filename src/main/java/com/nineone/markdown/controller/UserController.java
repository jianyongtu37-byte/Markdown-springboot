package com.nineone.markdown.controller;

import com.nineone.markdown.common.Result;
import com.nineone.markdown.entity.User;
import com.nineone.markdown.exception.AuthenticationException;
import com.nineone.markdown.security.CustomUserDetails;
import com.nineone.markdown.service.UserService;
import com.nineone.markdown.util.UserContextHolder;
import com.nineone.markdown.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * 用户控制器
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Validated
public class UserController {

    private final UserService userService;

    /**
     * 获取当前用户信息
     * <p>
     * 🔥 优化：优先从 UserContextHolder（ThreadLocal 缓存）获取用户信息，零数据库查询
     * 回退策略：UserContextHolder -> CustomUserDetails.getUser() -> 数据库查询
     */
    @GetMapping("/me")
    public Result<User> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Result.unauthorized("用户未认证");
        }
        
        Object principal = authentication.getPrincipal();
        
        // 增加类型判断，避免 JWT 过期后出现 ClassCastException
        if (principal instanceof CustomUserDetails) {
            CustomUserDetails userDetails = (CustomUserDetails) principal;
            
            // ========== 🔥 优化：优先从 ThreadLocal 缓存获取 ==========
            // JwtAuthenticationFilter 中已将当前登录用户缓存到 UserContextHolder
            // 零数据库查询，直接从内存获取
            User user = UserContextHolder.getCurrentUser();
            
            // 回退策略 1：从 CustomUserDetails 获取
            if (user == null) {
                user = userDetails.getUser();
            }
            
            // 回退策略 2：从数据库查询（JWT 无数据库查询模式）
            if (user == null) {
                Long userId = userDetails.getId();
                if (userId != null) {
                    user = userService.getUserById(userId);
                }
            }
            
            // 必须加这一步判断！如果用户不存在（比如 Token 过期或账号被删），直接返回未登录错误
            if (user == null) {
                return Result.error(401, "用户信息已过期，请重新登录");
            }
            // 清除密码信息
            user.setPassword(null);
            return Result.success(user);
        } else {
            // 抛出自定义的认证失败异常，让全局异常处理器返回 401 状态码
            throw new AuthenticationException("用户未登录或登录已过期", "TOKEN_EXPIRED");
        }
    }

    /**
     * 获取用户详情
     */
    @GetMapping("/{id}")
    public Result<UserVO> getUser(@PathVariable Long id) {
        User user = userService.getUserById(id);
        if (user == null) {
            return Result.<UserVO>notFound("用户不存在");
        }
        
        UserVO userVO = UserVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .email(user.getEmail())
                .createTime(user.getCreateTime())
                .updateTime(user.getUpdateTime())
                .build();
        
        return Result.success(userVO);
    }

    /**
     * 更新当前用户信息
     * <p>
     * 🔥 优化：优先从 UserContextHolder（ThreadLocal 缓存）获取用户信息，零数据库查询
     */
    @PutMapping("/me")
    public Result<Void> updateCurrentUser(@Valid @RequestBody User updateRequest) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Result.unauthorized("用户未认证");
        }
        
        Object principal = authentication.getPrincipal();
        
        // 增加类型判断，避免 JWT 过期后出现 ClassCastException
        if (principal instanceof CustomUserDetails) {
            CustomUserDetails userDetails = (CustomUserDetails) principal;
            
            // ========== 🔥 优化：优先从 ThreadLocal 缓存获取 ==========
            User currentUser = UserContextHolder.getCurrentUser();
            if (currentUser == null) {
                currentUser = userDetails.getUser();
            }
            if (currentUser == null) {
                return Result.unauthorized("用户信息已过期，请重新登录");
            }
            Long currentUserId = currentUser.getId();
            
            // 确保只能更新自己的信息
            updateRequest.setId(currentUserId);
            // 不能通过此接口更新用户名和密码
            updateRequest.setUsername(null);
            updateRequest.setPassword(null);
            
            boolean success = userService.updateUser(updateRequest);
            if (!success) {
                return Result.<Void>builder().code(404).message("用户不存在，更新失败").build();
            }
            return Result.<Void>builder().code(200).message("用户信息更新成功").build();
        } else {
            // 抛出自定义的认证失败异常，让全局异常处理器返回 401 状态码
            throw new AuthenticationException("用户未登录或登录已过期", "TOKEN_EXPIRED");
        }
    }

    /**
     * 更新当前用户密码
     * <p>
     * 🔥 优化：优先从 UserContextHolder（ThreadLocal 缓存）获取用户信息，零数据库查询
     */
    @PutMapping("/me/password")
    public Result<Void> updatePassword(
            @RequestParam @NotBlank String oldPassword,
            @RequestParam @NotBlank String newPassword) {
        
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Result.unauthorized("用户未认证");
        }
        
        Object principal = authentication.getPrincipal();
        
        // 增加类型判断，避免 JWT 过期后出现 ClassCastException
        if (principal instanceof CustomUserDetails) {
            CustomUserDetails userDetails = (CustomUserDetails) principal;
            
            // ========== 🔥 优化：优先从 ThreadLocal 缓存获取 ==========
            User currentUser = UserContextHolder.getCurrentUser();
            if (currentUser == null) {
                currentUser = userDetails.getUser();
            }
            if (currentUser == null) {
                return Result.unauthorized("用户信息已过期，请重新登录");
            }
            Long currentUserId = currentUser.getId();
            
            boolean success = userService.updatePassword(currentUserId, oldPassword, newPassword);
            if (!success) {
                return Result.<Void>builder().code(400).message("原密码错误，更新失败").build();
            }
            return Result.<Void>builder().code(200).message("密码更新成功").build();
        } else {
            // 抛出自定义的认证失败异常，让全局异常处理器返回 401 状态码
            throw new AuthenticationException("用户未登录或登录已过期", "TOKEN_EXPIRED");
        }
    }

    /**
     * 获取所有用户列表（管理员功能）
     */
    @GetMapping
    public Result<List<UserVO>> getAllUsers() {
        List<UserVO> users = userService.getAllUsers();
        return Result.success(users);
    }

    /**
     * 搜索用户（管理员功能）
     */
    @GetMapping("/search")
    public Result<List<UserVO>> searchUsers(@RequestParam String keyword) {
        List<UserVO> users = userService.searchUsers(keyword);
        return Result.success(users);
    }

    /**
     * 获取用户统计信息（管理员功能）
     */
    @GetMapping("/stats")
    public Result<Long> getUserStats() {
        Long count = userService.getUserCount();
        return Result.success("获取用户统计成功", count);
    }

    /**
     * 重置用户密码（管理员功能）
     */
    @PostMapping("/{id}/reset-password")
    public Result<Void> resetPassword(@PathVariable Long id, @RequestParam @NotBlank String newPassword) {
        boolean success = userService.resetPassword(id, newPassword);
        if (!success) {
            return Result.<Void>builder().code(404).message("用户不存在，重置失败").build();
        }
        return Result.<Void>builder().code(200).message("密码重置成功").build();
    }
}