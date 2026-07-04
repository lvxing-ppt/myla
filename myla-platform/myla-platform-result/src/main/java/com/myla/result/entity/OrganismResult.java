package com.myla.result.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("organism_result")
public class OrganismResult {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String resultId;
    private Long sampleId;
    private String instrumentId;
    private String organismCode;
    private String organismName;
    private BigDecimal identificationPercent;
    private String resultType;
    private LocalDateTime testTime;
    private String reviewStatus;
    private String reviewedBy;
    private LocalDateTime reviewedAt;
    private String rawMessage;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
