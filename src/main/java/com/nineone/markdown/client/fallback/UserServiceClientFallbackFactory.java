package com.nineone.markdown.client.fallback;

import com.nineone.common.result.Result;
import com.nineone.markdown.client.UserServiceClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 用户服务 Feign 客户端降级工厂
 * 当 markdown-user 服务不可用时返回友好的错误信息
 */
@Slf4j
@Component
public class UserServiceClientFallbackFactory implements FallbackFactory<UserServiceClient> {

    private static final String ERROR_MSG = "用户服务暂时不可用，请稍后重试";

    @Override
    public UserServiceClient create(Throwable cause) {
        log.error("用户服务调用失败，触发降级: {}", cause.getMessage());
        return new UserServiceClient() {

            @Override
            public Result<Map<String, Object>> getUserById(Long userId) {
                return Result.error(ERROR_MSG);
            }

            @Override
            public Result<Boolean> checkEmail(String email) {
                return Result.error(ERROR_MSG);
            }
        };
    }
}
