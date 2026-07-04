package com.myla.notification.service.impl;

import com.myla.notification.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * MYLA 系统通知服务实现类。
 * 实现短信和邮件发送的核心业务逻辑。
 * 当前为开发/测试阶段的桩实现，仅记录日志；
 * 生产环境需要对接实际的短信网关和 SMTP 邮件服务器。
 * 通知的异步分发由 NotificationConsumer（RabbitMQ 消费者）负责触发。
 */
@Slf4j
@Service
public class NotificationServiceImpl implements NotificationService {

    /**
     * 发送短信通知。
     * 当前实现仅记录日志，生产环境需集成短信网关 SDK。
     *
     * @param mobile  目标手机号码
     * @param content 短信内容
     */
    @Override
    public void sendSms(String mobile, String content) {
        log.info("Sending SMS to {}: {}", mobile, content);
        // In production, integrate with SMS gateway/provider
    }

    /**
     * 发送邮件通知。
     * 当前实现仅记录日志，生产环境需使用 JavaMailSender 对接 SMTP 服务器。
     *
     * @param email   目标邮箱地址
     * @param subject 邮件主题
     * @param content 邮件正文内容
     */
    @Override
    public void sendEmail(String email, String subject, String content) {
        log.info("Sending email to {}: [{}] {}", email, subject, content);
        // In production, use JavaMailSender
    }
}
