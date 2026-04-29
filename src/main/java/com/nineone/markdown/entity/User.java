package com.nineone.markdown.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户表实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_user")
public class User {

    /**
     * 主键 ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户名（用于登录）
     */
    @TableField(value = "username")
    private String username;

    /**
     * 密码（BCrypt 加密后的哈希值）
     */
    @TableField(value = "password")
    private String password;

    /**
     * 昵称
     */
    @TableField(value = "nickname")
    private String nickname;

    /**
     * 邮箱
     */
    @TableField(value = "email")
    private String email;

    /**
     * 邮箱验证状态：0-未验证，1-已验证
     */
    @TableField(value = "email_verified")
    private Integer emailVerified;

    /**
     * 邮箱验证令牌
     */
    @TableField(value = "verification_token")
    private String verificationToken;

    /**
     * 验证令牌过期时间
     */
    @TableField(value = "verification_token_expiry")
    private LocalDateTime verificationTokenExpiry;

    /**
     * 邮箱验证时间
     */
    @TableField(value = "email_verified_at")
    private LocalDateTime emailVerifiedAt;

    /**
     * 注册时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}