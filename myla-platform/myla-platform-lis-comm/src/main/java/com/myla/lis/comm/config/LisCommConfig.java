package com.myla.lis.comm.config;

import com.myla.lis.comm.bootstrap.LisInboundServerStarter;
import com.myla.lis.comm.outbound.AstmTcpSender;
import com.myla.lis.comm.outbound.Hl7MllpSender;
import com.myla.lis.comm.outbound.HttpSender;
import com.myla.lis.comm.outbound.LisOutboundSender;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LIS 通讯层 RabbitMQ + Bean 配置。
 */
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
        return template;
    }

    // ==================== Beans ====================

    @Bean
    public LisInboundServerStarter lisInboundServerStarter(RabbitTemplate commRabbitTemplate) {
        return new LisInboundServerStarter(commRabbitTemplate);
    }

    @Bean
    public LisOutboundSender hl7MllpSender() { return new Hl7MllpSender(); }

    @Bean
    public LisOutboundSender astmTcpSender() { return new AstmTcpSender(); }

    @Bean
    public LisOutboundSender httpSender() { return new HttpSender(); }
}
