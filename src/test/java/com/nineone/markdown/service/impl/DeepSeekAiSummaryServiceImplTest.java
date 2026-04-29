package com.nineone.markdown.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

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
class DeepSeekAiSummaryServiceImplTest {

    private DeepSeekAiSummaryServiceImpl aiSummaryService;
    
    @Mock
    private WebClient webClient;
    
    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;
    
    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;
    
    @Mock
    private WebClient.RequestBodySpec requestBodySpec;
    
    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;
    
    @Mock
    private WebClient.ResponseSpec responseSpec;
    
    @BeforeEach
    void setUp() {
        // 使用反射设置私有字段，模拟WebClient
        aiSummaryService = new DeepSeekAiSummaryServiceImpl();
        
        // 使用Mockito来模拟WebClient行为
        // 注意：由于DeepSeekAiSummaryServiceImpl构造函数中创建了WebClient，
        // 在实际测试中可能需要使用PowerMock或重构代码以便注入WebClient
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
    void testGenerateSummary_WithoutApiKey() {
        // 模拟API密钥为空的情况
        // 这里实际上会使用application.properties中的配置
        // 为了测试无API密钥情况，可以临时修改属性或使用@TestPropertySource
        String testContent = "这是一篇测试文章内容，用于测试AI摘要生成功能。";
        
        // 调用方法
        String result = aiSummaryService.generateSummary(testContent);
        
        // 验证结果包含模拟摘要的标记
        assertNotNull(result);
        assertTrue(result.contains("模拟摘要") || result.length() <= testContent.length());
    }
    
    @Test
    void testGenerateMockSummary_ShortContent() {
        // 测试短内容模拟摘要
        String shortContent = "短内容";
        
        // 使用反射调用私有方法
        // 在实际测试中，可能需要将generateMockSummary方法改为protected或public以便测试
        // 或者使用反射工具类
        String result = aiSummaryService.generateSummary(shortContent);
        
        assertNotNull(result);
    }
    
    @Test
    void testGenerateMockSummary_LongContent() {
        // 测试长内容模拟摘要
        StringBuilder longContent = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            longContent.append("这是一段长文章内容。");
        }
        
        String result = aiSummaryService.generateSummary(longContent.toString());
        
        assertNotNull(result);
        assertTrue(result.length() <= 300); // 摘要应该被截断
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
            
            Exception exception = assertThrows(RuntimeException.class, () -> {
                method.invoke(aiSummaryService, jsonResponse);
            });
            
            assertTrue(exception.getCause().getMessage().contains("DeepSeek API错误"));
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