package com.myla.result.consumer;

import com.myla.common.api.dto.UnifiedResult;
import com.myla.result.service.ResultService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class ResultParsedConsumer {

    private final ResultService resultService;

    @RabbitListener(queues = "result.parsed")
    public void onResultParsed(UnifiedResult result, Message message, Channel channel,
                                @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        try {
            log.info("Received parsed result from instrument: {}", result.getInstrumentId());
            resultService.saveResult(result);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("Failed to process parsed result: {}", e.getMessage(), e);
            try {
                channel.basicNack(deliveryTag, false, true);
            } catch (IOException ex) {
                log.error("Failed to nack message", ex);
            }
        }
    }
}
