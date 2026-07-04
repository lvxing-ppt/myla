package com.myla.server.gateway;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.myla.common.api.dto.AstResultDTO;
import com.myla.common.api.dto.UnifiedResult;
import com.myla.result.entity.AstResult;
import com.myla.result.entity.OrganismResult;
import com.myla.result.mapper.AstResultMapper;
import com.myla.result.mapper.OrganismResultMapper;
import com.myla.sample.entity.Sample;
import com.myla.sample.mapper.SampleMapper;
import com.myla.workflow.model.LabEventMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * 结果持久化服务。
 * <p>
 * 在事务中完成 organism_result + ast_result 的写入，
 * 事务提交后通过 afterCommit 发布 MQ 事件。
 * 如果 MQ 发布失败，写 outbox 表（raw_message.parse_status='PUBLISH_FAILED'）供定时补发。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResultPersistenceService {

    private final OrganismResultMapper organismResultMapper;
    private final AstResultMapper astResultMapper;
    private final SampleMapper sampleMapper;
    private final RabbitTemplate rabbitTemplate;
    private final StringRedisTemplate redisTemplate;

    private static final String OUTBOX_KEY = "myla:outbox:workflow";

    /**
     * 事务性保存仪器结果。
     * DB 写入成功 + 事务提交后 → MQ 发布工作流事件。
     *
     * @return 保存后的 organism_result.id
     */
    @Transactional
    public Long saveResult(UnifiedResult result) {
        // 1. 写 organism_result
        OrganismResult orgResult = new OrganismResult();
        orgResult.setResultId(UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        orgResult.setSampleId(findSampleIdByBarcode(result.getSampleBarcode()));
        orgResult.setInstrumentId(result.getInstrumentId());
        orgResult.setOrganismCode(result.getOrganismCode());
        orgResult.setOrganismName(result.getOrganismName());
        if (result.getIdentificationPercent() != null) {
            orgResult.setIdentificationPercent(BigDecimal.valueOf(result.getIdentificationPercent()));
        }
        orgResult.setResultType(result.getResultType() != null ? result.getResultType().name() : null);
        orgResult.setTestTime(result.getTestTime());
        orgResult.setReviewStatus("PENDING");
        orgResult.setRawMessage(truncate(result.getRawMessage(), 4000));
        orgResult.setCreatedAt(LocalDateTime.now());
        orgResult.setUpdatedAt(LocalDateTime.now());
        organismResultMapper.insert(orgResult);

        // 2. 写 ast_result
        if (result.getAstResults() != null) {
            for (AstResultDTO dto : result.getAstResults()) {
                AstResult ast = new AstResult();
                ast.setOrganismResultId(orgResult.getId());
                ast.setAntibioticCode(dto.getAntibioticCode());
                ast.setAntibioticName(dto.getAntibioticName());
                if (dto.getMicValue() != null) {
                    ast.setMicValue(BigDecimal.valueOf(dto.getMicValue()));
                }
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

        Long orgResultId = orgResult.getId();

        // 3. 事务提交后 → MQ 发布（保证 DB 已持久化）
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publishWorkflowEvent(orgResultId);
                }
            });

        log.info("[PERSIST] saved organism_result.id={}, organism={}", orgResultId, result.getOrganismName());
        return orgResultId;
    }

    /** MQ 发布 + 重试 + 失败降级 */
    private void publishWorkflowEvent(Long orgResultId) {
        LabEventMessage msg = new LabEventMessage("AST_RESULT_RECEIVED", orgResultId);
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                rabbitTemplate.convertAndSend("myla.workflow", "lab.event", msg);
                log.debug("[WORKFLOW] event published: AST_RESULT_RECEIVED, orgResultId={}", orgResultId);
                return;
            } catch (Exception e) {
                log.warn("[WORKFLOW] publish attempt {}/3 failed: {}", attempt, e.getMessage());
                if (attempt < 3) {
                    try { Thread.sleep(1000L * attempt); } catch (InterruptedException ignored) {}
                }
            }
        }
        // 3 次都失败 → 写 outbox 标记，定时任务补发
        log.error("[WORKFLOW] all 3 attempts failed for orgResultId={}, marking for retry", orgResultId);
        markForRetry(orgResultId);
    }

    /** 写入 Redis outbox 集合，等待定时任务补发 */
    private void markForRetry(Long orgResultId) {
        try {
            redisTemplate.opsForSet().add(OUTBOX_KEY, orgResultId.toString());
            log.warn("[WORKFLOW] orgResultId={} added to outbox for retry", orgResultId);
        } catch (Exception e) {
            log.error("[WORKFLOW] failed to write outbox for orgResultId={}: {}", orgResultId, e.getMessage());
        }
    }

    /** 每 30 秒扫描 outbox，补发失败的工作流事件 */
    @Scheduled(fixedDelay = 30_000)
    public void retryOutboxMessages() {
        try {
            Set<String> failedIds = redisTemplate.opsForSet().members(OUTBOX_KEY);
            if (failedIds == null || failedIds.isEmpty()) return;

            for (String idStr : failedIds) {
                Long orgResultId = Long.valueOf(idStr);
                try {
                    LabEventMessage msg = new LabEventMessage("AST_RESULT_RECEIVED", orgResultId);
                    rabbitTemplate.convertAndSend("myla.workflow", "lab.event", msg);
                    // 成功 → 从 outbox 移除
                    redisTemplate.opsForSet().remove(OUTBOX_KEY, idStr);
                    log.info("[WORKFLOW] outbox retry success: orgResultId={}", orgResultId);
                } catch (Exception e) {
                    log.warn("[WORKFLOW] outbox retry still failing for orgResultId={}: {}", orgResultId, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("[WORKFLOW] outbox scan failed: {}", e.getMessage());
        }
    }

    private Long findSampleIdByBarcode(String barcode) {
        if (barcode == null || barcode.isBlank()) return null;
        Sample sample = sampleMapper.selectOne(
            new LambdaQueryWrapper<Sample>().eq(Sample::getBarcode, barcode));
        return sample != null ? sample.getId() : null;
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}
