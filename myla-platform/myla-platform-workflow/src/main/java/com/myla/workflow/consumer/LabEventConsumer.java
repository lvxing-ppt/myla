package com.myla.workflow.consumer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.myla.common.api.event.LabEvent;
import com.myla.workflow.entity.WorkflowRule;
import com.myla.workflow.mapper.WorkflowRuleMapper;
import com.myla.workflow.service.WorkflowEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class LabEventConsumer {

    private final WorkflowRuleMapper ruleMapper;
    private final WorkflowEngine workflowEngine;

    @RabbitListener(queues = "lab.event")
    public void onLabEvent(LabEvent event) {
        log.info("Received lab event: {}", event);

        List<WorkflowRule> rules = ruleMapper.selectList(
            new LambdaQueryWrapper<WorkflowRule>()
                .eq(WorkflowRule::getTriggerEvent, event.name())
                .eq(WorkflowRule::getEnabled, 1)
                .orderByDesc(WorkflowRule::getPriority)
        );

        for (WorkflowRule rule : rules) {
            try {
                workflowEngine.executeRule(rule, event);
            } catch (Exception e) {
                log.error("Failed to execute rule {}: {}", rule.getRuleId(), e.getMessage());
            }
        }
    }
}
