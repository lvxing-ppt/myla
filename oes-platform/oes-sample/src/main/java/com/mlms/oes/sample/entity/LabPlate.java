package com.mlms.oes.sample.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("lab_plate")
public class LabPlate {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String plateId;
    private Long sampleId;
    private String mediaType;
    private String mediaLot;
    private String status;
    private LocalDateTime inoculateTime;
    private String incubatorId;
    private String incubatorLocation;
    private String comment;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
