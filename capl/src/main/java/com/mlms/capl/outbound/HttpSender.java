package com.mlms.capl.outbound;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * HTTP 发送器（通讯层侧）。
 */
@Slf4j
public class HttpSender implements LisOutboundSender {

    private static final ObjectMapper jsonMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public String getChannelType() { return "HTTP"; }

    @Override
    public SendResult send(Map<String, Object> channelConfig, String messageContent, String hospitalCode) {
        try {
            String url = (String) channelConfig.get("url");
            if (url == null || url.isBlank()) {
                return SendResult.fail("channelConfig.url is missing");
            }
            int ackTimeoutSec = channelConfig.get("ackTimeoutSec") instanceof Number n ? n.intValue() : 30;

            Map<String, String> body = Map.of(
                    "hospitalCode", hospitalCode,
                    "messageType", "RESULT",
                    "messageContent", messageContent);
            byte[] bodyBytes = jsonMapper.writeValueAsBytes(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(ackTimeoutSec))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(bodyBytes))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() == 200) {
                log.info("HTTP sent OK: hospital={}, status={}", hospitalCode, response.statusCode());
                return SendResult.ok();
            } else {
                return SendResult.fail("HTTP " + response.statusCode() + ": "
                        + response.body().substring(0, Math.min(200, response.body().length())));
            }
        } catch (Exception e) {
            log.error("HTTP send failed: hospital={}, error={}", hospitalCode, e.getMessage());
            return SendResult.fail(e.getMessage());
        }
    }
}
