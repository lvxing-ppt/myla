package com.myla.notification.consumer;

import com.myla.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationConsumer {

    private final NotificationService notificationService;

    @RabbitListener(queues = "notify.sms")
    public void onSmsNotification(Map<String, Object> payload) {
        String mobile = (String) payload.get("mobile");
        String content = (String) payload.get("content");
        log.info("Processing SMS notification for {}", mobile);
        notificationService.sendSms(mobile, content);
    }

    @RabbitListener(queues = "notify.email")
    public void onEmailNotification(Map<String, Object> payload) {
        String email = (String) payload.get("email");
        String subject = (String) payload.get("subject");
        String content = (String) payload.get("content");
        log.info("Processing email notification for {}", email);
        notificationService.sendEmail(email, subject, content);
    }
}
