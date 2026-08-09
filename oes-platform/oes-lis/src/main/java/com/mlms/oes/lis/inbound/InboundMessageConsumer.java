package com.mlms.oes.lis.inbound;

import com.mlms.oes.lis.entity.LisInboundMessage;
import com.mlms.oes.lis.mapper.LisInboundMessageMapper;
import com.mlms.oes.sample.entity.Sample;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * LIS 入站消息消费者（业务层侧）。
 * <p>
 * 消费 lis.inbound 队列，接收通讯层发来的 HL7 消息 JSON，
 * 先存档到 lis_inbound_message 表，再 HAPI 解析创建 Sample。
 * </p>
 */
@Slf4j
public class InboundMessageConsumer {

    private final LisInboundServiceImpl inboundService;
    private final LisInboundMessageMapper inboundMsgMapper;

    public InboundMessageConsumer(LisInboundServiceImpl inboundService,
                                   LisInboundMessageMapper inboundMsgMapper) {
        this.inboundService = inboundService;
        this.inboundMsgMapper = inboundMsgMapper;
    }

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

        // 1. 存档原始报文
        LisInboundMessage entity = new LisInboundMessage();
        entity.setHospitalCode(hospitalCode);
        entity.setMessageType(messageType);
        entity.setMessageControlId(messageControlId);
        entity.setRawContent(rawMessage);
        entity.setProcessStatus("RECEIVED");
        entity.setReceivedAt(LocalDateTime.now());
        inboundMsgMapper.insert(entity);

        log.info("[LIS-IN-BIZ] received: hospital={}, type={}, msgId={}, dbId={}",
                hospitalCode, messageType, messageControlId, entity.getId());

        // 2. 解析并创建 Sample
        try {
            byte[] rawBytes = rawMessage.getBytes(StandardCharsets.UTF_8);

            if (messageType != null && (messageType.contains("ORM") || messageType.contains("O01"))) {
                Sample sample = inboundService.receiveOrder(hospitalCode, rawBytes, "HL7");
                entity.setProcessStatus("PROCESSED");
                entity.setSampleId(sample.getId());
                log.info("[LIS-IN-BIZ] order created: sampleId={}, barcode={}", sample.getSampleId(), sample.getBarcode());
            } else if (messageType != null && messageType.contains("ADT")) {
                inboundService.receivePatientUpdate(hospitalCode, rawBytes);
                entity.setProcessStatus("PROCESSED");
                log.info("[LIS-IN-BIZ] ADT processed for hospital={}", hospitalCode);
            } else {
                entity.setProcessStatus("FAILED");
                entity.setErrorMsg("Unknown messageType: " + messageType);
                log.warn("[LIS-IN-BIZ] unknown messageType={}, msgId={}", messageType, messageControlId);
            }
        } catch (Exception e) {
            entity.setProcessStatus("FAILED");
            entity.setErrorMsg(e.getMessage());
            log.error("[LIS-IN-BIZ] failed: hospital={}, error={}", hospitalCode, e.getMessage());
        }

        // 3. 更新处理状态
        inboundMsgMapper.updateById(entity);
    }
}
