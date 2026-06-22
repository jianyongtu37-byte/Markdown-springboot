package com.nineone.markdown.client.fallback;

import com.nineone.markdown.client.ExportServiceClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 导出服务 Feign 客户端降级工厂
 * 当 markdown-export 服务不可用时返回 null（调用方需处理）
 */
@Slf4j
@Component
public class ExportServiceClientFallbackFactory implements FallbackFactory<ExportServiceClient> {

    @Override
    public ExportServiceClient create(Throwable cause) {
        log.error("导出服务调用失败，触发降级: {}", cause.getMessage());
        return new ExportServiceClient() {

            @Override
            public byte[] exportPdf(Map<String, Object> request, Long userId) {
                return null;
            }

            @Override
            public byte[] exportWord(Map<String, Object> request, Long userId) {
                return null;
            }

            @Override
            public byte[] exportMarkdownZip(Map<String, Object> request, Long userId) {
                return null;
            }
        };
    }
}
