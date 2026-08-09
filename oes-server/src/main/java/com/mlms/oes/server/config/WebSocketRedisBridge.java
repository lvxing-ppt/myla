package com.mlms.oes.server.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/**
 * WebSocket 跨实例 Redis 桥接。
 * <p>
 * 多实例部署时，实例 A 上的 WebSocket 消息通过 Redis Pub/Sub
 * 广播到实例 B、C，各实例再转发给各自连接的 WebSocket 客户端。
 * </p>
 *
 * <h3>用法：</h3>
 * <pre>
 * // 发送跨实例 WebSocket 消息
 * redisTemplate.convertAndSend("oes:ws:broadcast", jsonPayload);
 *
 * // 消息体格式: {"destination":"/topic/instruments","payload":"..."}
 * </pre>
 */
@Slf4j
@RequiredArgsConstructor
public class WebSocketRedisBridge {

    private final SimpMessagingTemplate wsTemplate;
    private final StringRedisTemplate redisTemplate;
    private final RedisMessageListenerContainer listenerContainer;

    private static final String REDIS_CHANNEL = "oes:ws:broadcast";

    /**
     * 应用启动后自动订阅 Redis oes:ws:broadcast 频道。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        try {
            listenerContainer.addMessageListener(new MessageListener() {
            @Override
            public void onMessage(Message message, byte[] pattern) {
                String body = new String(message.getBody());
                try {
                    // 消息格式: {"destination":"/topic/xxx","payload":"..."}
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    var msg = mapper.readValue(body, java.util.Map.class);
                    String destination = (String) msg.get("destination");
                    Object payload = msg.get("payload");
                    wsTemplate.convertAndSend(destination, payload);
                    log.debug("[WS-BRIDGE] forwarded to {} ({} chars)", destination, body.length());
                } catch (Exception e) {
                    log.warn("[WS-BRIDGE] failed to parse message: {}", e.getMessage());
                }
            }
            }, new ChannelTopic(REDIS_CHANNEL));
            log.info("[WS-BRIDGE] subscribed to Redis channel: {}", REDIS_CHANNEL);
        } catch (Exception e) {
            log.warn("[WS-BRIDGE] Redis unavailable, cross-instance WebSocket disabled: {}", e.getMessage());
        }
    }

    /**
     * 发布跨实例 WebSocket 消息。
     * 同时发送到本地 WebSocket + Redis 广播给其他实例。
     */
    public void broadcast(String destination, Object payload) {
        // 1. 本地直接推送
        wsTemplate.convertAndSend(destination, payload);

        // 2. Redis 广播给其他实例
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var msg = java.util.Map.of("destination", destination, "payload", payload);
            redisTemplate.convertAndSend(REDIS_CHANNEL, mapper.writeValueAsString(msg));
        } catch (Exception e) {
            log.warn("[WS-BRIDGE] broadcast failed: {}", e.getMessage());
            // 本地已经推送了，降级可用
        }
    }
}
