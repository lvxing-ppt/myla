package com.myla.lis.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("lis_config")
public class LisConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String hospitalCode;
    private String channelType;
    private String channelConfig;
    private String orderMapping;
    private String testCodeMap;
    private String resultMapping;
    private String organismCodeMap;
    private String antibioticCodeMap;
    private String retryPolicy;
    private Integer ackTimeoutSec;
    private Integer enabled;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
