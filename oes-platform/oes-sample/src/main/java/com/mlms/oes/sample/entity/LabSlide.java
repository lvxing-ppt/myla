package com.mlms.oes.sample.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("lab_slide")
public class LabSlide {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String slideId;
    private Long sampleId;
    private String stainType;
    private String stainLot;
    private String status;
    private String wbcCount;
    private String epiCellCount;
    private String bacteriaMorphology;
    private String gramResult;
    private String examinedBy;
    private LocalDateTime examinedAt;
    private String comment;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
