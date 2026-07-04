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

/**
 * MYLA 系统实验室事件消费者。
 * 负责监听 RabbitMQ 实验室事件队列，接收来自样本模块、结果模块的事件，
 * 并驱动工作流规则引擎执行相应的业务规则。
 *
 * 消息处理流程：
 * 1. 从队列 "lab.event" 消费实验室领域事件（LabEvent 枚举）
 * 2. 根据事件类型查询匹配的已启用工作流规则
 * 3. 按优先级降序排列规则
 * 4. 逐一执行每条规则（规则之间相互独立，某条规则失败不影响后续规则执行）
 *
 * 错误处理策略：
 * 单条规则执行失败时仅记录错误日志，不中断后续规则的执行。
 * 该设计确保一条规则的异常不会阻塞其他业务流程。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LabEventConsumer {

    private final WorkflowRuleMapper ruleMapper;
    private final WorkflowEngine workflowEngine;

    /**
     * 处理实验室领域事件。
     * 监听队列 "lab.event"，根据事件类型查询匹配的已启用工作流规则，
     * 按优先级降序逐一执行规则。
     * 每条规则独立执行，某条规则失败不会影响其他规则。
     *
     * @param event 实验室领域事件，如 SAMPLE_REGISTERED、AST_RESULT_RECEIVED、
     *              RESULT_APPROVED、RESULT_RELEASED_TO_LIS 等
     */
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
