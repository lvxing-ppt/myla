package com.myla.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * MYLA 系统工作流规则实体类。
 * 对应数据库表 workflow_rule，存储实验室工作流的业务规则配置。
 * 每条规则定义了触发事件、条件表达式和要执行的动作列表。
 * 支持按优先级排序执行，可通过 enabled 字段动态启用/禁用规则。
 *
 * 典型使用场景：
 * - 危急值判定规则：检出特定多重耐药菌时触发预警
 * - 结果自动审核规则：满足条件的常规结果自动批准
 * - 报告自动发布规则：审核通过后自动推送至 LIS
 *
 * @author MYLA Team
 */
@Data
@TableName("workflow_rule")
public class WorkflowRule {
    /** 规则主键ID，数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 规则业务标识（唯一），用于跨系统引用 */
    private String ruleId;

    /** 规则名称，便于人工识别 */
    private String name;

    /** 触发事件名称，对应 LabEvent 枚举值 */
    private String triggerEvent;

    /** 条件表达式（MVEL/Script 格式），用于评估规则是否应执行 */
    private String conditionExpr;

    /** 动作列表（JSON 数组），定义规则触发后要执行的操作 */
    private String actions;

    /** 规则优先级，数值越大优先级越高，高优先级规则优先执行 */
    private Integer priority;

    /** 是否启用：0-禁用，1-启用 */
    private Integer enabled;

    /** 记录创建时间，由 MyBatis-Plus 插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 记录更新时间，由 MyBatis-Plus 插入和更新时自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
