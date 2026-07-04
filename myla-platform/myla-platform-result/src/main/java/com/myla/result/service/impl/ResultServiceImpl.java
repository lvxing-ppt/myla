package com.myla.result.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.myla.common.api.dto.AstResultDTO;
import com.myla.common.api.dto.UnifiedResult;
import com.myla.common.api.event.LabEvent;
import com.myla.common.core.constant.ResultCode;
import com.myla.common.core.exception.BusinessException;
import com.myla.result.entity.AstResult;
import com.myla.result.entity.OrganismResult;
import com.myla.result.mapper.AstResultMapper;
import com.myla.result.mapper.OrganismResultMapper;
import com.myla.result.service.ResultService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResultServiceImpl implements ResultService {

    private final OrganismResultMapper organismResultMapper;
    private final AstResultMapper astResultMapper;
    private final RabbitTemplate rabbitTemplate;

    @Override
    @Transactional
    public OrganismResult saveResult(UnifiedResult unifiedResult) {
        OrganismResult org = new OrganismResult();
        org.setResultId(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        org.setSampleId(findSampleIdByBarcode(unifiedResult.getSampleBarcode()));
        org.setInstrumentId(unifiedResult.getInstrumentId());
        org.setOrganismName(unifiedResult.getOrganismName());
        org.setOrganismCode(unifiedResult.getOrganismCode());
        if (unifiedResult.getIdentificationPercent() != null) {
            org.setIdentificationPercent(BigDecimal.valueOf(unifiedResult.getIdentificationPercent()));
        }
        org.setResultType(unifiedResult.getResultType() != null ? unifiedResult.getResultType().name() : "AST");
        org.setTestTime(unifiedResult.getTestTime());
        org.setReviewStatus("PENDING");
        org.setRawMessage(unifiedResult.getRawMessage());
        organismResultMapper.insert(org);

        // Save AST results
        if (unifiedResult.getAstResults() != null) {
            for (AstResultDTO dto : unifiedResult.getAstResults()) {
                AstResult ast = new AstResult();
                ast.setOrganismResultId(org.getId());
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
                astResultMapper.insert(ast);
            }
        }

        // Publish event
        rabbitTemplate.convertAndSend("myla.workflow", "lab.event", LabEvent.AST_RESULT_RECEIVED);
        log.info("Result saved: resultId={}, instrument={}, organism={}",
            org.getResultId(), org.getInstrumentId(), org.getOrganismName());

        return org;
    }

    @Override
    @Transactional
    public void reviewResult(Long id, String action, String reviewer) {
        OrganismResult result = organismResultMapper.selectById(id);
        if (result == null) {
            throw new BusinessException(ResultCode.RESULT_NOT_FOUND);
        }
        if (!"PENDING".equals(result.getReviewStatus())) {
            throw new BusinessException(ResultCode.INVALID_SAMPLE_STATUS);
        }

        if ("APPROVE".equalsIgnoreCase(action)) {
            result.setReviewStatus("APPROVED");
            rabbitTemplate.convertAndSend("myla.workflow", "lab.event", LabEvent.RESULT_APPROVED);
        } else if ("REJECT".equalsIgnoreCase(action)) {
            result.setReviewStatus("REJECTED");
        } else {
            throw new BusinessException(ResultCode.BAD_REQUEST);
        }

        result.setReviewedBy(reviewer);
        result.setReviewedAt(LocalDateTime.now());
        organismResultMapper.updateById(result);

        log.info("Result reviewed: id={}, action={}, reviewer={}", id, action, reviewer);
    }

    private Long findSampleIdByBarcode(String barcode) {
        // In production, this would query the sample table via SampleMapper.
        // For now, return 0L as a placeholder.
        return 0L;
    }
}
