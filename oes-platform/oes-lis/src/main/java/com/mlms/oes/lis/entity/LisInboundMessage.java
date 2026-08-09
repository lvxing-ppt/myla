package com.mlms.oes.lis.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * LIS 入站原始报文实体，对应 lis_inbound_message 表。
 */
@Data
@TableName("lis_inbound_message")
public class LisInboundMessage {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String hospitalCode;
    private String messageType;
    private String messageControlId;
    private String rawContent;
    private String processStatus;
    private Long sampleId;
    private String errorMsg;
    private LocalDateTime receivedAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
