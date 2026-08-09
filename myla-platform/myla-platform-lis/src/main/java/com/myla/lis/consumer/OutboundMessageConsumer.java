package com.myla.lis.consumer;

import com.myla.lis.entity.LisConfig;
import com.myla.lis.entity.OutboundMessage;
import com.myla.lis.mapper.LisConfigMapper;
import com.myla.lis.mapper.OutboundMessageMapper;
import com.myla.lis.outbound.LisOutboundSender;
import com.myla.lis.outbound.LisOutboundSender.SendResult;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * LIS 出站消息消费者。
 * <p>
 * 监听 outbound.msg 队列，根据 lis_config.channel_type 选择对应的
 * LisOutboundSender（HL7 MLLP / ASTM TCP / HTTP）实际发送消息到外部 LIS。
 * </p>
 *
 * <h3>发送流程：</h3>
 * <ol>
 *   <li>从队列 "outbound.msg" 消费出站消息</li>
 *   <li>查 lis_config 获取该医院的通道配置</li>
 *   <li>选择匹配的 LisOutboundSender</li>
 *   <li>调用 sender.send() 真实发送</li>
 *   <li>成功 → SENT + ACK</li>
 *   <li>失败 → 重试 / DLQ</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboundMessageConsumer {

    private final OutboundMessageMapper messageMapper;
    private final LisConfigMapper configMapper;
    private final List<LisOutboundSender> senders;

    @RabbitListener(queues = "outbound.msg")
    public void onOutboundMessage(OutboundMessage msg, Message message, Channel channel,
                                   @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        try {
            log.info("Processing outbound message: messageId={}, hospital={}, type={}",
                    msg.getMessageId(), msg.getHospitalCode(), msg.getMessageType());

            // 1. 加载 LIS 配置
            LisConfig config = configMapper.selectByHospitalCode(msg.getHospitalCode());
            if (config == null) {
                log.error("No LIS config for hospital={}, sending to DLQ", msg.getHospitalCode());
                channel.basicNack(deliveryTag, false, false); // DLQ
                return;
            }

            // 2. 选择匹配的 sender
            LisOutboundSender sender = senders.stream()
                    .filter(s -> s.getChannelType().equalsIgnoreCase(config.getChannelType()))
                    .findFirst()
                    .orElse(null);

            if (sender == null) {
                log.warn("No sender for channel_type={}, marking as SENT (no-op)", config.getChannelType());
                msg.setSendStatus("SENT");
                msg.setSentAt(LocalDateTime.now());
                messageMapper.updateById(msg);
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 3. 真实发送
            SendResult result = sender.send(msg, config);

            if (result.isSuccess()) {
                msg.setSendStatus("SENT");
                msg.setSentAt(LocalDateTime.now());
                messageMapper.updateById(msg);
                channel.basicAck(deliveryTag, false);
                log.info("Outbound message sent: messageId={}", msg.getMessageId());
            } else {
                log.error("Send failed: messageId={}, error={}", msg.getMessageId(), result.getError());
                msg.setSendStatus("FAILED");
                msg.setLastError(result.getError());
                messageMapper.updateById(msg);
                handleFailure(msg, channel, deliveryTag);
            }
        } catch (Exception e) {
            log.error("Outbound processing error: messageId={}, error={}",
                    msg.getMessageId(), e.getMessage());
            msg.setSendStatus("FAILED");
            msg.setLastError(e.getMessage());
            messageMapper.updateById(msg);
            handleFailure(msg, channel, deliveryTag);
        }
    }

    private void handleFailure(OutboundMessage msg, Channel channel, long deliveryTag) {
        try {
            if (msg.getRetryCount() >= msg.getMaxRetries()) {
                channel.basicNack(deliveryTag, false, false); // → DLQ
            } else {
                msg.setRetryCount(msg.getRetryCount() + 1);
                msg.setNextRetryAt(LocalDateTime.now().plusMinutes(1));
                messageMapper.updateById(msg);
                channel.basicNack(deliveryTag, false, true); // requeue
            }
        } catch (IOException e) {
            log.error("Failed to nack message", e);
        }
    }
}
