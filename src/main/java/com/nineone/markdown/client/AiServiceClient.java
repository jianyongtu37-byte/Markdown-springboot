package com.nineone.markdown.client;

import com.nineone.common.result.Result;
import com.nineone.markdown.client.fallback.AiServiceClientFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * AI 服务 Feign 客户端
 */
@FeignClient(name = "markdown-ai", path = "/api", fallbackFactory = AiServiceClientFallbackFactory.class)
public interface AiServiceClient {

    /**
     * 生成 AI 摘要
     */
    @PostMapping("/ai/summary")
    Result<String> generateSummary(@RequestBody Map<String, String> request);

    /**
     * 文章润色
     */
    @PostMapping("/ai/polish")
    Result<String> polish(@RequestBody Map<String, String> request);

    /**
     * 从文章内容中生成标签
     */
    @PostMapping("/ai/generate-tags")
    Result<String> generateTags(@RequestBody Map<String, String> request);

    /**
     * 从文章内容中抽取知识图谱
     */
    @PostMapping("/ai/extract-knowledge-graph")
    Result<String> extractKnowledgeGraph(@RequestBody Map<String, String> request);
}
