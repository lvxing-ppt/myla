package com.myla.server.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket 配置 — STOMP over WebSocket。
 * <p>前端订阅 /topic/instruments 接收仪器状态实时推送。</p>
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 前端订阅的目标前缀
        registry.enableSimpleBroker("/topic");
        // 前端发送消息到服务器的目标前缀
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // WebSocket 连接端点，前端通过这个 URL 连接
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*").withSockJS();
    }
}
