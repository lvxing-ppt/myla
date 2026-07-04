package com.myla.server.gateway;

import com.myla.common.api.dto.AstResultDTO;
import com.myla.common.api.dto.UnifiedResult;
import com.myla.gateway.core.model.InstrumentStatus;
import com.myla.gateway.core.spi.DataEventListener;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * DataEventListener 实现 —— 结构化日志输出 + 委托 ResultPersistenceService 持久化。
 *
 * <p>职责分离：</p>
 * <ul>
 *   <li>本类：日志输出 + 内存缓存（测试断言）</li>
 *   <li>{@link ResultPersistenceService}：@Transactional DB 写入 + afterCommit MQ 发布</li>
 * </ul>
 */
@Slf4j
public class LoggingDataEventListener implements DataEventListener {

    private final ResultPersistenceService persistenceService;

    /** 已收到的所有 UnifiedResult（线程安全），用于测试断言 */
    private final List<UnifiedResult> receivedResults = new CopyOnWriteArrayList<>();

    /** 解析失败的原始报文列表 */
    private final List<String> parseFailures = new CopyOnWriteArrayList<>();

    /** 最近一次的仪器状态 */
    private volatile InstrumentStatus lastStatus;

    /** 连接错误列表 */
    private final List<String> connectionErrors = new CopyOnWriteArrayList<>();

    public LoggingDataEventListener(ResultPersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    @Override
    public void onResultReceived(UnifiedResult result) {
        receivedResults.add(result);

        // 委托事务性持久化（DB + afterCommit MQ）
        Long orgResultId = persistenceService.saveResult(result);

        // 控制台日志
        log.info("╔══════════════════════════════════════════════╗");
        log.info("║  >>> 收到检验结果 (DB: organism_result.id={})", orgResultId);
        log.info("╠══════════════════════════════════════════════╣");
        log.info("║  仪器ID   : {}", result.getInstrumentId());
        log.info("║  样本条码 : {}", result.getSampleBarcode());
        log.info("║  结果类型 : {}", result.getResultType());
        if (result.getOrganismName() != null) {
            log.info("║  [菌种鉴定] {} (置信度 {}%)", result.getOrganismName(), result.getIdentificationPercent());
        }
        if (result.getAstResults() != null && !result.getAstResults().isEmpty()) {
            log.info("║  [药敏结果] 共 {} 项", result.getAstResults().size());
            for (AstResultDTO ast : result.getAstResults()) {
                log.info("║    {} MIC={} SIR={}", padRight(ast.getAntibioticName(), 20), ast.getMicValue(), ast.getFinalSIR());
            }
        }
        log.info("╚══════════════════════════════════════════════╝");
    }

    @Override
    public void onParseFailed(String rawText, String error) {
        parseFailures.add(rawText);
        log.error("[PARSE-FAIL] error={}, rawText={}", error,
                rawText.length() > 200 ? rawText.substring(0, 200) + "..." : rawText);
    }

    @Override
    public void onStatusChanged(InstrumentStatus status) {
        this.lastStatus = status;
        log.info("[STATUS] instrument={}, status={}, message={}",
                status.getInstrumentId(), status.getStatus(), status.getMessage());
    }

    @Override
    public void onConnectionError(String instrumentId, String error, int consecutiveFailures) {
        connectionErrors.add(instrumentId + ":" + error);
        log.error("[CONN-ERROR] instrument={}, error={}, consecutiveFailures={}",
                instrumentId, error, consecutiveFailures);
    }

    // ==================== 测试辅助 ====================

    public List<UnifiedResult> getReceivedResults() { return Collections.unmodifiableList(receivedResults); }
    public int getResultCount() { return receivedResults.size(); }
    public int getParseFailureCount() { return parseFailures.size(); }
    public InstrumentStatus getLastStatus() { return lastStatus; }

    public void reset() {
        receivedResults.clear();
        parseFailures.clear();
        connectionErrors.clear();
        lastStatus = null;
    }

    private static String padRight(String s, int n) {
        if (s == null) s = "null";
        if (s.length() >= n) return s;
        return s + " ".repeat(n - s.length());
    }
}
