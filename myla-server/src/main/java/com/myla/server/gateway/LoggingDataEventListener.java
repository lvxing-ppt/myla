package com.myla.server.gateway;

import com.myla.common.api.dto.AstResultDTO;
import com.myla.common.api.dto.UnifiedResult;
import com.myla.gateway.core.model.InstrumentStatus;
import com.myla.gateway.core.spi.DataEventListener;
import com.myla.result.entity.AstResult;
import com.myla.result.entity.OrganismResult;
import com.myla.result.mapper.AstResultMapper;
import com.myla.result.mapper.OrganismResultMapper;
import com.myla.workflow.model.LabEventMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * DataEventListener 实现 —— 结构化日志输出 + 数据库持久化 + 触发工作流。
 * <p>
 * 收到解析结果后：
 * <ol>
 *   <li>输出盒式日志到控制台</li>
 *   <li>写入 organism_result 表（菌种鉴定结果）</li>
 *   <li>写入 ast_result 表（药敏明细）</li>
 *   <li>发布 AST_RESULT_RECEIVED 事件到 MQ，触发工作流规则引擎</li>
 *   <li>缓存到内存列表（供测试断言）</li>
 * </ol>
 * </p>
 *
 * @author MyLA Team
 */
@Slf4j
public class LoggingDataEventListener implements DataEventListener {

    private final OrganismResultMapper organismResultMapper;
    private final AstResultMapper astResultMapper;
    private final RabbitTemplate rabbitTemplate;

    /** 已收到的所有 UnifiedResult（线程安全），用于测试断言 */
    private final List<UnifiedResult> receivedResults = new CopyOnWriteArrayList<>();

    /** 解析失败的原始报文列表 */
    private final List<String> parseFailures = new CopyOnWriteArrayList<>();

    /** 最近一次的仪器状态 */
    private volatile InstrumentStatus lastStatus;

    /** 连接错误列表 */
    private final List<String> connectionErrors = new CopyOnWriteArrayList<>();

    public LoggingDataEventListener(OrganismResultMapper organismResultMapper,
                                    AstResultMapper astResultMapper,
                                    RabbitTemplate rabbitTemplate) {
        this.organismResultMapper = organismResultMapper;
        this.astResultMapper = astResultMapper;
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 成功解析到检验结果 —— 打印日志 + 写库。
     */
    @Override
    public void onResultReceived(UnifiedResult result) {
        receivedResults.add(result);

        // 1. 写 organism_result 表
        OrganismResult orgResult = new OrganismResult();
        orgResult.setResultId(UUID.randomUUID().toString().substring(0, 12));
        orgResult.setSampleId(null);  // 样本关联由后续流程处理
        orgResult.setInstrumentId(result.getInstrumentId());
        orgResult.setOrganismCode(result.getOrganismCode());
        orgResult.setOrganismName(result.getOrganismName());
        orgResult.setIdentificationPercent(
                result.getIdentificationPercent() != null
                    ? BigDecimal.valueOf(result.getIdentificationPercent()) : null);
        orgResult.setResultType(result.getResultType() != null ? result.getResultType().name() : null);
        orgResult.setTestTime(result.getTestTime());
        orgResult.setReviewStatus("PENDING");
        orgResult.setRawMessage(truncate(result.getRawMessage(), 4000));
        orgResult.setCreatedAt(LocalDateTime.now());
        orgResult.setUpdatedAt(LocalDateTime.now());
        organismResultMapper.insert(orgResult);

        // 2. 写 ast_result 表
        if (result.getAstResults() != null && !result.getAstResults().isEmpty()) {
            for (AstResultDTO dto : result.getAstResults()) {
                AstResult ast = new AstResult();
                ast.setOrganismResultId(orgResult.getId());
                ast.setAntibioticCode(dto.getAntibioticCode());
                ast.setAntibioticName(dto.getAntibioticName());
                ast.setMicValue(
                    dto.getMicValue() != null
                        ? BigDecimal.valueOf(dto.getMicValue()) : null);
                ast.setMicUnit(dto.getMicUnit());
                ast.setMachineSir(dto.getMachineSIR());
                ast.setManualSir(dto.getManualSIR());
                ast.setFinalSir(dto.getFinalSIR());
                ast.setExpertRuleComment(dto.getExpertRuleComment());
                ast.setIsCorrected(0);
                ast.setCreatedAt(LocalDateTime.now());
                astResultMapper.insert(ast);
            }
        }

        // 3. 发布事件到工作流引擎
        try {
            LabEventMessage eventMsg = new LabEventMessage("AST_RESULT_RECEIVED", orgResult.getId());
            rabbitTemplate.convertAndSend("myla.workflow", "lab.event", eventMsg);
            log.debug("[WORKFLOW] event published: AST_RESULT_RECEIVED, orgResultId={}", orgResult.getId());
        } catch (Exception e) {
            log.warn("[WORKFLOW] failed to publish event: {}", e.getMessage());
        }

        // 4. 控制台日志
        log.info("╔══════════════════════════════════════════════╗");
        log.info("║  >>> 收到检验结果 (DB: organism_result.id={})", orgResult.getId());
        log.info("╠══════════════════════════════════════════════╣");
        log.info("║  仪器ID   : {}", result.getInstrumentId());
        log.info("║  样本条码 : {}", result.getSampleBarcode());
        log.info("║  结果类型 : {}", result.getResultType());
        log.info("╠══════════════════════════════════════════════╣");

        if (result.getOrganismName() != null) {
            log.info("║  [菌种鉴定] {} (置信度 {}%)", result.getOrganismName(), result.getIdentificationPercent());
        }

        if (result.getAstResults() != null && !result.getAstResults().isEmpty()) {
            log.info("║  [药敏结果] 共 {} 项", result.getAstResults().size());
            for (AstResultDTO ast : result.getAstResults()) {
                log.info("║    {} MIC={} SIR={}",
                        padRight(ast.getAntibioticName(), 20), ast.getMicValue(), ast.getFinalSIR());
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

    // ==================== 辅助 ====================

    public List<UnifiedResult> getReceivedResults() {
        return Collections.unmodifiableList(receivedResults);
    }

    public int getResultCount() {
        return receivedResults.size();
    }

    public int getParseFailureCount() {
        return parseFailures.size();
    }

    public InstrumentStatus getLastStatus() {
        return lastStatus;
    }

    public void reset() {
        receivedResults.clear();
        parseFailures.clear();
        connectionErrors.clear();
        lastStatus = null;
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }

    private static String padRight(String s, int n) {
        if (s == null) s = "null";
        if (s.length() >= n) return s;
        return s + " ".repeat(n - s.length());
    }
}
