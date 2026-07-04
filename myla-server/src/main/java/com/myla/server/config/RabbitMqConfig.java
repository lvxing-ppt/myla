package com.myla.server.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    // ==================== Exchanges ====================

    @Bean
    public TopicExchange instrumentExchange() {
        return new TopicExchange("myla.instrument");
    }

    @Bean
    public TopicExchange workflowExchange() {
        return new TopicExchange("myla.workflow");
    }

    @Bean
    public TopicExchange lisExchange() {
        return new TopicExchange("myla.lis");
    }

    @Bean
    public TopicExchange notificationExchange() {
        return new TopicExchange("myla.notification");
    }

    @Bean
    public TopicExchange reportExchange() {
        return new TopicExchange("myla.report");
    }

    @Bean
    public TopicExchange systemExchange() {
        return new TopicExchange("myla.system");
    }

    // ==================== Queues ====================

    @Bean
    public Queue rawMessageQueue() {
        return QueueBuilder.durable("raw.message").build();
    }

    @Bean
    public Queue resultParsedQueue() {
        return QueueBuilder.durable("result.parsed").build();
    }

    @Bean
    public Queue instrumentTelemetryQueue() {
        return QueueBuilder.durable("instrument.telemetry").build();
    }

    @Bean
    public Queue labEventQueue() {
        return QueueBuilder.durable("lab.event").build();
    }

    @Bean
    public Queue outboundMsgQueue() {
        return QueueBuilder.durable("outbound.msg")
                .withArgument("x-dead-letter-exchange", "myla.lis")
                .withArgument("x-dead-letter-routing-key", "outbound.dlq")
                .build();
    }

    @Bean
    public Queue outboundDlqQueue() {
        return QueueBuilder.durable("outbound.dlq").build();
    }

    @Bean
    public Queue notifySmsQueue() {
        return QueueBuilder.durable("notify.sms").build();
    }

    @Bean
    public Queue notifyEmailQueue() {
        return QueueBuilder.durable("notify.email").build();
    }

    @Bean
    public Queue reportGenQueue() {
        return QueueBuilder.durable("report.gen").build();
    }

    @Bean
    public Queue auditWriteQueue() {
        return QueueBuilder.durable("audit.write").build();
    }

    // ==================== Bindings ====================

    @Bean
    public Binding rawMessageBinding() {
        return BindingBuilder.bind(rawMessageQueue()).to(instrumentExchange()).with("raw.message");
    }

    @Bean
    public Binding resultParsedBinding() {
        return BindingBuilder.bind(resultParsedQueue()).to(instrumentExchange()).with("result.parsed");
    }

    @Bean
    public Binding instrumentTelemetryBinding() {
        return BindingBuilder.bind(instrumentTelemetryQueue()).to(instrumentExchange()).with("instrument.telemetry");
    }

    @Bean
    public Binding labEventBinding() {
        return BindingBuilder.bind(labEventQueue()).to(workflowExchange()).with("lab.event");
    }

    @Bean
    public Binding outboundMsgBinding() {
        return BindingBuilder.bind(outboundMsgQueue()).to(lisExchange()).with("outbound.msg");
    }

    @Bean
    public Binding outboundDlqBinding() {
        return BindingBuilder.bind(outboundDlqQueue()).to(lisExchange()).with("outbound.dlq");
    }

    @Bean
    public Binding notifySmsBinding() {
        return BindingBuilder.bind(notifySmsQueue()).to(notificationExchange()).with("notify.sms");
    }

    @Bean
    public Binding notifyEmailBinding() {
        return BindingBuilder.bind(notifyEmailQueue()).to(notificationExchange()).with("notify.email");
    }

    @Bean
    public Binding reportGenBinding() {
        return BindingBuilder.bind(reportGenQueue()).to(reportExchange()).with("report.gen");
    }

    @Bean
    public Binding auditWriteBinding() {
        return BindingBuilder.bind(auditWriteQueue()).to(systemExchange()).with("audit.write");
    }

    // ==================== Converter ====================

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                // Publisher confirm failed — handle in production
            }
        });
        return template;
    }
}
