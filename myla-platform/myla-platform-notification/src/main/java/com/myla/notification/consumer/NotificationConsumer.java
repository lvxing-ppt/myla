package com.myla.notification.consumer;

import com.myla.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * MYLA 系统通知消息消费者。
 * 负责监听 RabbitMQ 通知队列，处理短信和邮件通知的异步发送。
 * 支持两个队列的消息消费：
 * - notify.sms：短信通知队列
 * - notify.email：邮件通知队列
 * 每个队列的处理由对应的监听方法独立完成，互不影响。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationConsumer {

    private final NotificationService notificationService;

    /**
     * 处理短信通知消息。
     * 监听队列 "notify.sms"，从消息负载中提取手机号和短信内容，
     * 委托 NotificationService 执行实际的短信发送操作。
     *
     * @param payload 消息负载，包含 "mobile"（手机号）和 "content"（短信内容）字段
     */
    @RabbitListener(queues = "notify.sms")
    public void onSmsNotification(Map<String, Object> payload) {
        String mobile = (String) payload.get("mobile");
        String content = (String) payload.get("content");
        log.info("Processing SMS notification for {}", mobile);
        notificationService.sendSms(mobile, content);
    }

    /**
     * 处理邮件通知消息。
     * 监听队列 "notify.email"，从消息负载中提取邮箱地址、主题和正文内容，
     * 委托 NotificationService 执行实际的邮件发送操作。
     *
     * @param payload 消息负载，包含 "email"（邮箱）、"subject"（主题）
     *                和 "content"（正文）字段
     */
    @RabbitListener(queues = "notify.email")
    public void onEmailNotification(Map<String, Object> payload) {
        String email = (String) payload.get("email");
        String subject = (String) payload.get("subject");
        String content = (String) payload.get("content");
        log.info("Processing email notification for {}", email);
        notificationService.sendEmail(email, subject, content);
    }
}
