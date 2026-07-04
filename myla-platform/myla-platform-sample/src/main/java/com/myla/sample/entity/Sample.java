package com.myla.sample.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sample")
public class Sample {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String sampleId;
    private String barcode;
    private String patientId;
    private String patientName;
    private String gender;
    private Integer age;
    private String specimenType;
    private LocalDateTime collectTime;
    private LocalDateTime receiveTime;
    private String status;
    private String priority;
    private String wardCode;
    private String wardName;
    private String diagnosis;
    private String sourceSystem;
    private String comment;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
