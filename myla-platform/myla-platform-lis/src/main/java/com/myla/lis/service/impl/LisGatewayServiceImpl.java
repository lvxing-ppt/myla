package com.myla.lis.service.impl;

import com.myla.lis.entity.OutboundMessage;
import com.myla.lis.mapper.OutboundMessageMapper;
import com.myla.lis.service.LisGatewayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LisGatewayServiceImpl implements LisGatewayService {

    private final OutboundMessageMapper messageMapper;

    @Override
    public void sendResult(String hospitalCode, String messageContent) {
        OutboundMessage msg = new OutboundMessage();
        msg.setMessageId(UUID.randomUUID().toString().replace("-", ""));
        msg.setHospitalCode(hospitalCode);
        msg.setMessageType("RESULT");
        msg.setMessageContent(messageContent);
        msg.setSendStatus("PENDING");
        msg.setRetryCount(0);
        msg.setMaxRetries(3);
        messageMapper.insert(msg);

        log.info("LIS outbound message queued: messageId={}, hospitalCode={}", msg.getMessageId(), hospitalCode);
    }

    @Override
    public void retryMessage(Long messageId) {
        OutboundMessage msg = messageMapper.selectById(messageId);
        if (msg != null && msg.getRetryCount() < msg.getMaxRetries()) {
            msg.setSendStatus("PENDING");
            msg.setRetryCount(msg.getRetryCount() + 1);
            msg.setNextRetryAt(LocalDateTime.now().plusMinutes(1));
            messageMapper.updateById(msg);
            log.info("LIS message retry scheduled: messageId={}, retryCount={}", msg.getMessageId(), msg.getRetryCount());
        }
    }
}
