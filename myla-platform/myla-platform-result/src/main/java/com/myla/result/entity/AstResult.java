package com.myla.result.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ast_result")
public class AstResult {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long organismResultId;
    private String antibioticCode;
    private String antibioticName;
    private BigDecimal micValue;
    private String micUnit;
    private String machineSir;
    private String manualSir;
    private String finalSir;
    private String expertRuleComment;
    private Integer isCorrected;
    private String correctedBy;
    private LocalDateTime correctedAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
