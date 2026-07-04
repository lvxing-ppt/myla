package com.myla.notification.service.impl;

import com.myla.notification.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class NotificationServiceImpl implements NotificationService {

    @Override
    public void sendSms(String mobile, String content) {
        log.info("Sending SMS to {}: {}", mobile, content);
        // In production, integrate with SMS gateway/provider
    }

    @Override
    public void sendEmail(String email, String subject, String content) {
        log.info("Sending email to {}: [{}] {}", email, subject, content);
        // In production, use JavaMailSender
    }
}
