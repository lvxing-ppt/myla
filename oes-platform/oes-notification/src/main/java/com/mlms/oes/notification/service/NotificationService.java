package com.mlms.oes.notification.service;

/**
 * MLMS 系统通知服务接口。
 * 定义消息通知的核心业务操作，
 * 包括短信发送和邮件发送两种通知方式。
 * 生产环境需要对接具体的短信网关和邮件服务器。
 */
public interface NotificationService {

    /**
     * 发送短信通知。
     * 通过短信网关将指定内容发送到目标手机号。
     * 生产环境需对接具体的短信服务提供商（如阿里云短信、腾讯云短信等）。
     *
     * @param mobile  目标手机号码
     * @param content 短信内容
     */
    void sendSms(String mobile, String content);

    /**
     * 发送邮件通知。
     * 通过邮件服务器将指定主题和内容的邮件发送到目标邮箱。
     * 生产环境需使用 JavaMailSender 对接 SMTP 邮件服务器。
     *
     * @param email   目标邮箱地址
     * @param subject 邮件主题
     * @param content 邮件正文内容
     */
    void sendEmail(String email, String subject, String content);
}
