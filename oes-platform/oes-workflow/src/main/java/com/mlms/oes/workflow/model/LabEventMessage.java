package com.mlms.oes.workflow.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * 实验室事件消息，通过 RabbitMQ 在模块间传递。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LabEventMessage implements Serializable {
    /** 事件名称，对应 LabEvent 枚举值 */
    private String event;
    /** 关联的 organism_result.id */
    private Long organismResultId;
}
