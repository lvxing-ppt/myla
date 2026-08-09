package com.mlms.oes.workflow.service;

import com.mlms.oes.workflow.entity.CriticalValueAlert;
import com.mlms.oes.workflow.entity.WorkflowRule;

/**
 * MLMS 系统工作流引擎服务接口。
 * 定义工作流规则执行和危急值预警管理的核心业务操作。
 * 工作流引擎是 MLMS 系统的业务编排核心，负责根据事件驱动的规则
 * 自动触发审核、预警、通知和报告发布等业务流程。
 */
public interface WorkflowEngine {

    /**
     * 执行工作流规则。
     * 根据规则配置的条件表达式评估是否满足执行条件，
     * 若满足则执行规则定义的动作列表。
     * 生产环境使用 MVEL 表达式引擎评估条件。
     *
     * @param rule    要执行的工作流规则
     * @param context 规则执行上下文（通常为 LabEvent 事件对象）
     */
    void executeRule(WorkflowRule rule, Object context);

    /**
     * 创建危急值预警记录。
     * 设置预警默认状态为 PENDING，初始化升级次数为 0，
     * 持久化预警记录。后续由通知模块异步发送预警通知。
     *
     * @param alert 危急值预警实体（包含细菌结果ID、预警原因、级别等信息）
     * @return 创建成功的危急值预警实体
     */
    CriticalValueAlert createCriticalAlert(CriticalValueAlert alert);
}
