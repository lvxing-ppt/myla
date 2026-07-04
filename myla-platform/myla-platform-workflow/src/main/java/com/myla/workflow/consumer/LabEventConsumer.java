package com.myla.workflow.consumer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.myla.workflow.entity.WorkflowRule;
import com.myla.workflow.mapper.WorkflowRuleMapper;
import com.myla.workflow.model.LabEventMessage;
import com.myla.workflow.model.RuleExecutionContext;
import com.myla.workflow.service.WorkflowEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 实验室事件消费者。
 * 监听 "lab.event" 队列，接收 LabEventMessage，
 * 查询匹配的规则并按优先级执行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LabEventConsumer {

    private final WorkflowRuleMapper ruleMapper;
    private final WorkflowEngine workflowEngine;

    @RabbitListener(queues = "lab.event")
    public void onLabEvent(LabEventMessage msg) {
        log.info("Received lab event: event={}, organismResultId={}",
                msg.getEvent(), msg.getOrganismResultId());

        List<WorkflowRule> rules = ruleMapper.selectList(
            new LambdaQueryWrapper<WorkflowRule>()
                .eq(WorkflowRule::getTriggerEvent, msg.getEvent())
                .eq(WorkflowRule::getEnabled, 1)
                .orderByDesc(WorkflowRule::getPriority)
        );

        if (rules.isEmpty()) {
            log.debug("No enabled rules for event {}", msg.getEvent());
            return;
        }

        // 构建规则执行上下文
        RuleExecutionContext ctx = RuleExecutionContext.builder()
                .triggerEvent(msg.getEvent())
                .organismResultId(msg.getOrganismResultId())
                .variables(Map.of())
                .build();

        for (WorkflowRule rule : rules) {
            try {
                workflowEngine.executeRule(rule, ctx);
            } catch (Exception e) {
                log.error("Failed to execute rule {}: {}", rule.getRuleId(), e.getMessage(), e);
            }
        }
    }
}
