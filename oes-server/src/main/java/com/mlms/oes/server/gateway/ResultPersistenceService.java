package com.mlms.oes.server.gateway;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mlms.oes.common.api.dto.AstResultDTO;
import com.mlms.oes.common.api.dto.UnifiedResult;
import com.mlms.oes.result.entity.AstResult;
import com.mlms.oes.result.entity.OrganismResult;
import com.mlms.oes.result.mapper.AstResultMapper;
import com.mlms.oes.result.mapper.OrganismResultMapper;
import com.mlms.oes.sample.entity.Sample;
import com.mlms.oes.sample.mapper.SampleMapper;
import com.mlms.oes.workflow.model.LabEventMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * 结果持久化服务（MQ 消费者）。
 * <p>
 * 消费 result.parsed 队列（通讯层 InstrumentResultPublisher 发布），
 * 在事务中完成 organism_result + ast_result 的写入，
 * 事务提交后通过 afterCommit 发布工作流事件。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResultPersistenceService {

    private final OrganismResultMapper organismResultMapper;
    private final AstResultMapper astResultMapper;
    private final SampleMapper sampleMapper;
    private final RabbitTemplate rabbitTemplate;
    private final StringRedisTemplate redisTemplate;

    private static final String OUTBOX_KEY = "myla:outbox:workflow";

    /**
     * 消费通讯层发布的 UnifiedResult，事务性保存到 DB。
     */
    @RabbitListener(queues = "result.parsed")
    public void onResultParsed(UnifiedResult result) {
        log.info("[PERSIST] received from result.parsed: instrument={}, organism={}",
                result.getInstrumentId(), result.getOrganismName());
        Long orgResultId = saveResult(result);
        log.info("[PERSIST] saved organism_result.id={}, organism={}", orgResultId, result.getOrganismName());
    }

    @Transactional
    public Long saveResult(UnifiedResult result) {
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

        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publishWorkflowEvent(orgResultId);
                }
            });

        return orgResultId;
    }

    private void publishWorkflowEvent(Long orgResultId) {
        LabEventMessage msg = new LabEventMessage("AST_RESULT_RECEIVED", orgResultId);
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                rabbitTemplate.convertAndSend("myla.workflow", "lab.event", msg);
                return;
            } catch (Exception e) {
                log.warn("[WORKFLOW] publish attempt {}/3 failed: {}", attempt, e.getMessage());
                if (attempt < 3) {
                    try { Thread.sleep(1000L * attempt); } catch (InterruptedException ignored) {}
                }
            }
        }
        log.error("[WORKFLOW] all 3 attempts failed for orgResultId={}, marking for retry", orgResultId);
        markForRetry(orgResultId);
    }

    private void markForRetry(Long orgResultId) {
        try {
            redisTemplate.opsForSet().add(OUTBOX_KEY, orgResultId.toString());
        } catch (Exception e) {
            log.error("[WORKFLOW] failed to write outbox for orgResultId={}: {}", orgResultId, e.getMessage());
        }
    }

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
                    redisTemplate.opsForSet().remove(OUTBOX_KEY, idStr);
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
