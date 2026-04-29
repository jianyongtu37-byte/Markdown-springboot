package com.nineone.markdown.exception;

/**
 * 权限拒绝异常
 * 用于处理用户没有权限执行操作的情况，返回 403 状态码
 */
public class PermissionDeniedException extends RuntimeException {
    
    private final String code;
    
    public PermissionDeniedException(String message) {
        super(message);
        this.code = "PERMISSION_DENIED";
    }
    
    public PermissionDeniedException(String message, String code) {
        super(message);
        this.code = code;
    }
    
    public PermissionDeniedException(String message, Throwable cause) {
        super(message, cause);
        this.code = "PERMISSION_DENIED";
    }
    
    public PermissionDeniedException(String message, String code, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
    
    public String getCode() {
        return code;
    }
}