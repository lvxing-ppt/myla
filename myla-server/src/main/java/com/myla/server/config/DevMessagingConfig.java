package com.myla.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Dev 环境模拟消息中间件配置。
 * 当 RabbitMQ 和 Redis 不可用时，提供空操作的替代 Bean，
 * 确保应用在本地开发环境（无 Docker）下能够正常启动。
 */
@Configuration
@Profile("dev")
public class DevMessagingConfig {

    private static final Logger log = LoggerFactory.getLogger(DevMessagingConfig.class);

    /**
     * 提供模拟的 RabbitMQ ConnectionFactory。
     * 仅在真正的 ConnectionFactory 不存在时创建。
     * 使用本地地址，但不会真正尝试连接（lazy connect）。
     */
    @Bean
    @ConditionalOnMissingBean(ConnectionFactory.class)
    public ConnectionFactory devConnectionFactory() {
        log.warn(">>> DEV MODE: Using mock RabbitMQ ConnectionFactory. Messages will NOT be sent. <<<");
        CachingConnectionFactory factory = new CachingConnectionFactory("localhost");
        factory.setConnectionTimeout(1);
        return factory;
    }

    /**
     * 提供模拟的 RabbitTemplate。
     * 允许所有依赖 RabbitTemplate 的 Service 正常注入，但消息发送操作将被静默忽略。
     */
    @Bean
    @ConditionalOnMissingBean(RabbitTemplate.class)
    public RabbitTemplate devRabbitTemplate(ConnectionFactory cf) {
        log.warn(">>> DEV MODE: Using mock RabbitTemplate. All send operations are NO-OP. <<<");
        return new RabbitTemplate(cf) {
            @Override
            public void convertAndSend(String exchange, String routingKey, Object message) {
                log.debug("[DEV-NOOP] convertAndSend: exchange={}, routingKey={}, message={}", exchange, routingKey, message);
            }
        };
    }
}
