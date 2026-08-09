package com.mlms.capl.outbound;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * LIS 出站消息消费者（通讯层侧）。
 * <p>
 * 消费 outbound.msg 队列，根据 channelType 选择对应的 LisOutboundSender
 * 真实发送到外部 LIS 系统。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LisOutboundConsumer {

    private final List<LisOutboundSender> senders;
    private static final ObjectMapper jsonMapper = new ObjectMapper();

    /**
     * 消费出站消息队列。
     * <p>消息格式：{"messageId":"...","hospitalCode":"...","channelType":"HL7",
     * "channelConfig":{"host":"...","port":2576},"messageContent":"MSH|...","ackTimeoutSec":30}</p>
     */
    @SuppressWarnings("unchecked")
    @RabbitListener(queues = "outbound.msg")
    public void onOutboundMessage(Map<String, Object> msg) {
        String messageId = (String) msg.getOrDefault("messageId", "?");
        String hospitalCode = (String) msg.getOrDefault("hospitalCode", "?");
        String channelType = (String) msg.getOrDefault("channelType", "HL7");
        String messageContent = (String) msg.get("messageContent");
        Map<String, Object> channelConfig = (Map<String, Object>) msg.get("channelConfig");

        log.info("Outbound request: messageId={}, hospital={}, channelType={}", messageId, hospitalCode, channelType);

        if (messageContent == null || messageContent.isBlank()) {
            log.error("Empty messageContent for messageId={}", messageId);
            return;
        }

        LisOutboundSender sender = senders.stream()
                .filter(s -> s.getChannelType().equalsIgnoreCase(channelType))
                .findFirst()
                .orElse(null);

        if (sender == null) {
            log.error("No sender found for channelType={}, messageId={}", channelType, messageId);
            return;
        }

        try {
            LisOutboundSender.SendResult result = sender.send(channelConfig, messageContent, hospitalCode);
            if (result.isSuccess()) {
                log.info("Outbound sent OK: messageId={}", messageId);
            } else {
                log.error("Outbound send failed: messageId={}, error={}", messageId, result.getError());
            }
        } catch (Exception e) {
            log.error("Outbound send exception: messageId={}, error={}", messageId, e.getMessage());
        }
    }
}
