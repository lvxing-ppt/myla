package com.mlms.oes.result.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mlms.oes.common.api.dto.AstResultDTO;
import com.mlms.oes.common.api.dto.UnifiedResult;
import com.mlms.oes.common.api.event.LabEvent;
import com.mlms.oes.common.core.constant.ResultCode;
import com.mlms.oes.common.core.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mlms.oes.result.entity.AstResult;
import com.mlms.oes.result.entity.OrganismResult;
import com.mlms.oes.result.mapper.AstResultMapper;
import com.mlms.oes.result.mapper.OrganismResultMapper;
import com.mlms.oes.result.service.ResultService;
import com.mlms.oes.sample.entity.Sample;
import com.mlms.oes.sample.mapper.SampleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * MLMS 系统检验结果服务实现类。
 * 实现检验结果的保存和审核业务逻辑。
 *
 * 保存结果流程（事务性操作）：
 * 1. 生成唯一 resultId（UUID 前16位）
 * 2. 根据样本条码查找样本ID
 * 3. 构建并保存细菌鉴定结果（OrganismResult），默认审核状态为 PENDING
 * 4. 遍历药敏结果列表，逐条构建并保存 AST 药敏结果
 * 5. 通过 RabbitMQ 发布 "AST_RESULT_RECEIVED" 领域事件到工作流模块（路由键：lab.event，交换机：myla.workflow）
 *
 * 审核结果流程（事务性操作）：
 * 1. 根据ID查询检验结果，不存在则抛出异常
 * 2. 校验当前状态必须为 PENDING
 * 3. 根据审核动作更新状态（APPROVE -> APPROVED, REJECT -> REJECTED）
 * 4. 批准时发布 "RESULT_APPROVED" 领域事件
 * 5. 记录审核人和审核时间
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResultServiceImpl implements ResultService {

    private final OrganismResultMapper organismResultMapper;
    private final AstResultMapper astResultMapper;
    private final RabbitTemplate rabbitTemplate;
    private final SampleMapper sampleMapper;

    /**
     * 保存仪器解析后的统一检验结果。
     * 在事务中完成细菌鉴定结果和药敏结果的持久化，
     * 并发布领域事件通知工作流模块进行后续处理（如危急值判定、专家规则触发等）。
     *
     * @param unifiedResult 统一格式的检验结果，包含仪器ID、细菌信息、药敏数据和原始消息
     * @return 保存后的细菌鉴定结果实体
     */
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

    /**
     * 审核检验结果。
     * 对指定 ID 的检验结果执行审核操作。
     * 业务规则：
     * - 仅 PENDING 状态的结果可被审核
     * - APPROVE 操作将状态更新为 APPROVED 并发布 RESULT_APPROVED 事件
     * - REJECT 操作将状态更新为 REJECTED
     * - 传入无效动作时抛出 BAD_REQUEST 异常
     *
     * @param id       检验结果主键ID
     * @param action   审核动作：APPROVE-批准 / REJECT-拒绝
     * @param reviewer 审核人用户名
     * @throws BusinessException 当结果不存在、状态不是 PENDING 或审核动作无效时抛出
     */
    /**
     * 三级审核状态机：
     * <pre>
     * PENDING →(一级技术审核) TECH_APPROVED / REJECTED
     * TECH_APPROVED →(二级临床审核) CLINICAL_APPROVED / REJECTED
     * CLINICAL_APPROVED →(三级终审) RELEASED / REJECTED
     * </pre>
     */
    @Override
    @Transactional
    public void reviewResult(Long id, String action, String reviewer, String reviewerRole) {
        OrganismResult result = organismResultMapper.selectById(id);
        if (result == null) throw new BusinessException(ResultCode.RESULT_NOT_FOUND);

        String current = result.getReviewStatus();
        boolean isApprove = "APPROVE".equalsIgnoreCase(action);
        boolean isReject = "REJECT".equalsIgnoreCase(action);

        switch (current) {
            case "PENDING" -> {
                if (isApprove && "ROLE_TECHNICIAN".equals(reviewerRole)) {
                    result.setReviewStatus("TECH_APPROVED");
                    result.setTechReviewedBy(reviewer);
                    result.setTechReviewedAt(LocalDateTime.now());
                } else if (isReject && "ROLE_TECHNICIAN".equals(reviewerRole)) {
                    result.setReviewStatus("REJECTED");
                    result.setTechReviewedBy(reviewer);
                    result.setTechReviewedAt(LocalDateTime.now());
                } else {
                    throw new BusinessException(ResultCode.BAD_REQUEST,
                        "PENDING requires TECHNICIAN role for APPROVE/REJECT");
                }
            }
            case "TECH_APPROVED" -> {
                if (isApprove && "ROLE_REVIEWER".equals(reviewerRole)) {
                    result.setReviewStatus("CLINICAL_APPROVED");
                    result.setClinicalReviewedBy(reviewer);
                    result.setClinicalReviewedAt(LocalDateTime.now());
                } else if (isReject && "ROLE_REVIEWER".equals(reviewerRole)) {
                    result.setReviewStatus("REJECTED");
                    result.setClinicalReviewedBy(reviewer);
                    result.setClinicalReviewedAt(LocalDateTime.now());
                } else {
                    throw new BusinessException(ResultCode.BAD_REQUEST,
                        "TECH_APPROVED requires REVIEWER role for APPROVE/REJECT");
                }
            }
            case "CLINICAL_APPROVED" -> {
                if (isApprove && "ROLE_DIRECTOR".equals(reviewerRole)) {
                    result.setReviewStatus("RELEASED");
                    result.setReviewedBy(reviewer);
                    result.setReviewedAt(LocalDateTime.now());
                    rabbitTemplate.convertAndSend("myla.workflow", "lab.event", LabEvent.RESULT_RELEASED_TO_LIS);
                } else if (isReject && "ROLE_DIRECTOR".equals(reviewerRole)) {
                    result.setReviewStatus("REJECTED");
                    result.setReviewedBy(reviewer);
                    result.setReviewedAt(LocalDateTime.now());
                } else {
                    throw new BusinessException(ResultCode.BAD_REQUEST,
                        "CLINICAL_APPROVED requires DIRECTOR role for APPROVE/REJECT");
                }
            }
            default -> throw new BusinessException(ResultCode.INVALID_SAMPLE_STATUS,
                "Status " + current + " cannot be reviewed");
        }

        organismResultMapper.updateById(result);
        log.info("Result reviewed: id={}, action={}, role={}, reviewer={}, {}→{}",
            id, action, reviewerRole, reviewer, current, result.getReviewStatus());
    }

    /**
     * 根据样本条码查找样本ID。
     * 生产环境应通过 SampleMapper 查询样本表获取实际ID。
     * 当前为占位实现，返回默认值 0L。
     *
     * @param barcode 样本条码
     * @return 样本数据库主键ID
     */
    /**
     * 根据条码查找样本 ID。
     * 一期：查 sample 表按 barcode 匹配（手动登记或 LIS 入站的样本）。
     * 二期：如果找不到，LisInboundService.receiveOrder() 自动创建。
     */
    private Long findSampleIdByBarcode(String barcode) {
        if (barcode == null || barcode.isBlank()) return null;
        Sample sample = sampleMapper.selectOne(
            new LambdaQueryWrapper<Sample>().eq(Sample::getBarcode, barcode));
        return sample != null ? sample.getId() : null;
    }
}
