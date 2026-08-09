package com.myla.lis.inbound;

import com.myla.sample.entity.Sample;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * LIS 入站消息消费者（业务层侧）。
 * <p>
 * 消费 lis.inbound 队列，接收通讯层发来的 HL7 消息 JSON，
 * HAPI 解析后创建 Sample。
 * </p>
 *
 * <h3>消息格式：</h3>
 * <pre>
 * {"hospitalCode":"DEMO","messageType":"ORM^O01","messageControlId":"MSG001","rawMessage":"MSH|..."}
 * </pre>
 */
@Slf4j
@RequiredArgsConstructor
public class InboundMessageConsumer {

    private final LisInboundServiceImpl inboundService;

    @RabbitListener(queues = "lis.inbound")
    public void onInboundMessage(Map<String, Object> msg) {
        String hospitalCode = (String) msg.get("hospitalCode");
        String messageType = (String) msg.get("messageType");
        String messageControlId = (String) msg.getOrDefault("messageControlId", "");
        String rawMessage = (String) msg.get("rawMessage");

        if (rawMessage == null || rawMessage.isBlank()) {
            log.warn("Empty rawMessage in lis.inbound, msgControlId={}", messageControlId);
            return;
        }

        log.info("[LIS-IN-BIZ] received: hospital={}, type={}, msgId={}", hospitalCode, messageType, messageControlId);

        try {
            byte[] rawBytes = rawMessage.getBytes(StandardCharsets.UTF_8);

            if (messageType != null && (messageType.contains("ORM") || messageType.contains("O01"))) {
                Sample sample = inboundService.receiveOrder(hospitalCode, rawBytes, "HL7");
                log.info("[LIS-IN-BIZ] order created: sampleId={}, barcode={}", sample.getSampleId(), sample.getBarcode());
            } else if (messageType != null && messageType.contains("ADT")) {
                inboundService.receivePatientUpdate(hospitalCode, rawBytes);
                log.info("[LIS-IN-BIZ] ADT processed for hospital={}", hospitalCode);
            } else {
                log.warn("[LIS-IN-BIZ] unknown messageType={}, msgId={}", messageType, messageControlId);
            }
        } catch (Exception e) {
            log.error("[LIS-IN-BIZ] failed to process message from {}: {}", hospitalCode, e.getMessage());
        }
    }
}
