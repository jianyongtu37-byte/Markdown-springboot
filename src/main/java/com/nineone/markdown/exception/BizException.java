package com.nineone.markdown.exception;

/**
 * 业务异常类
 */
public class BizException extends RuntimeException {
    
    private String code;
    
    public BizException(String message) {
        super(message);
    }
    
    public BizException(String message, String code) {
        super(message);
        this.code = code;
    }
    
    public BizException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public BizException(String message, String code, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
    
    public String getCode() {
        return code;
    }
    
    public void setCode(String code) {
        this.code = code;
    }
}