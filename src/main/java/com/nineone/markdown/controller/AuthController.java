package com.nineone.markdown.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.nineone.common.result.Result;
import com.nineone.markdown.dto.ForgotPasswordRequest;
import com.nineone.markdown.dto.LoginRequest;
import com.nineone.markdown.dto.RegisterRequest;
import com.nineone.markdown.dto.ResetPasswordRequest;
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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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
    private final HttpServletRequest httpServletRequest;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final int LOGIN_MAX_ATTEMPTS = 5;
    private static final Duration LOGIN_BLOCK_DURATION = Duration.ofMinutes(15);

    private static final int REGISTER_MAX_ATTEMPTS = 3;
    private static final Duration REGISTER_BLOCK_DURATION = Duration.ofHours(1);

    private static final int FORGOT_PASSWORD_MAX_ATTEMPTS = 3;
    private static final Duration FORGOT_PASSWORD_BLOCK_DURATION = Duration.ofMinutes(30);

    @Value("${app.url:http://localhost:8080}")
    private String appUrl;

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public Result<String> login(@Valid @RequestBody LoginRequest loginRequest) {
        String clientIp = getClientIp();
        String rateLimitKey = "login:attempts:" + clientIp;

        // 检查是否已被限流
        Object attemptsObj = redisTemplate.opsForValue().get(rateLimitKey);
        int attempts = attemptsObj instanceof Number ? ((Number) attemptsObj).intValue() : 0;
        if (attempts >= LOGIN_MAX_ATTEMPTS) {
            log.warn("登录限流触发，IP: {}, 尝试次数: {}", clientIp, attempts);
            return Result.failure("登录尝试过于频繁，请15分钟后再试");
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword())
            );
            // 登录成功，清除失败计数
            redisTemplate.delete(rateLimitKey);
        
            SecurityContextHolder.getContext().setAuthentication(authentication);
            Object principal = authentication.getPrincipal();

            if (principal instanceof CustomUserDetails) {
                CustomUserDetails userDetails = (CustomUserDetails) principal;
                String jwt = jwtUtil.generateToken(userDetails);
                return Result.success("登录成功", jwt);
            } else {
                log.error("登录成功后 Principal 类型异常: {}", principal.getClass().getName());
                throw new AuthenticationException("登录状态异常，请重试", "LOGIN_STATE_ERROR");
            }
        } catch (org.springframework.security.core.AuthenticationException e) {
            // 登录失败，递增失败计数
            incrementLoginAttempts(rateLimitKey);
            log.warn("登录失败，IP: {}, 用户名: {}, 当前尝试次数递增", clientIp, loginRequest.getUsername());
            throw new AuthenticationException("用户名或密码错误", "LOGIN_FAILED");
        }
    }

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public Result<Long> register(@Valid @RequestBody RegisterRequest registerRequest) {
        // 注册限速
        String clientIp = getClientIp();
        String registerLimitKey = "register:attempts:" + clientIp;
        if (isRateLimited(registerLimitKey, REGISTER_MAX_ATTEMPTS)) {
            log.warn("注册限流触发，IP: {}", clientIp);
            return Result.failure("注册请求过于频繁，请1小时后再试");
        }

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
    public Result<String> verifyEmail(@RequestParam("userId") Long userId, @RequestParam("token") String token) {
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
     * 忘记密码 - 发送密码重置邮件
     */
    @PostMapping("/forgot-password")
    public Result<String> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        // 忘记密码限速（按IP）
        String clientIp = getClientIp();
        String forgotLimitKey = "forgot-password:attempts:" + clientIp;
        if (isRateLimited(forgotLimitKey, FORGOT_PASSWORD_MAX_ATTEMPTS)) {
            log.warn("忘记密码限流触发，IP: {}", clientIp);
            return Result.failure("请求过于频繁，请30分钟后再试");
        }

        String email = request.getEmail();
        User user = userMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<User>().eq("email", email));
        if (user == null) {
            // 不暴露用户是否存在，统一返回成功提示
            return Result.success("如果该邮箱已注册，重置密码邮件已发送");
        }

        // 生成密码重置令牌
        String resetToken = UUID.randomUUID().toString();
        LocalDateTime expiryTime = LocalDateTime.now().plusMinutes(15);

        // 存储令牌到用户表（复用 verification_token 字段，前缀 pwreset: 区分）
        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<User> updateWrapper =
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
        updateWrapper.eq("id", user.getId())
                .set("verification_token", "pwreset:" + resetToken)
                .set("verification_token_expiry", expiryTime);
        userMapper.update(null, updateWrapper);

        // 构建重置链接
        String resetLink = appUrl + "/reset-password?token=" + resetToken + "&userId=" + user.getId();

        // 异步发送密码重置邮件
        try {
            emailService.sendPasswordResetEmail(email, user.getUsername(), resetLink);
            log.info("密码重置邮件已发送至 {}", email);
        } catch (Exception e) {
            log.error("密码重置邮件发送失败: {}", email, e);
        }

        return Result.success("如果该邮箱已注册，重置密码邮件已发送");
    }

    /**
     * 验证密码重置令牌是否有效
     */
    @GetMapping("/reset-password/validate")
    public Result<Map<String, Object>> validateResetToken(
            @RequestParam("token") String token,
            @RequestParam("userId") Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || user.getVerificationToken() == null) {
            return Result.success(Map.of("valid", false, "message", "令牌无效"));
        }

        String expectedToken = "pwreset:" + token;
        if (!expectedToken.equals(user.getVerificationToken())) {
            return Result.success(Map.of("valid", false, "message", "令牌无效"));
        }

        if (user.getVerificationTokenExpiry() == null
                || user.getVerificationTokenExpiry().isBefore(LocalDateTime.now())) {
            return Result.success(Map.of("valid", false, "message", "令牌已过期"));
        }

        return Result.success(Map.of("valid", true));
    }

    /**
     * 通过令牌重置密码
     */
    @PostMapping("/reset-password")
    public Result<String> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        Long userId;
        try {
            userId = Long.parseLong(request.getUserId());
        } catch (NumberFormatException e) {
            return Result.failure("用户ID格式无效");
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.failure("重置令牌无效或已过期");
        }

        // 验证令牌
        String expectedToken = "pwreset:" + request.getToken();
        if (user.getVerificationToken() == null || !expectedToken.equals(user.getVerificationToken())) {
            return Result.failure("重置令牌无效或已过期");
        }

        if (user.getVerificationTokenExpiry() == null
                || user.getVerificationTokenExpiry().isBefore(LocalDateTime.now())) {
            return Result.failure("重置令牌已过期，请重新发起密码重置");
        }

        // 更新密码并清除令牌
        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<User> updateWrapper =
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
        updateWrapper.eq("id", userId)
                .set("password", passwordEncoder.encode(request.getNewPassword()))
                .set("verification_token", null)
                .set("verification_token_expiry", null);
        userMapper.update(null, updateWrapper);

        log.info("用户 {} 密码重置成功", userId);
        return Result.success("密码重置成功，请使用新密码登录", null);
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

    /**
     * 递增登录失败计数，并设置过期时间
     */
    private void incrementLoginAttempts(String key) {
        Object current = redisTemplate.opsForValue().get(key);
        int count = current instanceof Number ? ((Number) current).intValue() : 0;
        redisTemplate.opsForValue().set(key, count + 1, LOGIN_BLOCK_DURATION);
    }

    /**
     * 检查是否触发限流
     */
    private boolean isRateLimited(String key, int maxAttempts) {
        Object attemptsObj = redisTemplate.opsForValue().get(key);
        int attempts = attemptsObj instanceof Number ? ((Number) attemptsObj).intValue() : 0;
        if (attempts >= maxAttempts) {
            return true;
        }
        redisTemplate.opsForValue().set(key, attempts + 1, REGISTER_BLOCK_DURATION);
        return false;
    }

    /**
     * 获取客户端真实IP（网关已将真实IP写入 X-Real-IP，不信任客户端伪造的 X-Forwarded-For）
     */
    private String getClientIp() {
        String xRealIp = httpServletRequest.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }
        return httpServletRequest.getRemoteAddr();
    }
}