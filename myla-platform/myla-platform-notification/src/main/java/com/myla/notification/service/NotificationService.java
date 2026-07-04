package com.myla.notification.service;

public interface NotificationService {
    void sendSms(String mobile, String content);
    void sendEmail(String email, String subject, String content);
}
