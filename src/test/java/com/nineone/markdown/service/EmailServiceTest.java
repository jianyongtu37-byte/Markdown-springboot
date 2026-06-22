package com.nineone.markdown.service;

import com.nineone.markdown.service.impl.EmailServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.lang.reflect.Field;

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
    void setUp() throws Exception {
        // @Value 注解不会在单元测试中注入，需通过反射设置
        Field fromEmailField = EmailServiceImpl.class.getDeclaredField("fromEmail");
        fromEmailField.setAccessible(true);
        fromEmailField.set(emailService, "test@example.com");

        Field appUrlField = EmailServiceImpl.class.getDeclaredField("appUrl");
        appUrlField.setAccessible(true);
        appUrlField.set(emailService, "http://localhost:8080");
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
        MimeMessage mockMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mockMessage);
        doNothing().when(mailSender).send(any(MimeMessage.class));

        boolean result = emailService.sendHtmlEmail(
            "recipient@example.com",
            "HTML测试主题",
            "<h1>HTML内容</h1>"
        );

        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void testSendWelcomeEmail_Success() {
        when(templateEngine.process(eq("email/welcome"), any(Context.class)))
            .thenReturn("<html>欢迎邮件内容</html>");

        MimeMessage mockMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mockMessage);
        doNothing().when(mailSender).send(any(MimeMessage.class));

        boolean result = emailService.sendWelcomeEmail(
            "recipient@example.com",
            "测试用户"
        );

        verify(templateEngine, times(1)).process(eq("email/welcome"), any(Context.class));
        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void testSendRegistrationVerificationEmail_Success() {
        when(templateEngine.process(eq("email/verification"), any(Context.class)))
            .thenReturn("<html>验证邮件内容</html>");

        MimeMessage mockMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mockMessage);
        doNothing().when(mailSender).send(any(MimeMessage.class));

        boolean result = emailService.sendRegistrationVerificationEmail(
            "recipient@example.com",
            "测试用户",
            "http://localhost:8080/verify?token=abc123"
        );

        verify(templateEngine, times(1)).process(eq("email/verification"), any(Context.class));
        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void testSendPasswordResetEmail_Success() {
        when(templateEngine.process(eq("email/password-reset"), any(Context.class)))
            .thenReturn("<html>密码重置邮件内容</html>");

        MimeMessage mockMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mockMessage);
        doNothing().when(mailSender).send(any(MimeMessage.class));

        boolean result = emailService.sendPasswordResetEmail(
            "recipient@example.com",
            "测试用户",
            "http://localhost:8080/reset-password?token=xyz789"
        );

        verify(templateEngine, times(1)).process(eq("email/password-reset"), any(Context.class));
        verify(mailSender, times(1)).send(any(MimeMessage.class));
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