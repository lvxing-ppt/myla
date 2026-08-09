package com.myla.lis.comm.bootstrap;

import com.myla.common.api.dto.UnifiedResult;
import com.myla.gateway.core.model.InstrumentStatus;
import com.myla.gateway.core.spi.DataEventListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * 仪器结果 MQ 发布器。
 * <p>
 * 替代 LoggingDataEventListener — 收到 UnifiedResult 后直接发 MQ，
 * 不再委托 ResultPersistenceService。业务层从 result.parsed 消费并持久化。
 * </p>
 */
@Slf4j
@RequiredArgsConstructor
public class InstrumentResultPublisher implements DataEventListener {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void onResultReceived(UnifiedResult result) {
        rabbitTemplate.convertAndSend("myla.instrument", "result.parsed", result);
        log.info("[PUBLISH] result -> result.parsed: instrument={}, organism={}, barcode={}",
                result.getInstrumentId(), result.getOrganismName(), result.getSampleBarcode());
    }

    @Override
    public void onParseFailed(String rawText, String error) {
        log.error("[PARSE-FAIL] error={}, rawText={}",
                error, rawText != null && rawText.length() > 200 ? rawText.substring(0, 200) + "..." : rawText);
    }

    @Override
    public void onStatusChanged(InstrumentStatus status) {
        log.info("[STATUS] instrument={}, status={}, message={}",
                status.getInstrumentId(), status.getStatus(), status.getMessage());
    }

    @Override
    public void onConnectionError(String instrumentId, String error, int consecutiveFailures) {
        log.error("[CONN-ERROR] instrument={}, error={}, consecutiveFailures={}",
                instrumentId, error, consecutiveFailures);
    }
}
