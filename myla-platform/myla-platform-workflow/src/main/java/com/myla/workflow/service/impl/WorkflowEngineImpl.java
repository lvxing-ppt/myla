package com.myla.workflow.service.impl;

import com.myla.common.api.event.LabEvent;
import com.myla.workflow.entity.CriticalValueAlert;
import com.myla.workflow.entity.WorkflowRule;
import com.myla.workflow.mapper.CriticalValueAlertMapper;
import com.myla.workflow.mapper.WorkflowRuleMapper;
import com.myla.workflow.service.WorkflowEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowEngineImpl implements WorkflowEngine {

    private final WorkflowRuleMapper ruleMapper;
    private final CriticalValueAlertMapper alertMapper;

    @Override
    public void executeRule(WorkflowRule rule, Object context) {
        log.info("Executing workflow rule: ruleId={}, name={}", rule.getRuleId(), rule.getName());
        // In production, evaluate MVEL condition expression and execute actions
    }

    @Override
    @Transactional
    public CriticalValueAlert createCriticalAlert(CriticalValueAlert alert) {
        alert.setNotifyStatus("PENDING");
        alert.setEscalateCount(0);
        alert.setCreatedAt(LocalDateTime.now());
        alertMapper.insert(alert);
        log.info("Critical alert created: organismName={}, reason={}", alert.getOrganismName(), alert.getAlertReason());
        return alert;
    }
}
