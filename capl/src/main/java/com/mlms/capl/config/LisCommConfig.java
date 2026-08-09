package com.mlms.capl.config;

import com.mlms.capl.bootstrap.LisInboundServerStarter.LisCorrelationData;
import com.mlms.capl.bootstrap.LisInboundServerStarter;
import com.mlms.capl.bootstrap.FailoverBuffer;
import com.mlms.capl.outbound.AstmTcpSender;
import com.mlms.capl.outbound.Hl7MllpSender;
import com.mlms.capl.outbound.HttpSender;
import com.mlms.capl.outbound.LisOutboundSender;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * LIS 通讯层 RabbitMQ + Bean 配置。
 */
@Slf4j
@Configuration
public class LisCommConfig {

    // ==================== MQ ====================

    @Bean
    public Jackson2JsonMessageConverter commJsonConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate commRabbitTemplate(ConnectionFactory connectionFactory,
                                              Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        // Publisher confirms：异步感知消息是否到达 MQ broker。
        // 普通 NACK：记录日志告警。
        // LisCorrelationData NACK：携带完整消息体 + FailoverBuffer 引用，自动落地。
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                log.error("[CAPL-MQ] publisher confirm NACK: correlationId={}, cause={}",
                        correlationData != null ? correlationData.getId() : "null", cause);
                if (correlationData instanceof LisCorrelationData lcd) {
                    log.warn("[CAPL-MQ] NACK recovery: buffering to failover, msgId={}", lcd.getId());
                    lcd.failoverBuffer.buffer(lcd.hospitalCode, lcd.hl7, lcd.getId(), lcd.msgType);
                }
            }
        });
        return template;
    }

    // ==================== Beans ====================

    @Bean
    public LisInboundServerStarter lisInboundServerStarter(RabbitTemplate commRabbitTemplate,
                                                            StringRedisTemplate stringRedisTemplate) {
        return new LisInboundServerStarter(commRabbitTemplate, stringRedisTemplate);
    }

    @Bean
    public LisOutboundSender hl7MllpSender() { return new Hl7MllpSender(); }

    @Bean
    public LisOutboundSender astmTcpSender() { return new AstmTcpSender(); }

    @Bean
    public LisOutboundSender httpSender() { return new HttpSender(); }
}
