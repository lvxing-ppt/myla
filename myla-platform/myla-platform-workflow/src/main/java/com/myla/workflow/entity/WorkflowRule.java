package com.myla.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("workflow_rule")
public class WorkflowRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String ruleId;
    private String name;
    private String triggerEvent;
    private String conditionExpr;
    private String actions;
    private Integer priority;
    private Integer enabled;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
