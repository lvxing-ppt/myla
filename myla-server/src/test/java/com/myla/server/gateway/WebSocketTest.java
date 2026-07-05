package com.myla.server.gateway;

import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket 仪器状态推送功能测试。
 */
public class WebSocketTest {

    private static final CountDownLatch latch = new CountDownLatch(1);

    public static void main(String[] args) throws Exception {
        System.out.println("=== WebSocket Test ===");

        // 1. 连接 WebSocket
        System.out.println("[1] Connecting to ws://localhost:8080/ws ...");
        WebSocketStompClient stompClient = new WebSocketStompClient(
            new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient()))));
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        StompSession session = stompClient.connectAsync("http://localhost:8080/ws",
            new StompSessionHandlerAdapter() {
                @Override
                public void afterConnected(StompSession session, StompHeaders headers) {
                    System.out.println("  ✅ Connected!");
                }
                @Override
                public void handleTransportError(StompSession session, Throwable e) {
                    System.err.println("  ❌ Transport error: " + e.getMessage());
                }
            }).get(5, TimeUnit.SECONDS);
        System.out.println("  Session: " + session.getSessionId());

        // 2. 订阅 /topic/instruments
        System.out.println("[2] Subscribing to /topic/instruments ...");
        session.subscribe("/topic/instruments", new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders headers) { return Map.class; }
            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                System.out.println("  📡 WebSocket PUSH: " + payload);
                latch.countDown();
            }
        });
        System.out.println("  ✅ Subscribed");

        // 3. 触发状态变更：API 手动改为 OFFLINE
        System.out.println("[3] Triggering status change: ONLINE → OFFLINE ...");
        HttpClient http = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:8080/api/v1/instruments/VITEK2-LAB1-001/status"))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString("{\"status\":\"OFFLINE\",\"message\":\"test\"}"))
            .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        System.out.println("  API response: " + resp.statusCode());

        // 4. 等 WebSocket 推送
        System.out.println("[4] Waiting for WebSocket push (max 5s)...");
        boolean received = latch.await(5, TimeUnit.SECONDS);

        // 5. 改回 ONLINE
        req = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:8080/api/v1/instruments/VITEK2-LAB1-001/status"))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString("{\"status\":\"ONLINE\",\"message\":\"restore\"}"))
            .build();
        http.send(req, HttpResponse.BodyHandlers.ofString());

        session.disconnect();
        stompClient.stop();

        System.out.println("\n" + (received ? "✅ WebSocket WORKING" : "❌ WebSocket NOT WORKING"));
        System.exit(received ? 0 : 1);
    }
}
