package com.myla.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myla.result.entity.AstResult;
import com.myla.result.entity.OrganismResult;
import com.myla.result.mapper.AstResultMapper;
import com.myla.result.mapper.OrganismResultMapper;
import com.myla.workflow.entity.CriticalValueAlert;
import com.myla.workflow.entity.WorkflowRule;
import com.myla.workflow.mapper.CriticalValueAlertMapper;
import com.myla.workflow.mapper.WorkflowRuleMapper;
import com.myla.workflow.model.RuleExecutionContext;
import com.myla.workflow.service.WorkflowEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mvel2.MVEL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 工作流引擎实现 —— MVEL 条件求值 + JSON actions 执行。
 *
 * <h3>支持的 action 类型：</h3>
 * <ul>
 *   <li><b>correct_sir</b> — 修正药敏 SIR 判定（天然耐药规则）</li>
 *   <li><b>create_alert</b> — 创建危急值告警</li>
 *   <li><b>set_review_status</b> — 修改审核状态（退回重新审核）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowEngineImpl implements WorkflowEngine {

    private final WorkflowRuleMapper ruleMapper;
    private final CriticalValueAlertMapper alertMapper;
    private final OrganismResultMapper organismResultMapper;
    private final AstResultMapper astResultMapper;
    private static final ObjectMapper json = new ObjectMapper();

    /**
     * 执行工作流规则。
     * <ol>
     *   <li>根据 organismResultId 加载 organism 和 AST 数据</li>
     *   <li>构建 MVEL 变量上下文</li>
     *   <li>求值 conditionExpr，条件不满足则跳过</li>
     *   <li>解析 actions JSON，逐条执行</li>
     * </ol>
     */
    @Override
    @Transactional
    public void executeRule(WorkflowRule rule, Object contextObj) {
        RuleExecutionContext ctx = (RuleExecutionContext) contextObj;

        // 1. 加载结果数据
        OrganismResult orgResult = organismResultMapper.selectById(ctx.getOrganismResultId());
        if (orgResult == null) {
            log.warn("Rule {} skipped: organism_result not found (id={})",
                    rule.getRuleId(), ctx.getOrganismResultId());
            return;
        }

        List<AstResult> astResults = astResultMapper.selectList(
                new LambdaQueryWrapper<AstResult>()
                        .eq(AstResult::getOrganismResultId, orgResult.getId()));

        // 2. 构建 MVEL 变量上下文
        Map<String, Object> vars = ctx.getVariables() != null
                ? new java.util.HashMap<>(ctx.getVariables()) : new java.util.HashMap<>();
        vars.put("organismName", orgResult.getOrganismName());
        vars.put("organismCode", orgResult.getOrganismCode());
        vars.put("organismId", orgResult.getId());
        vars.put("resultId", orgResult.getResultId());
        vars.put("instrumentId", orgResult.getInstrumentId());
        vars.put("reviewStatus", orgResult.getReviewStatus());
        vars.put("resultType", orgResult.getResultType());
        vars.put("astCount", astResults.size());

        // 3. 求值 MVEL 条件
        if (rule.getConditionExpr() != null && !rule.getConditionExpr().isBlank()) {
            try {
                Serializable compiled = MVEL.compileExpression(rule.getConditionExpr());
                Object evalResult = MVEL.executeExpression(compiled, vars);
                if (!isTruthy(evalResult)) {
                    log.debug("Rule {} condition not met: {}", rule.getRuleId(), rule.getConditionExpr());
                    return;
                }
            } catch (Exception e) {
                log.error("Rule {} condition evaluation failed: {}", rule.getRuleId(), e.getMessage());
                return;
            }
        }

        log.info("Rule {} matched, executing actions for organism_result.id={}",
                rule.getRuleId(), orgResult.getId());

        // 4. 解析并执行 actions
        if (rule.getActions() == null || rule.getActions().isBlank()) {
            log.warn("Rule {} has no actions defined", rule.getRuleId());
            return;
        }

        try {
            List<Map<String, Object>> actions = json.readValue(rule.getActions(),
                    new TypeReference<List<Map<String, Object>>>() {});
            for (Map<String, Object> action : actions) {
                executeAction(action, orgResult, astResults);
            }
        } catch (Exception e) {
            log.error("Rule {} action parsing failed: {}", rule.getRuleId(), e.getMessage());
        }
    }

    // ==================== Action Dispatch ====================

    @SuppressWarnings("unchecked")
    private void executeAction(Map<String, Object> action, OrganismResult orgResult,
                                List<AstResult> astResults) {
        String type = (String) action.get("type");
        if (type == null) return;

        switch (type) {
            case "correct_sir" -> executeCorrectSir(action, astResults);
            case "create_alert" -> executeCreateAlert(action, orgResult);
            case "set_review_status" -> executeSetReviewStatus(action, orgResult);
            default -> log.warn("Unknown action type: {}", type);
        }
    }

    /**
     * correct_sir: 修正指定抗生素的 SIR 判定。
     * <pre>{@code
     * {
     *   "type": "correct_sir",
     *   "antibiotics": ["Ceftriaxone", "Cefotaxime"],
     *   "to_sir": "R",
     *   "reason": "肠球菌属对头孢菌素类天然耐药 (CLSI M100 Table 2A)"
     * }
     * }</pre>
     */
    @SuppressWarnings("unchecked")
    private void executeCorrectSir(Map<String, Object> action, List<AstResult> astResults) {
        List<String> targets = (List<String>) action.get("antibiotics");
        String toSir = (String) action.get("to_sir");
        String reason = (String) action.getOrDefault("reason", "专家规则修正");
        if (targets == null || toSir == null) return;

        int corrected = 0;
        for (AstResult ast : astResults) {
            if (ast.getAntibioticName() != null
                    && targets.stream().anyMatch(t -> ast.getAntibioticName().equalsIgnoreCase(t))) {
                // 仅当需要修正时才更新
                if (!toSir.equals(ast.getFinalSir())) {
                    ast.setFinalSir(toSir);
                    ast.setManualSir(toSir);
                    ast.setExpertRuleComment(reason);
                    ast.setIsCorrected(1);
                    astResultMapper.updateById(ast);
                    corrected++;
                }
            }
        }
        log.info("  [correct_sir] corrected {} AST results to SIR={}, reason: {}",
                corrected, toSir, reason);
    }

    /**
     * create_alert: 创建危急值告警。
     * <pre>{@code
     * {
     *   "type": "create_alert",
     *   "alert_level": "CRITICAL",
     *   "alert_reason": "检出万古霉素耐药金黄色葡萄球菌",
     *   "notify_methods": "SMS,ONSCREEN",
     *   "notify_targets": "13800138000"
     * }
     * }</pre>
     */
    private void executeCreateAlert(Map<String, Object> action, OrganismResult orgResult) {
        CriticalValueAlert alert = new CriticalValueAlert();
        alert.setOrganismResultId(orgResult.getId());
        alert.setOrganismName(orgResult.getOrganismName());
        alert.setAlertReason((String) action.getOrDefault("alert_reason", "未指定原因"));
        alert.setAlertLevel((String) action.getOrDefault("alert_level", "CRITICAL"));
        alert.setNotifyMethods((String) action.get("notify_methods"));
        alert.setNotifyTargets((String) action.get("notify_targets"));
        alert.setNotifyStatus("PENDING");
        alert.setEscalateCount(0);
        alert.setCreatedAt(LocalDateTime.now());
        alertMapper.insert(alert);
        log.info("  [create_alert] id={}, level={}, reason={}",
                alert.getId(), alert.getAlertLevel(), alert.getAlertReason());
    }

    /**
     * set_review_status: 修改审核状态。
     * <pre>{@code
     * {
     *   "type": "set_review_status",
     *   "to_status": "PENDING",
     *   "comment": "专家规则已修正药敏结果，需重新审核"
     * }
     * }</pre>
     */
    private void executeSetReviewStatus(Map<String, Object> action, OrganismResult orgResult) {
        String toStatus = (String) action.get("to_status");
        String comment = (String) action.getOrDefault("comment", "");
        if (toStatus == null) return;

        String oldStatus = orgResult.getReviewStatus();
        orgResult.setReviewStatus(toStatus);
        orgResult.setUpdatedAt(LocalDateTime.now());
        organismResultMapper.updateById(orgResult);
        log.info("  [set_review_status] {}: {} → {}, comment: {}",
                orgResult.getResultId(), oldStatus, toStatus, comment);
    }

    // ==================== 警告相关 ====================

    @Override
    @Transactional
    public CriticalValueAlert createCriticalAlert(CriticalValueAlert alert) {
        alert.setNotifyStatus("PENDING");
        alert.setEscalateCount(0);
        alert.setCreatedAt(LocalDateTime.now());
        alertMapper.insert(alert);
        log.info("Critical alert created: id={}, organism={}, reason={}",
                alert.getId(), alert.getOrganismName(), alert.getAlertReason());
        return alert;
    }

    /** 判断 MVEL 求值结果是否为"真" */
    private boolean isTruthy(Object result) {
        if (result == null) return false;
        if (result instanceof Boolean b) return b;
        if (result instanceof String s) return !s.isBlank();
        if (result instanceof Number n) return n.doubleValue() != 0;
        return true;
    }
}
