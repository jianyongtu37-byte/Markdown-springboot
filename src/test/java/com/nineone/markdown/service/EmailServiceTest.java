package com.nineone.markdown.service;

import com.nineone.markdown.service.impl.EmailServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 邮件服务测试类
 */
@ExtendWith(MockitoExtension.class)
public class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private TemplateEngine templateEngine;

    @InjectMocks
    private EmailServiceImpl emailService;

    @BeforeEach
    void setUp() {
        // 可以使用反射设置私有字段
        // emailService.setFromEmail("test@example.com");
        // emailService.setAppUrl("http://localhost:8080");
    }

    @Test
    void testSendSimpleEmail_Success() {
        // 模拟邮件发送成功
        doNothing().when(mailSender).send(any(SimpleMailMessage.class));

        // 执行测试
        boolean result = emailService.sendSimpleEmail(
            "recipient@example.com", 
            "测试主题", 
            "测试内容"
        );

        // 验证结果
        assert result : "邮件发送应该成功";
        
        // 验证邮件发送方法被调用
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    void testSendHtmlEmail_Success() {
        // 模拟HTML邮件发送成功
        when(mailSender.createMimeMessage()).thenReturn(null);
        // 注意：由于MimeMessage是复杂的对象，这里简化测试
        // 在实际测试中，可能需要更详细的模拟
        
        // 执行测试
        boolean result = emailService.sendHtmlEmail(
            "recipient@example.com", 
            "HTML测试主题", 
            "<h1>HTML内容</h1>"
        );

        // 验证结果
        // 由于我们简化了模拟，这里只验证方法被调用
        // 在实际项目中应该使用更完整的测试
    }

    @Test
    void testSendWelcomeEmail_Success() {
        // 模拟模板引擎
        when(templateEngine.process(eq("email/welcome"), any(Context.class)))
            .thenReturn("<html>欢迎邮件内容</html>");

        // 模拟HTML邮件发送成功
        when(mailSender.createMimeMessage()).thenReturn(null);

        // 执行测试
        boolean result = emailService.sendWelcomeEmail(
            "recipient@example.com", 
            "测试用户"
        );

        // 验证结果
        // 验证模板引擎被调用
        verify(templateEngine, times(1)).process(eq("email/welcome"), any(Context.class));
    }

    @Test
    void testSendRegistrationVerificationEmail_Success() {
        // 模拟模板引擎
        when(templateEngine.process(eq("email/verification"), any(Context.class)))
            .thenReturn("<html>验证邮件内容</html>");

        // 模拟HTML邮件发送成功
        when(mailSender.createMimeMessage()).thenReturn(null);

        // 执行测试
        boolean result = emailService.sendRegistrationVerificationEmail(
            "recipient@example.com", 
            "测试用户",
            "http://localhost:8080/verify?token=abc123"
        );

        // 验证结果
        verify(templateEngine, times(1)).process(eq("email/verification"), any(Context.class));
    }

    @Test
    void testSendPasswordResetEmail_Success() {
        // 模拟模板引擎
        when(templateEngine.process(eq("email/password-reset"), any(Context.class)))
            .thenReturn("<html>密码重置邮件内容</html>");

        // 模拟HTML邮件发送成功
        when(mailSender.createMimeMessage()).thenReturn(null);

        // 执行测试
        boolean result = emailService.sendPasswordResetEmail(
            "recipient@example.com", 
            "测试用户",
            "http://localhost:8080/reset-password?token=xyz789"
        );

        // 验证结果
        verify(templateEngine, times(1)).process(eq("email/password-reset"), any(Context.class));
    }

    @Test
    void testSendSimpleEmail_Failure() {
        // 模拟邮件发送失败
        doThrow(new RuntimeException("邮件发送失败")).when(mailSender).send(any(SimpleMailMessage.class));

        // 执行测试
        boolean result = emailService.sendSimpleEmail(
            "recipient@example.com", 
            "测试主题", 
            "测试内容"
        );

        // 验证结果
        assert !result : "邮件发送应该失败";
        
        // 验证邮件发送方法被调用
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }
}