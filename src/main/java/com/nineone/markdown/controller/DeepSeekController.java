 package com.nineone.markdown.controller;

import com.nineone.markdown.common.Result;
import com.nineone.markdown.service.AiSummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * DeepSeek AI 控制器
 * 提供 DeepSeek AI 相关的 API 接口
 */
@RestController
@RequestMapping("/api/deepseek")
@RequiredArgsConstructor
@Validated
@Slf4j
public class DeepSeekController {

    private final AiSummaryService aiSummaryService;

    @Value("${ai.deepseek.api-key:}")
    private String apiKey;

    @Value("${ai.deepseek.api-url:https://api.deepseek.com/v1}")
    private String apiUrl;

    @Value("${ai.deepseek.model:deepseek-chat}")
    private String model;

    @Value("${ai.deepseek.timeout-seconds:30}")
    private int timeoutSeconds;

    @Value("${ai.deepseek.max-tokens:500}")
    private int maxTokens;

    /**
     * 测试 DeepSeek API 连接状态
     * 用于验证 API 配置是否正确
     */
    @GetMapping("/status")
    public Result<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        
        // 基础配置信息
        status.put("serviceName", aiSummaryService.getServiceName());
        status.put("apiConfigured", apiKey != null && !apiKey.trim().isEmpty());
        status.put("apiUrl", apiUrl);
        status.put("model", model);
        status.put("timeoutSeconds", timeoutSeconds);
        status.put("maxTokens", maxTokens);
        
        // 测试连接状态
        boolean connected = testConnection();
        status.put("connected", connected);
        status.put("status", connected ? "连接正常" : "连接失败");
        
        if (!connected) {
            status.put("message", "请检查 API Key 配置和网络连接");
        }
        
        log.info("DeepSeek API 状态检查: {}", status);
        return Result.success("DeepSeek API 状态查询成功", status);
    }

    /**
     * 手动生成文章摘要
     * 适用于调试或手动触发 AI 摘要生成
     */
    @PostMapping("/generate-summary")
    public Result<Map<String, Object>> generateSummary(@RequestBody Map<String, String> request) {
        String content = request.get("content");
        
        if (content == null || content.trim().isEmpty()) {
            return Result.failure("文章内容不能为空");
        }
        
        try {
            log.info("手动触发 DeepSeek 摘要生成，内容长度: {}", content.length());
            
            // 调用 AI 服务生成摘要
            String summary = aiSummaryService.generateSummary(content);
            
            Map<String, Object> result = new HashMap<>();
            result.put("originalLength", content.length());
            result.put("summaryLength", summary.length());
            result.put("summary", summary);
            result.put("serviceUsed", aiSummaryService.getServiceName());
            result.put("success", true);
            
            log.info("DeepSeek 摘要生成成功，摘要长度: {}", summary.length());
            return Result.success("AI 摘要生成成功", result);
            
        } catch (Exception e) {
            log.error("DeepSeek 摘要生成失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("serviceUsed", aiSummaryService.getServiceName());
            return Result.failure("AI 摘要生成失败: " + e.getMessage(), result);
        }
    }

    /**
     * 测试与 DeepSeek API 的连接
     * 通过发送一个简单的测试请求验证连接性
     */
    @PostMapping("/test-connection")
    public Result<Map<String, Object>> testApiConnection() {
        try {
            // 使用一个简单的测试内容来验证连接
            String testContent = "这是一个测试内容，用于验证 DeepSeek API 连接状态。";
            String testSummary = aiSummaryService.generateSummary(testContent);
            
            Map<String, Object> result = new HashMap<>();
            result.put("connected", true);
            result.put("serviceName", aiSummaryService.getServiceName());
            result.put("testContent", testContent);
            result.put("testSummary", testSummary);
            result.put("responseTime", "正常");
            
            log.info("DeepSeek API 连接测试成功");
            return Result.success("DeepSeek API 连接正常", result);
            
        } catch (Exception e) {
            log.error("DeepSeek API 连接测试失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("connected", false);
            result.put("serviceName", aiSummaryService.getServiceName());
            result.put("error", e.getMessage());
            result.put("suggestion", "请检查 API Key 配置、网络连接或 API 服务状态");
            return Result.failure("DeepSeek API 连接测试失败: " + e.getMessage(), result);
        }
    }

    /**
     * 获取 DeepSeek 服务配置信息
     * 返回当前配置的详细信息（不包含敏感的 API Key）
     */
    @GetMapping("/config")
    public Result<Map<String, Object>> getConfig() {
        Map<String, Object> config = new HashMap<>();
        
        // 基本配置
        config.put("apiUrl", apiUrl);
        config.put("model", model);
        config.put("timeoutSeconds", timeoutSeconds);
        config.put("maxTokens", maxTokens);
        config.put("serviceName", aiSummaryService.getServiceName());
        
        // API Key 状态（只显示是否已配置，不显示具体值）
        boolean apiKeyConfigured = apiKey != null && !apiKey.trim().isEmpty();
        config.put("apiKeyConfigured", apiKeyConfigured);
        config.put("apiKeyStatus", apiKeyConfigured ? "已配置" : "未配置");
        
        // 服务类型
        String serviceType = "真实 API 服务";
        if (aiSummaryService.getServiceName().contains("模拟")) {
            serviceType = "模拟服务";
        }
        config.put("serviceType", serviceType);
        
        log.info("DeepSeek 配置信息查询");
        return Result.success("DeepSeek 配置信息查询成功", config);
    }

    /**
     * 生成文章标题
     * 使用 AI 根据文章内容生成合适的标题
     */
    @PostMapping("/generate-title")
    public Result<Map<String, Object>> generateTitle(@RequestBody Map<String, String> request) {
        String content = request.get("content");
        
        if (content == null || content.trim().isEmpty()) {
            return Result.failure("文章内容不能为空");
        }
        
        try {
            log.info("DeepSeek 标题生成请求，内容长度: {}", content.length());
            
            // 调用 AI 服务生成标题
            String title = aiSummaryService.generateTitle(content);
            
            Map<String, Object> result = new HashMap<>();
            result.put("originalLength", content.length());
            result.put("title", title);
            result.put("serviceUsed", aiSummaryService.getServiceName());
            result.put("success", true);
            
            log.info("DeepSeek 标题生成成功，标题: {}", title);
            return Result.success("AI 标题生成成功", result);
            
        } catch (Exception e) {
            log.error("DeepSeek 标题生成失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("serviceUsed", aiSummaryService.getServiceName());
            return Result.failure("AI 标题生成失败: " + e.getMessage(), result);
        }
    }

    /**
     * 简单的聊天接口
     * 提供基本的 AI 聊天功能（如果项目需要）
     */
    @PostMapping("/chat")
    public Result<Map<String, Object>> chat(@RequestBody Map<String, String> request) {
        String message = request.get("message");
        
        if (message == null || message.trim().isEmpty()) {
            return Result.failure("聊天消息不能为空");
        }
        
        try {
            log.info("DeepSeek 聊天请求，消息长度: {}", message.length());
            
            // 构建聊天内容 - 使用一个简单的提示词
            String chatContent = "请对以下消息进行回复：" + message;
            String response = aiSummaryService.generateSummary(chatContent);
            
            Map<String, Object> result = new HashMap<>();
            result.put("request", message);
            result.put("response", response);
            result.put("serviceUsed", aiSummaryService.getServiceName());
            result.put("timestamp", System.currentTimeMillis());
            
            log.info("DeepSeek 聊天响应生成成功");
            return Result.success("聊天响应生成成功", result);
            
        } catch (Exception e) {
            log.error("DeepSeek 聊天请求失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("error", e.getMessage());
            result.put("serviceUsed", aiSummaryService.getServiceName());
            return Result.failure("聊天请求失败: " + e.getMessage(), result);
        }
    }

    /**
     * 文本润色接口
     * 使用 AI 对文本进行润色改进
     */
    @PostMapping("/polish")
    public Result<Map<String, Object>> polish(@RequestBody Map<String, String> request) {
        String content = request.get("content");
        String style = request.get("style");
        String tone = request.get("tone");
        
        if (content == null || content.trim().isEmpty()) {
            return Result.failure("文本内容不能为空");
        }
        
        try {
            log.info("DeepSeek 文本润色请求，内容长度: {}, 风格: {}, 语气: {}", 
                    content.length(), style, tone);
            
            // 调用 AI 服务进行润色
            String polishedText = aiSummaryService.polishText(content, style, tone);
            
            Map<String, Object> result = new HashMap<>();
            result.put("originalLength", content.length());
            result.put("polishedLength", polishedText.length());
            result.put("original", content);
            result.put("polished", polishedText);
            result.put("serviceUsed", aiSummaryService.getServiceName());
            result.put("success", true);
            
            log.info("DeepSeek 文本润色成功，润色后长度: {}", polishedText.length());
            return Result.success("文本润色成功", result);
            
        } catch (Exception e) {
            log.error("DeepSeek 文本润色失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("serviceUsed", aiSummaryService.getServiceName());
            return Result.failure("文本润色失败: " + e.getMessage(), result);
        }
    }

    /**
     * 内部方法：测试 API 连接
     * @return 连接是否成功
     */
    private boolean testConnection() {
        try {
            // 尝试调用一个简短的内容来测试连接
            String testContent = "测试连接";
            aiSummaryService.generateSummary(testContent);
            return true;
        } catch (Exception e) {
            log.warn("DeepSeek API 连接测试失败: {}", e.getMessage());
            return false;
        }
    }
}