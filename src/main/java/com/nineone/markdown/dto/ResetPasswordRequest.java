package com.nineone.markdown.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordRequest {
    @NotBlank(message = "重置令牌不能为空")
    private String token;

    @NotBlank(message = "用户ID不能为空")
    private String userId;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 100, message = "密码长度需在6-100之间")
    private String newPassword;
}
