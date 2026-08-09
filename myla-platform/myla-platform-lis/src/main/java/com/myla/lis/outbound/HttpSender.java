package com.myla.lis.outbound;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myla.lis.entity.LisConfig;
import com.myla.lis.entity.OutboundMessage;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * HTTP 发送器。
 * <p>
 * 通过 HTTP POST 将消息以 JSON 格式发送到 LIS 系统。
 * 请求体：{"hospitalCode": "...", "messageType": "...", "messageContent": "..."}
 * 验证 HTTP 200 响应即认为发送成功。
 * </p>
 */
@Slf4j
public class HttpSender implements LisOutboundSender {

    private static final ObjectMapper jsonMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public String getChannelType() {
        return "HTTP";
    }

    @Override
    public SendResult send(OutboundMessage msg, LisConfig config) {
        try {
            String channelCfgJson = config.getOutboundConfig();
            if (channelCfgJson == null || channelCfgJson.isBlank()) {
                return SendResult.fail("outbound_config is empty");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> cfg = jsonMapper.readValue(channelCfgJson, Map.class);
            String url = (String) cfg.get("url");
            if (url == null || url.isBlank()) {
                return SendResult.fail("outbound_config.url is missing");
            }
            int timeout = config.getAckTimeoutSec() != null ? config.getAckTimeoutSec() : 30;

            // 构造 JSON 请求体
            Map<String, String> body = Map.of(
                    "hospitalCode", msg.getHospitalCode(),
                    "messageType", msg.getMessageType(),
                    "messageContent", msg.getMessageContent());
            byte[] bodyBytes = jsonMapper.writeValueAsBytes(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(timeout))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(bodyBytes))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() == 200) {
                log.info("HTTP sent OK: messageId={}, hospital={}, status={}",
                        msg.getMessageId(), msg.getHospitalCode(), response.statusCode());
                return SendResult.ok();
            } else {
                return SendResult.fail("HTTP " + response.statusCode() + ": "
                        + response.body().substring(0, Math.min(200, response.body().length())));
            }
        } catch (Exception e) {
            log.error("HTTP send failed: messageId={}, hospital={}, error={}",
                    msg.getMessageId(), msg.getHospitalCode(), e.getMessage());
            return SendResult.fail(e.getMessage());
        }
    }

    @Override
    public boolean testConnection(LisConfig config) {
        try {
            String channelCfgJson = config.getOutboundConfig();
            if (channelCfgJson == null) return false;
            @SuppressWarnings("unchecked")
            Map<String, Object> cfg = jsonMapper.readValue(channelCfgJson, Map.class);
            String url = (String) cfg.get("url");
            if (url == null) return false;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET().build();
            HttpResponse<Void> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.discarding());
            return response.statusCode() < 500;
        } catch (Exception e) {
            return false;
        }
    }
}
