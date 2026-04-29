package com.nineone.markdown.exception;

/**
 * 自定义认证异常
 * 用于处理用户认证失败、登录过期等情况
 */
public class AuthenticationException extends RuntimeException {
    
    private final String code;
    
    public AuthenticationException(String message) {
        super(message);
        this.code = "AUTHENTICATION_FAILED";
    }
    
    public AuthenticationException(String message, String code) {
        super(message);
        this.code = code;
    }
    
    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
        this.code = "AUTHENTICATION_FAILED";
    }
    
    public AuthenticationException(String message, String code, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
    
    public String getCode() {
        return code;
    }
}