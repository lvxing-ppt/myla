package com.mlms.oes.sample.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sample_barcode")
public class SampleBarcode {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long sampleId;
    private String barcode;
    private String source;
    private Integer isPrimary;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
