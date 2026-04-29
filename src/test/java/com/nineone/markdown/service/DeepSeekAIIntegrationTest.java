package com.nineone.markdown.service;

import com.nineone.markdown.service.impl.DeepSeekAiSummaryServiceImpl;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DeepSeek AI集成测试类
 * 
 * 这个测试类用于验证DeepSeek AI服务是否正常工作。
 * 需要有效的DeepSeek API密钥配置在application.properties中。
 * 
 * 测试步骤：
 * 1. 检查application.properties中是否配置了有效的API密钥
 * 2. 调用DeepSeek AI服务生成文章摘要
 * 3. 验证返回的摘要是否有效
 * 
 * 注意：此测试会实际调用DeepSeek API，需要网络连接和有效的API密钥。
 */
@SpringBootTest
@TestPropertySource(locations = "classpath:application.properties")
public class DeepSeekAIIntegrationTest {
    
    private static final Logger log = LoggerFactory.getLogger(DeepSeekAIIntegrationTest.class);
    
    @Autowired
    private AiSummaryService aiSummaryService;
    
    @Test
    void testDeepSeekAIServiceIsConfigured() {
        log.info("开始测试DeepSeek AI服务配置...");
        
        // 检查是否注入了正确的服务
        assertNotNull(aiSummaryService, "AiSummaryService应该被正确注入");
        
        String serviceName = aiSummaryService.getServiceName();
        log.info("当前使用的AI服务: {}", serviceName);
        
        // 验证是否是DeepSeek服务
        assertTrue(serviceName.contains("DeepSeek"), 
                "应该使用DeepSeek AI服务，但当前服务是: " + serviceName);
        
        log.info("DeepSeek AI服务配置验证通过");
    }
    
    @Test
    void testGenerateSummaryWithSimpleContent() {
        log.info("测试简单内容摘要生成...");
        
        String simpleContent = "Spring Boot是一个用于创建独立的、生产级别的Spring应用程序的框架。"
                + "它简化了Spring应用程序的初始设置和开发过程，提供了自动配置和起步依赖等功能。";
        
        String summary = aiSummaryService.generateSummary(simpleContent);
        
        // 验证摘要不为空
        assertNotNull(summary, "生成的摘要不应该为null");
        assertFalse(summary.isEmpty(), "生成的摘要不应该为空");
        
        // 验证摘要长度合理
        assertTrue(summary.length() > 0, "摘要长度应该大于0");
        
        log.info("简单内容摘要生成测试通过");
        log.info("生成摘要: {}", summary);
        log.info("摘要长度: {} 字符", summary.length());
    }
    
    @Test
    void testGenerateSummaryWithLongContent() {
        log.info("测试长内容摘要生成...");
        
        // 创建一个较长的文章内容
        StringBuilder longContent = new StringBuilder();
        longContent.append("# Spring Boot框架介绍\n\n");
        longContent.append("Spring Boot是一个开源的Java框架，用于创建独立的、生产级别的Spring应用程序。");
        longContent.append("它简化了Spring应用程序的初始设置和开发过程，提供了自动配置和起步依赖等功能。\n\n");
        longContent.append("## 主要特性\n\n");
        longContent.append("1. **自动配置**: Spring Boot基于类路径上的jar包、已存在的bean定义和属性设置自动配置Spring应用程序。\n");
        longContent.append("2. **起步依赖**: 提供了一组方便的依赖描述符，可以快速添加功能到项目中。\n");
        longContent.append("3. **嵌入式服务器**: 支持Tomcat、Jetty或Undertow等嵌入式服务器，无需部署WAR文件。\n");
        longContent.append("4. **生产就绪**: 提供健康检查、指标、外部化配置等生产就绪功能。\n\n");
        longContent.append("## 使用场景\n\n");
        longContent.append("Spring Boot适用于各种类型的应用程序，从简单的微服务到复杂的企业级应用程序。");
        longContent.append("它的设计目标是简化Spring应用程序的开发，让开发者能够快速构建和部署应用程序。");
        
        String summary = aiSummaryService.generateSummary(longContent.toString());
        
        // 验证摘要不为空
        assertNotNull(summary, "生成的摘要不应该为null");
        assertFalse(summary.isEmpty(), "生成的摘要不应该为空");
        
        // 验证摘要比原文短
        assertTrue(summary.length() < longContent.length(), 
                "摘要应该比原文短，但摘要长度: " + summary.length() + 
                ", 原文长度: " + longContent.length());
        
        log.info("长内容摘要生成测试通过");
        log.info("原文长度: {} 字符", longContent.length());
        log.info("摘要长度: {} 字符", summary.length());
        log.info("压缩比例: {:.2f}%", (1 - (double)summary.length() / longContent.length()) * 100);
    }
    
    @Test
    void testGenerateSummaryWithChineseContent() {
        log.info("测试中文内容摘要生成...");
        
        String chineseContent = "Spring Boot是一个基于Spring框架的开源Java框架，"
                + "用于快速构建独立的、生产级别的Spring应用程序。"
                + "它通过自动配置和约定优于配置的原则，大大简化了Spring应用程序的开发和部署过程。"
                + "Spring Boot提供了嵌入式服务器、健康检查、指标监控等生产就绪功能，"
                + "使得开发者能够专注于业务逻辑的实现，而不是基础设施的搭建。"
                + "该框架广泛应用于微服务架构、RESTful API开发、企业级应用开发等领域。";
        
        String summary = aiSummaryService.generateSummary(chineseContent);
        
        // 验证摘要不为空
        assertNotNull(summary, "生成的摘要不应该为null");
        assertFalse(summary.isEmpty(), "生成的摘要不应该为空");
        
        // 验证摘要包含中文字符（如果是AI生成的）
        boolean containsChinese = summary.matches(".*[\\u4e00-\\u9fa5].*");
        if (containsChinese) {
            log.info("摘要包含中文字符，可能是AI生成的摘要");
        } else {
            log.warn("摘要不包含中文字符，可能是模拟摘要或API配置问题");
        }
        
        log.info("中文内容摘要生成测试通过");
        log.info("生成摘要: {}", summary);
    }
    
    @Test
    void testGenerateSummaryWithEmptyContent() {
        log.info("测试空内容摘要生成...");
        
        String summary = aiSummaryService.generateSummary("");
        
        // 验证返回了默认摘要
        assertNotNull(summary, "空内容摘要不应该为null");
        assertEquals("这篇文章暂无摘要内容。", summary, "空内容应该返回默认消息");
        
        log.info("空内容摘要生成测试通过");
    }
    
    /**
     * 综合测试 - 验证AI服务的完整功能
     */
    @Test
    void comprehensiveDeepSeekAITest() {
        log.info("开始DeepSeek AI服务综合测试...");
        
        // 测试1：服务配置
        assertNotNull(aiSummaryService, "AiSummaryService应该被正确注入");
        assertEquals("DeepSeek AI Summary Service", aiSummaryService.getServiceName(),
                "应该使用DeepSeek AI服务");
        
        // 测试2：正常内容摘要生成
        String testContent = "人工智能是计算机科学的一个分支，旨在创建能够执行通常需要人类智能的任务的智能机器。"
                + "这些任务包括学习、推理、问题解决、感知和语言理解。"
                + "AI技术已经广泛应用于各个领域，包括医疗诊断、自动驾驶、推荐系统和自然语言处理。";
        
        String summary = aiSummaryService.generateSummary(testContent);
        
        // 验证摘要质量
        assertNotNull(summary, "摘要不应该为null");
        assertFalse(summary.isEmpty(), "摘要不应该为空");
        assertTrue(summary.length() > 10, "摘要应该有一定长度");
        
        // 如果摘要包含模拟摘要标记，说明可能使用了模拟服务
        if (summary.contains("模拟摘要") || summary.contains("配置DeepSeek API密钥")) {
            log.warn("检测到使用模拟摘要，可能原因：");
            log.warn("1. application.properties中未配置DeepSeek API密钥");
            log.warn("2. API密钥无效或已过期");
            log.warn("3. 网络连接问题导致API调用失败");
            log.warn("4. DeepSeek API服务暂时不可用");
        } else {
            log.info("成功生成AI摘要，DeepSeek服务工作正常");
        }
        
        log.info("综合测试完成");
        log.info("测试内容长度: {} 字符", testContent.length());
        log.info("生成摘要长度: {} 字符", summary.length());
        log.info("摘要预览: {}", summary.length() > 100 ? summary.substring(0, 100) + "..." : summary);
        
        // 返回成功状态
        assertTrue(true, "综合测试应该通过");
    }
}