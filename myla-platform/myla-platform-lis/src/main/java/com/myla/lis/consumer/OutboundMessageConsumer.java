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

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboundMessageConsumer {

    private final OutboundMessageMapper messageMapper;

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
