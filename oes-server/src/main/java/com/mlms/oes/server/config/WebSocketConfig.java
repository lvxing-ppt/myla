package com.mlms.oes.server.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket 配置 — STOMP over WebSocket + Redis 跨实例广播。
 * <p>
 * 多实例部署时，通过 Redis Pub/Sub 将 WebSocket 消息广播到所有实例，
 * 确保无论前端连到哪个实例，都能收到推送。
 * </p>
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*").withSockJS();
    }

    /** Redis Pub/Sub 监听容器 */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        return container;
    }

    /** WebSocket 跨实例 Redis 桥接 */
    @Bean
    public WebSocketRedisBridge webSocketRedisBridge(
            SimpMessagingTemplate wsTemplate,
            StringRedisTemplate redisTemplate,
            RedisMessageListenerContainer listenerContainer) {
        return new WebSocketRedisBridge(wsTemplate, redisTemplate, listenerContainer);
    }

}
