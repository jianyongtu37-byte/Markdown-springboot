package com.nineone.markdown.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.nineone.markdown.common.Result;
import com.nineone.markdown.dto.LoginRequest;
import com.nineone.markdown.dto.RegisterRequest;
import com.nineone.markdown.entity.User;
import com.nineone.markdown.exception.AuthenticationException;
import com.nineone.markdown.mapper.UserMapper;
import com.nineone.markdown.security.CustomUserDetails;
import com.nineone.markdown.security.JwtUtil;
import com.nineone.markdown.service.EmailService;
import com.nineone.markdown.util.UserContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 认证控制器
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Validated
@Slf4j
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final EmailService emailService;

    @Value("${app.url:http://localhost:8080}")
    private String appUrl;

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public Result<String> login(@Valid @RequestBody LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword())
        );
        
        SecurityContextHolder.getContext().setAuthentication(authentication);
        Object principal = authentication.getPrincipal();
        
        // 增加类型判断，确保认证成功后的 Principal 是 CustomUserDetails
        if (principal instanceof CustomUserDetails) {
            CustomUserDetails userDetails = (CustomUserDetails) principal;
            String jwt = jwtUtil.generateToken(userDetails);
            return Result.success("登录成功", jwt);
        } else {
            // 理论上登录成功后 principal 应该是 CustomUserDetails，但为了安全起见还是加上检查
            log.error("登录成功后 Principal 类型异常: {}", principal.getClass().getName());
            throw new AuthenticationException("登录状态异常，请重试", "LOGIN_STATE_ERROR");
        }
    }

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public Result<Long> register(@Valid @RequestBody RegisterRequest registerRequest) {
        // 验证密码一致性
        if (!registerRequest.getPassword().equals(registerRequest.getConfirmPassword())) {
            return Result.failure("两次输入的密码不一致");
        }
        
        // 检查用户名是否已存在
        QueryWrapper<User> usernameQuery = new QueryWrapper<>();
        usernameQuery.eq("username", registerRequest.getUsername());
        if (userMapper.selectOne(usernameQuery) != null) {
            return Result.failure("用户名已存在");
        }
        
        // 检查邮箱是否已存在
        if (registerRequest.getEmail() != null && !registerRequest.getEmail().isEmpty()) {
            QueryWrapper<User> emailQuery = new QueryWrapper<>();
            emailQuery.eq("email", registerRequest.getEmail());
            if (userMapper.selectOne(emailQuery) != null) {
                return Result.failure("邮箱已被注册");
            }
        }
        
        // 创建新用户
        User user = User.builder()
                .username(registerRequest.getUsername())
                .password(passwordEncoder.encode(registerRequest.getPassword()))
                .nickname(registerRequest.getNickname() != null ? registerRequest.getNickname() : registerRequest.getUsername())
                .email(registerRequest.getEmail())
                .emailVerified(0) // 初始状态为未验证
                .build();
        
        userMapper.insert(user);
        
        // 异步发送欢迎邮件和验证邮件
        if (registerRequest.getEmail() != null && !registerRequest.getEmail().isEmpty()) {
            try {
                // 生成唯一的验证令牌
                String verificationToken = UUID.randomUUID().toString();
                LocalDateTime expiryTime = LocalDateTime.now().plusHours(24); // 24小时过期
                
                // 更新用户令牌和过期时间
                UpdateWrapper<User> updateWrapper = new UpdateWrapper<>();
                updateWrapper.eq("id", user.getId())
                        .set("verification_token", verificationToken)
                        .set("verification_token_expiry", expiryTime);
                userMapper.update(null, updateWrapper);
                
                // 生成验证链接
                String verificationLink = appUrl + "/api/auth/verify-email?userId=" + user.getId() + "&token=" + verificationToken;
                
                // 发送欢迎邮件
                emailService.sendWelcomeEmail(registerRequest.getEmail(), registerRequest.getUsername());
                
                // 发送验证邮件
                emailService.sendRegistrationVerificationEmail(registerRequest.getEmail(), 
                    registerRequest.getUsername(), verificationLink);
                
                log.info("注册成功，已为用户 {} ({}) 发送欢迎邮件和验证邮件，令牌: {}", 
                    registerRequest.getUsername(), registerRequest.getEmail(), verificationToken);
            } catch (Exception e) {
                log.error("发送注册邮件失败，用户ID: {}, 邮箱: {}", user.getId(), registerRequest.getEmail(), e);
                // 邮件发送失败不影响注册流程
            }
        }
        
        return Result.success("注册成功，欢迎邮件已发送", user.getId());
    }

    /**
     * 邮箱验证端点
     */
    @GetMapping("/verify-email")
    public Result<String> verifyEmail(@RequestParam Long userId, @RequestParam String token) {
        // 查找用户
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.failure("用户不存在");
        }
        
        // 检查是否已验证
        if (user.getEmailVerified() != null && user.getEmailVerified() == 1) {
            return Result.success("邮箱已验证");
        }
        
        // 检查令牌
        if (user.getVerificationToken() == null || !user.getVerificationToken().equals(token)) {
            return Result.failure("验证令牌无效");
        }
        
        // 检查令牌是否过期
        if (user.getVerificationTokenExpiry() == null || user.getVerificationTokenExpiry().isBefore(LocalDateTime.now())) {
            return Result.failure("验证令牌已过期");
        }
        
        // 更新用户状态
        UpdateWrapper<User> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", userId)
                .set("email_verified", 1)
                .set("email_verified_at", LocalDateTime.now())
                .set("verification_token", null) // 清空令牌
                .set("verification_token_expiry", null); // 清空过期时间
        
        int rows = userMapper.update(null, updateWrapper);
        
        if (rows > 0) {
            log.info("邮箱验证成功，用户ID: {}, 邮箱: {}", userId, user.getEmail());
            return Result.success("邮箱验证成功");
        } else {
            return Result.failure("邮箱验证失败");
        }
    }

    /**
     * 获取当前用户信息
     * <p>
     * 🔥 优化：优先从 UserContextHolder（ThreadLocal 缓存）获取用户信息，零数据库查询
     * 回退策略：UserContextHolder -> CustomUserDetails.getUser() -> 数据库查询
     */
    @PostMapping("/me")
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
                    user = userMapper.selectById(userId);
                }
            }
            
            if (user == null) {
                throw new AuthenticationException("用户信息不存在", "USER_NOT_FOUND");
            }
            // 清除密码信息
            user.setPassword(null);
            return Result.success(user);
        } else {
            // 抛出自定义的认证失败异常，让全局异常处理器返回 401 状态码
            throw new AuthenticationException("用户未登录或登录已过期", "TOKEN_EXPIRED");
        }
    }
}