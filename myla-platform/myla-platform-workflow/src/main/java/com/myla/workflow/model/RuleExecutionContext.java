package com.myla.workflow.model;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

/**
 * 规则执行上下文，携带 MVEL 求值所需的全部变量。
 * LabEvent 枚举值由 triggerEvent 字段携带，不需要单独传递。
 */
@Data
@Builder
public class RuleExecutionContext {
    /** 触发事件类型 */
    private String triggerEvent;
    /** 关联的 organism_result ID */
    private Long organismResultId;
    /** MVEL 表达式求值的变量 Map */
    private Map<String, Object> variables;
}
