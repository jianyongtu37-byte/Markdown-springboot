package com.nineone.markdown.client.fallback;

import com.nineone.common.result.Result;
import com.nineone.markdown.client.AiServiceClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * AI 服务 Feign 客户端降级工厂
 * 当 markdown-ai 服务不可用时返回友好的错误信息
 */
@Slf4j
@Component
public class AiServiceClientFallbackFactory implements FallbackFactory<AiServiceClient> {

    private static final String ERROR_MSG = "AI 服务暂时不可用，请稍后重试";

    @Override
    public AiServiceClient create(Throwable cause) {
        log.error("AI 服务调用失败，触发降级: {}", cause.getMessage());
        return new AiServiceClient() {

            @Override
            public Result<String> generateSummary(Map<String, String> request) {
                return Result.error(ERROR_MSG);
            }

            @Override
            public Result<String> polish(Map<String, String> request) {
                return Result.error(ERROR_MSG);
            }

            @Override
            public Result<String> generateTags(Map<String, String> request) {
                return Result.error(ERROR_MSG);
            }

            @Override
            public Result<String> extractKnowledgeGraph(Map<String, String> request) {
                return Result.error(ERROR_MSG);
            }
        };
    }
}
