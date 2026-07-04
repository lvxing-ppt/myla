package com.myla.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("critical_value_alert")
public class CriticalValueAlert {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long organismResultId;
    private String organismName;
    private String alertReason;
    private String alertLevel;
    private String notifyMethods;
    private String notifyTargets;
    private String notifyStatus;
    private LocalDateTime confirmTime;
    private String confirmBy;
    private Integer escalateCount;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
