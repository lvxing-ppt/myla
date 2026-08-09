package com.myla.lis.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myla.lis.entity.LisConfig;
import com.myla.lis.entity.OutboundMessage;
import com.myla.lis.mapper.LisConfigMapper;
import com.myla.lis.mapper.OutboundMessageMapper;
import com.myla.lis.service.LisGatewayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * LIS 网关服务实现。
 * <p>
 * sendResult 流程：
 * <ol>
 *   <li>查 lis_config 获取该医院的通道配置</li>
 *   <li>持久化 OutboundMessage 到 DB（状态追踪）</li>
 *   <li>发布自包含 JSON 到 outbound.msg（通讯层消费并真实发送）</li>
 * </ol>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LisGatewayServiceImpl implements LisGatewayService {

    private final OutboundMessageMapper messageMapper;
    private final LisConfigMapper configMapper;
    private final RabbitTemplate rabbitTemplate;
    private static final ObjectMapper jsonMapper = new ObjectMapper();

    @Override
    public void sendResult(String hospitalCode, String messageContent) {
        String messageId = UUID.randomUUID().toString().replace("-", "");

        // 1. 持久化到 DB（用于追踪、重试、审计）
        OutboundMessage msg = new OutboundMessage();
        msg.setMessageId(messageId);
        msg.setHospitalCode(hospitalCode);
        msg.setMessageType("RESULT");
        msg.setMessageContent(messageContent);
        msg.setSendStatus("PENDING");
        msg.setRetryCount(0);
        msg.setMaxRetries(3);
        messageMapper.insert(msg);

        // 2. 加载医院 LIS 配置，获取通道参数
        LisConfig config = configMapper.selectByHospitalCode(hospitalCode);
        String channelType = config != null ? config.getChannelType() : "HL7";
        String outboundConfigJson = config != null ? config.getOutboundConfig() : null;
        int ackTimeoutSec = config != null && config.getAckTimeoutSec() != null ? config.getAckTimeoutSec() : 30;

        // 3. 构建自包含 JSON 消息 → 发 MQ（通讯层消费）
        Map<String, Object> mqMsg = new HashMap<>();
        mqMsg.put("messageId", messageId);
        mqMsg.put("hospitalCode", hospitalCode);
        mqMsg.put("channelType", channelType);
        mqMsg.put("messageContent", messageContent);
        mqMsg.put("ackTimeoutSec", ackTimeoutSec);

        // 解析 channelConfig JSON
        if (outboundConfigJson != null && !outboundConfigJson.isBlank()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> channelCfg = jsonMapper.readValue(outboundConfigJson, Map.class);
                mqMsg.put("channelConfig", channelCfg);
            } catch (Exception e) {
                log.warn("Failed to parse outbound_config for hospital={}: {}", hospitalCode, e.getMessage());
                mqMsg.put("channelConfig", Map.of("host", "localhost", "port", 2575));
            }
        } else {
            mqMsg.put("channelConfig", Map.of("host", "localhost", "port", 2575));
        }

        rabbitTemplate.convertAndSend("myla.lis", "outbound.msg", mqMsg);
        log.info("LIS outbound message queued: messageId={}, hospitalCode={}, channelType={}",
                messageId, hospitalCode, channelType);
    }

    @Override
    public void retryMessage(Long messageId) {
        OutboundMessage msg = messageMapper.selectById(messageId);
        if (msg != null && msg.getRetryCount() < msg.getMaxRetries()) {
            msg.setSendStatus("PENDING");
            msg.setRetryCount(msg.getRetryCount() + 1);
            msg.setNextRetryAt(LocalDateTime.now().plusMinutes(1));
            messageMapper.updateById(msg);

            // 重新发送到 MQ
            sendResult(msg.getHospitalCode(), msg.getMessageContent());
            log.info("LIS message retry scheduled: messageId={}, retryCount={}", msg.getMessageId(), msg.getRetryCount());
        }
    }
}
