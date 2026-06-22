package com.nineone.markdown.client;

import com.nineone.common.result.Result;
import com.nineone.markdown.client.fallback.UserServiceClientFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * 用户服务 Feign 客户端
 */
@FeignClient(name = "markdown-user", path = "/api", fallbackFactory = UserServiceClientFallbackFactory.class)
public interface UserServiceClient {

    /**
     * 获取用户信息
     */
    @GetMapping("/users/{userId}")
    Result<Map<String, Object>> getUserById(@PathVariable("userId") Long userId);

    /**
     * 检查邮箱是否存在
     */
    @GetMapping("/auth/check-email")
    Result<Boolean> checkEmail(@RequestParam("email") String email);
}
