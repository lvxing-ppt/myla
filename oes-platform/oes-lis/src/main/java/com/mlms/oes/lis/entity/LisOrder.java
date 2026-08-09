package com.mlms.oes.lis.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * LIS 检验医嘱（订单），对应 lis_order 表。
 * 同一标本可有多条订单（不同检验项目）。
 */
@Data
@TableName("lis_order")
public class LisOrder {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String orderCode;
    private Long sampleId;
    private String hospitalCode;
    private String testCode;
    private String testName;
    private String specimenType;
    private LocalDateTime collectTime;
    private String priority;
    private String status;
    private Long inboundMessageId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
