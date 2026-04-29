package com.nineone.markdown.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一响应结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    /**
     * 响应码
     */
    private Integer code;

    /**
     * 响应消息
     */
    private String message;

    /**
     * 响应数据
     */
    private T data;

    /**
     * 时间戳
     */
    private Long timestamp = System.currentTimeMillis();

    /**
     * 成功响应（无数据）
     */
    public static <T> Result<T> success() {
        return Result.<T>builder()
                .code(200)
                .message("success")
                .build();
    }

    /**
     * 成功响应（有数据）
     */
    public static <T> Result<T> success(T data) {
        return Result.<T>builder()
                .code(200)
                .message("success")
                .data(data)
                .build();
    }

    /**
     * 成功响应（自定义消息）
     */
    public static <T> Result<T> success(String message, T data) {
        return Result.<T>builder()
                .code(200)
                .message(message)
                .data(data)
                .build();
    }

    /**
     * 失败响应
     */
    public static <T> Result<T> error(Integer code, String message) {
        return Result.<T>builder()
                .code(code)
                .message(message)
                .build();
    }

    /**
     * 失败响应（默认 500 错误）
     */
    public static <T> Result<T> error(String message) {
        return error(500, message);
    }

    /**
     * 参数错误响应
     */
    public static <T> Result<T> badRequest(String message) {
        return error(400, message);
    }

    /**
     * 未授权响应
     */
    public static <T> Result<T> unauthorized(String message) {
        return error(401, message);
    }

    /**
     * 禁止访问响应
     */
    public static <T> Result<T> forbidden(String message) {
        return error(403, message);
    }

    /**
     * 资源不存在响应
     */
    public static <T> Result<T> notFound(String message) {
        return error(404, message);
    }

    /**
     * 失败响应（通用）
     */
    public static <T> Result<T> failure(String message) {
        return error(500, message);
    }

    /**
     * 失败响应（包含数据）
     */
    public static <T> Result<T> failure(String message, T data) {
        return Result.<T>builder()
                .code(500)
                .message(message)
                .data(data)
                .build();
    }
}