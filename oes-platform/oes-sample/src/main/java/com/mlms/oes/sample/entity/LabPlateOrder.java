package com.mlms.oes.sample.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("lab_plate_order")
public class LabPlateOrder {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long plateId;
    private Long orderId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
