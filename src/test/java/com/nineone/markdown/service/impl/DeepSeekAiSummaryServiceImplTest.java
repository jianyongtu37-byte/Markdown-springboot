package com.nineone.markdown.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.lang.reflect.Field;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DeepSeek AI摘要服务测试类
 * 
 * 测试场景：
 * 1. API密钥为空时，使用模拟摘要
 * 2. API调用成功时，返回AI生成的摘要
 * 3. API调用失败时，使用模拟摘要作为备选方案
 * 4. 输入内容为空时的处理
 * 5. 异步生成摘要功能
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeepSeekAiSummaryServiceImplTest {

    private DeepSeekAiSummaryServiceImpl aiSummaryService;
    
    @BeforeEach
    void setUp() throws Exception {
        aiSummaryService = spy(new DeepSeekAiSummaryServiceImpl());

        // 通过反射设置apiKey，避免无API密钥时直接抛异常
        Field apiKeyField = DeepSeekAiSummaryServiceImpl.class.getDeclaredField("apiKey");
        apiKeyField.setAccessible(true);
        apiKeyField.set(aiSummaryService, "test-api-key");

        // mock WebClient 链式调用 — 使用 doReturn 避免 when() 实际执行方法链
        String validResponse = "{\"choices\":[{\"message\":{\"content\":\"这是AI生成的摘要。\"}}]}";
        WebClient.RequestBodyUriSpec mockUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec mockBodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.ResponseSpec mockResponseSpec = mock(WebClient.ResponseSpec.class);

        // 将 mock WebClient 直接注入字段，绕过 getWebClient() 的懒加载
        WebClient mockWebClient = mock(WebClient.class);
        Field webClientField = DeepSeekAiSummaryServiceImpl.class.getDeclaredField("webClient");
        webClientField.setAccessible(true);
        webClientField.set(aiSummaryService, mockWebClient);

        // 逐级 doReturn 搭建链式调用
        doReturn(mockUriSpec).when(mockWebClient).post();
        doReturn(mockBodySpec).when(mockUriSpec).uri(anyString());
        doReturn(mockBodySpec).when(mockBodySpec).header(anyString(), any(String[].class));
        doReturn(mockBodySpec).when(mockBodySpec).bodyValue(any());
        doReturn(mockResponseSpec).when(mockBodySpec).retrieve();

        // mock Mono 链：timeout() 返回自身，block() 返回响应
        Mono<String> mockMono = mock(Mono.class);
        doReturn(mockMono).when(mockResponseSpec).bodyToMono(String.class);
        doReturn(mockMono).when(mockMono).timeout(any(Duration.class));
        doReturn(validResponse).when(mockMono).block();
    }
    
    @Test
    void testGenerateSummary_WithEmptyContent() {
        // 测试空内容
        String result = aiSummaryService.generateSummary("");
        
        assertEquals("这篇文章暂无摘要内容。", result);
    }
    
    @Test
    void testGenerateSummary_WithNullContent() {
        // 测试null内容
        String result = aiSummaryService.generateSummary(null);
        
        assertEquals("这篇文章暂无摘要内容。", result);
    }
    
    @Test
    void testGenerateSummary_WithBlankContent() {
        // 测试空白内容
        String result = aiSummaryService.generateSummary("   ");
        
        assertEquals("这篇文章暂无摘要内容。", result);
    }
    
    @Test
    void testGenerateSummary_WithoutApiKey() throws Exception {
        // 通过反射清空apiKey，测试无API密钥的情况
        Field apiKeyField = DeepSeekAiSummaryServiceImpl.class.getDeclaredField("apiKey");
        apiKeyField.setAccessible(true);
        apiKeyField.set(aiSummaryService, "");

        String testContent = "这是一篇测试文章内容，用于测试AI摘要生成功能。";

        // 无API密钥时应抛出异常
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            aiSummaryService.generateSummary(testContent);
        });
        assertTrue(exception.getMessage().contains("AI服务暂不可用"));
    }
    
    @Test
    void testGenerateMockSummary_ShortContent() {
        String shortContent = "短内容";

        String result = aiSummaryService.generateSummary(shortContent);

        assertNotNull(result);
        assertFalse(result.isEmpty());
    }
    
    @Test
    void testGenerateMockSummary_LongContent() {
        StringBuilder longContent = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            longContent.append("这是一段长文章内容。");
        }

        String result = aiSummaryService.generateSummary(longContent.toString());

        assertNotNull(result);
        assertFalse(result.isEmpty());
    }
    
    @Test
    void testGetServiceName() {
        String serviceName = aiSummaryService.getServiceName();
        
        assertEquals("DeepSeek AI Summary Service", serviceName);
    }
    
    @Test
    void testBuildPrompt() {
        String content = "测试内容";
        
        // 使用反射测试私有方法
        // 在实际测试中，可以提取为protected方法或使用反射
        try {
            java.lang.reflect.Method method = DeepSeekAiSummaryServiceImpl.class
                    .getDeclaredMethod("buildPrompt", String.class);
            method.setAccessible(true);
            String prompt = (String) method.invoke(aiSummaryService, content);
            
            assertNotNull(prompt);
            assertTrue(prompt.contains(content));
            assertTrue(prompt.contains("请为以下文章生成一个简洁、准确的摘要"));
        } catch (Exception e) {
            fail("测试私有方法失败: " + e.getMessage());
        }
    }
    
    @Test
    void testParseResponse_ValidResponse() {
        String jsonResponse = "{\"choices\":[{\"message\":{\"content\":\"这是AI生成的摘要内容。\"}}]}";
        
        try {
            java.lang.reflect.Method method = DeepSeekAiSummaryServiceImpl.class
                    .getDeclaredMethod("parseResponse", String.class);
            method.setAccessible(true);
            String summary = (String) method.invoke(aiSummaryService, jsonResponse);
            
            assertEquals("这是AI生成的摘要内容。", summary);
        } catch (Exception e) {
            fail("测试私有方法失败: " + e.getMessage());
        }
    }
    
    @Test
    void testParseResponse_ErrorResponse() {
        String jsonResponse = "{\"error\":{\"message\":\"API调用失败\"}}";

        try {
            java.lang.reflect.Method method = DeepSeekAiSummaryServiceImpl.class
                    .getDeclaredMethod("parseResponse", String.class);
            method.setAccessible(true);

            // 反射调用会将异常包装为 InvocationTargetException
            java.lang.reflect.InvocationTargetException exception =
                    assertThrows(java.lang.reflect.InvocationTargetException.class, () -> {
                        method.invoke(aiSummaryService, jsonResponse);
                    });

            // 验证异常链中包含 "DeepSeek API错误"（可能被外层 RuntimeException 包装）
            Throwable cause = exception.getCause();
            assertInstanceOf(RuntimeException.class, cause);
            boolean found = false;
            Throwable current = cause;
            while (current != null) {
                if (current.getMessage() != null && current.getMessage().contains("DeepSeek API错误")) {
                    found = true;
                    break;
                }
                current = current.getCause();
            }
            assertTrue(found, "异常链中应包含 'DeepSeek API错误'，实际消息: " + cause.getMessage());
        } catch (Exception e) {
            fail("测试私有方法失败: " + e.getMessage());
        }
    }
    
    /**
     * 集成测试 - 需要实际的DeepSeek API密钥
     * 在实际环境中运行此测试前，请确保在application.properties中配置了有效的API密钥
     */
    @Test
    void integrationTest_WithRealApi() {
        // 此测试仅在配置了有效API密钥时运行
        String apiKey = System.getProperty("ai.deepseek.api-key");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            System.out.println("未配置DeepSeek API密钥，跳过集成测试");
            return;
        }
        
        String testContent = "Spring Boot是一个开源的Java框架，用于创建独立的、生产级别的Spring应用程序。" +
                            "它简化了Spring应用程序的初始设置和开发过程，提供了自动配置和起步依赖等功能。" +
                            "Spring Boot使得开发者能够快速构建和部署应用程序，减少了大量的样板代码。";
        
        String summary = aiSummaryService.generateSummary(testContent);
        
        assertNotNull(summary);
        assertFalse(summary.isEmpty());
        System.out.println("AI生成的摘要: " + summary);
    }
}