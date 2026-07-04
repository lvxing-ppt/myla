package com.myla.lis.service;

import com.myla.lis.entity.OutboundMessage;

public interface LisGatewayService {
    void sendResult(String hospitalCode, String messageContent);
    void retryMessage(Long messageId);
}
