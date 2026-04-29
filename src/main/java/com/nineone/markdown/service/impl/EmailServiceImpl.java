package com.nineone.markdown.service.impl;

import com.nineone.markdown.service.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Locale;

/**
 * 邮件服务实现类
 * 支持QQ邮箱和谷歌邮箱的邮件发送
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.url:http://localhost:8080}")
    private String appUrl;

    @Override
    public boolean sendSimpleEmail(String to, String subject, String content) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(content);
            
            mailSender.send(message);
            log.info("简单邮件发送成功，收件人: {}, 主题: {}", to, subject);
            return true;
        } catch (Exception e) {
            log.error("简单邮件发送失败，收件人: {}, 主题: {}", to, subject, e);
            return false;
        }
    }

    @Override
    public boolean sendHtmlEmail(String to, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            
            mailSender.send(message);
            log.info("HTML邮件发送成功，收件人: {}, 主题: {}", to, subject);
            return true;
        } catch (MessagingException e) {
            log.error("HTML邮件发送失败，收件人: {}, 主题: {}", to, subject, e);
            return false;
        }
    }

    @Override
    public boolean sendRegistrationVerificationEmail(String to, String username, String verificationLink) {
        try {
            Context context = new Context(Locale.CHINA);
            context.setVariable("username", username);
            context.setVariable("verificationLink", verificationLink);
            context.setVariable("appUrl", appUrl);
            
            String htmlContent = templateEngine.process("email/verification", context);
            
            String subject = "【Markdown知识库】请验证您的邮箱地址";
            
            return sendHtmlEmail(to, subject, htmlContent);
        } catch (Exception e) {
            log.error("注册验证邮件发送失败，收件人: {}, 用户名: {}", to, username, e);
            return false;
        }
    }

    @Override
    public boolean sendPasswordResetEmail(String to, String username, String resetLink) {
        try {
            Context context = new Context(Locale.CHINA);
            context.setVariable("username", username);
            context.setVariable("resetLink", resetLink);
            context.setVariable("appUrl", appUrl);
            
            String htmlContent = templateEngine.process("email/password-reset", context);
            
            String subject = "【Markdown知识库】重置密码请求";
            
            return sendHtmlEmail(to, subject, htmlContent);
        } catch (Exception e) {
            log.error("密码重置邮件发送失败，收件人: {}, 用户名: {}", to, username, e);
            return false;
        }
    }

    @Override
    public boolean sendWelcomeEmail(String to, String username) {
        try {
            Context context = new Context(Locale.CHINA);
            context.setVariable("username", username);
            context.setVariable("appUrl", appUrl);
            
            String htmlContent = templateEngine.process("email/welcome", context);
            
            String subject = "【Markdown知识库】欢迎加入！";
            
            return sendHtmlEmail(to, subject, htmlContent);
        } catch (Exception e) {
            log.error("欢迎邮件发送失败，收件人: {}, 用户名: {}", to, username, e);
            return false;
        }
    }

    /**
     * 异步发送简单邮件
     */
    @Async
    public void sendSimpleEmailAsync(String to, String subject, String content) {
        sendSimpleEmail(to, subject, content);
    }

    /**
     * 异步发送HTML邮件
     */
    @Async
    public void sendHtmlEmailAsync(String to, String subject, String htmlContent) {
        sendHtmlEmail(to, subject, htmlContent);
    }

    /**
     * 异步发送欢迎邮件
     */
    @Async
    public void sendWelcomeEmailAsync(String to, String username) {
        sendWelcomeEmail(to, username);
    }

    /**
     * 异步发送注册验证邮件
     */
    @Async
    public void sendRegistrationVerificationEmailAsync(String to, String username, String verificationLink) {
        sendRegistrationVerificationEmail(to, username, verificationLink);
    }

    /**
     * 异步发送密码重置邮件
     */
    @Async
    public void sendPasswordResetEmailAsync(String to, String username, String resetLink) {
        sendPasswordResetEmail(to, username, resetLink);
    }
}