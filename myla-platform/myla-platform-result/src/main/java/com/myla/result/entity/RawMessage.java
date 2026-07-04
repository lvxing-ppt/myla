package com.myla.result.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("raw_message")
public class RawMessage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String instrumentId;
    private String messageDirection;
    private String messageType;
    private String rawContent;
    private String parseStatus;
    private String parseError;
    private LocalDateTime receivedAt;
}
