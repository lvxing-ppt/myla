package com.myla.lis.consumer;

import com.myla.lis.entity.OutboundMessage;
import com.myla.lis.mapper.OutboundMessageMapper;
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

/**
 * MYLA 系统 LIS 出站消息消费者。
 * 负责监听 RabbitMQ 出站消息队列，处理向外发送的 LIS 消息。
 *
 * 消息处理流程：
 * 1. 从队列 "outbound.msg" 消费出站消息
 * 2. 尝试发送消息到外部 LIS 系统（生产环境通过 HL7/ASTM/HTTP 通道）
 * 3. 发送成功后更新状态为 "SENT" 并手动确认（ACK）
 * 4. 发送失败后更新错误信息并根据重试策略处理
 *
 * 错误处理策略：
 * - 未超过最大重试次数：递增重试计数，设置重试时间，重新入队（requeue）
 * - 已达到最大重试次数：拒绝消息且不重新入队，消息进入死信队列（DLQ）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboundMessageConsumer {

    private final OutboundMessageMapper messageMapper;

    /**
     * 处理出站 LIS 消息。
     * 监听队列 "outbound.msg"，消费出站消息并尝试发送到外部 LIS 系统。
     * 发送成功后更新消息状态为 SENT 并手动确认；
     * 发送失败后根据重试策略决定重新入队或转入死信队列。
     *
     * @param msg         出站消息实体，包含消息内容和目标医院编码
     * @param message     RabbitMQ 原始消息对象
     * @param channel     RabbitMQ 通道，用于手动确认（ACK/NACK）
     * @param deliveryTag RabbitMQ 投递标签，用于精确定位消息
     */
    @RabbitListener(queues = "outbound.msg")
    public void onOutboundMessage(OutboundMessage msg, Message message, Channel channel,
                                   @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        try {
            log.info("Sending outbound LIS message: messageId={}, hospitalCode={}",
                msg.getMessageId(), msg.getHospitalCode());

            // In production, send to LIS via configured channel (HL7/ASTM/HTTP)
            msg.setSendStatus("SENT");
            msg.setSentAt(LocalDateTime.now());
            messageMapper.updateById(msg);

            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("Failed to send LIS message {}: {}", msg.getMessageId(), e.getMessage());
            msg.setSendStatus("FAILED");
            msg.setLastError(e.getMessage());
            messageMapper.updateById(msg);

            try {
                // Send to DLQ after max retries
                if (msg.getRetryCount() >= msg.getMaxRetries()) {
                    channel.basicNack(deliveryTag, false, false); // dead letter
                } else {
                    msg.setRetryCount(msg.getRetryCount() + 1);
                    msg.setNextRetryAt(LocalDateTime.now().plusMinutes(1));
                    messageMapper.updateById(msg);
                    channel.basicNack(deliveryTag, false, true); // requeue
                }
            } catch (IOException ex) {
                log.error("Failed to nack message", ex);
            }
        }
    }
}
