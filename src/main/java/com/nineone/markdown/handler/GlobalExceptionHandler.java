    package com.nineone.markdown.handler;

import com.nineone.markdown.common.Result;
import com.nineone.markdown.exception.AuthenticationException;
import com.nineone.markdown.exception.PermissionDeniedException;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理器
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 处理 JWT 签名异常
     */
    @ExceptionHandler(SignatureException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Map<String, String>> handleSignatureException(SignatureException e) {
        log.warn("JWT 签名验证失败: {}", e.getMessage());
        Map<String, String> error = new HashMap<>();
        error.put("message", "身份验证失败，请重新登录");
        error.put("code", "INVALID_TOKEN");
        return Result.failure("身份验证失败", error);
    }

    /**
     * 处理 JWT 过期异常
     */
    @ExceptionHandler(ExpiredJwtException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Map<String, String>> handleExpiredJwtException(ExpiredJwtException e) {
        log.warn("JWT 已过期: {}", e.getMessage());
        Map<String, String> error = new HashMap<>();
        error.put("message", "登录已过期，请重新登录");
        error.put("code", "TOKEN_EXPIRED");
        return Result.failure("登录已过期", error);
    }

    /**
     * 处理自定义认证异常
     */
    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Map<String, String>> handleAuthenticationException(AuthenticationException e) {
        log.warn("认证失败: {}", e.getMessage());
        Map<String, String> error = new HashMap<>();
        error.put("message", e.getMessage());
        error.put("code", e.getCode());
        return Result.failure("认证失败", error);
    }

    /**
     * 处理权限拒绝异常
     */
    @ExceptionHandler(PermissionDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Map<String, String>> handlePermissionDeniedException(PermissionDeniedException e) {
        log.warn("权限拒绝: {}", e.getMessage());
        Map<String, String> error = new HashMap<>();
        error.put("message", e.getMessage());
        error.put("code", e.getCode());
        return Result.failure("权限拒绝", error);
    }

    /**
     * 处理参数验证异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException e) {
        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        log.warn("参数验证失败: {}", errors);
        return Result.failure("参数验证失败", errors);
    }

    /**
     * 处理绑定异常
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Map<String, String>> handleBindException(BindException e) {
        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        log.warn("绑定异常: {}", errors);
        return Result.failure("参数绑定失败", errors);
    }

    /**
     * 处理HTTP消息不可读异常（JSON解析错误）
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Map<String, String>> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        log.warn("JSON解析错误: {}", e.getMessage());
        Map<String, String> error = new HashMap<>();
        error.put("message", "文章内容不能为空");
        error.put("code", "JSON_PARSE_ERROR");
        return Result.<Map<String, String>>builder()
                .code(400)
                .message("文章内容不能为空")
                .data(error)
                .build();
    }

    /**
     * 处理所有其他异常
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Map<String, String>> handleAllExceptions(Exception e) {
        log.error("系统内部错误: {}", e.getMessage(), e);
        Map<String, String> error = new HashMap<>();
        error.put("message", "系统内部错误，请稍后重试");
        error.put("code", "INTERNAL_ERROR");
        return Result.failure("系统内部错误", error);
    }
}