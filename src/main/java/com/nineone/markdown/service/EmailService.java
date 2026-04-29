package com.nineone.markdown.service;

/**
 * 邮件服务接口
 * 支持QQ邮箱和谷歌邮箱的邮件发送功能
 */
public interface EmailService {

    /**
     * 发送简单文本邮件
     * @param to 收件人邮箱地址
     * @param subject 邮件主题
     * @param content 邮件内容
     * @return 是否发送成功
     */
    boolean sendSimpleEmail(String to, String subject, String content);

    /**
     * 发送HTML格式邮件
     * @param to 收件人邮箱地址
     * @param subject 邮件主题
     * @param htmlContent HTML格式的邮件内容
     * @return 是否发送成功
     */
    boolean sendHtmlEmail(String to, String subject, String htmlContent);

    /**
     * 发送用户注册验证邮件
     * @param to 收件人邮箱地址
     * @param username 用户名
     * @param verificationLink 验证链接
     * @return 是否发送成功
     */
    boolean sendRegistrationVerificationEmail(String to, String username, String verificationLink);

    /**
     * 发送密码重置邮件
     * @param to 收件人邮箱地址
     * @param username 用户名
     * @param resetLink 重置链接
     * @return 是否发送成功
     */
    boolean sendPasswordResetEmail(String to, String username, String resetLink);

    /**
     * 发送欢迎邮件
     * @param to 收件人邮箱地址
     * @param username 用户名
     * @return 是否发送成功
     */
    boolean sendWelcomeEmail(String to, String username);
}