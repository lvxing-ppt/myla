package com.mlms.oes.result.consumer;

import com.mlms.oes.common.api.dto.UnifiedResult;
import com.mlms.oes.result.service.ResultService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * MLMS 系统解析结果消费者。
 * 负责监听 RabbitMQ 解析结果队列，处理仪器上报的已解析检验结果。
 *
 * 消息处理流程：
 * 1. 从队列 "result.parsed" 消费统一解析结果（UnifiedResult）
 * 2. 委托 ResultService 将结果持久化到数据库（包括细菌鉴定结果和药敏结果）
 * 3. 保存成功后手动确认（ACK）
 * 4. 保存失败时手动拒绝并重新入队（NACK + requeue）
 *
 * 错误处理策略：
 * 处理失败时执行 basicNack 并设置 requeue=true，将消息重新放回队列等待重试。
 * 注意：当前策略为无限重试，生产环境建议增加重试计数和死信队列机制。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResultParsedConsumer {

    private final ResultService resultService;

    /**
     * 处理仪器上报的已解析检验结果。
     * 监听队列 "result.parsed"，将统一格式的解析结果（包含细菌鉴定和药敏数据）
     * 保存到数据库。处理成功后手动确认消息；处理失败时拒绝消息并重新入队。
     *
     * @param result      统一格式的检验结果，包含仪器ID、样本条码、细菌信息和药敏结果
     * @param message     RabbitMQ 原始消息对象
     * @param channel     RabbitMQ 通道，用于手动确认（ACK/NACK）
     * @param deliveryTag RabbitMQ 投递标签，用于精确定位消息
     */
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
