package com.myla.lis.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("outbound_message")
public class OutboundMessage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String messageId;
    private String hospitalCode;
    private String messageType;
    private String messageContent;
    private String sendStatus;
    private Integer retryCount;
    private Integer maxRetries;
    private String lastError;
    private LocalDateTime nextRetryAt;
    private LocalDateTime sentAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
