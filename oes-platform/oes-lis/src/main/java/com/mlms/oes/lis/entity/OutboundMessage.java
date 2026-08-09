package com.mlms.oes.lis.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * MLMS 系统 LIS 出站消息实体类。
 * 对应数据库表 outbound_message，记录系统向外发送给 LIS 系统的消息。
 * 每条消息包含唯一消息ID、目标医院编码、消息类型和内容，
 * 以及发送状态、重试次数、错误信息等追踪字段。
 * 支持消息重试机制：当发送失败时根据重试次数和最大重试次数决定是重试还是转入死信队列。
 *
 * @author MLMS Team
 */
@Data
@TableName("outbound_message")
public class OutboundMessage {
    /** 消息主键ID，数据库自增 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 消息唯一标识（UUID 去除连字符），用于追踪和去重 */
    private String messageId;

    /** 目标医院编码 */
    private String hospitalCode;

    /** 消息类型，如 RESULT-检验结果、ORDER-医嘱等 */
    private String messageType;

    /** 消息内容，通常为 HL7/ASTM 格式的文本 */
    private String messageContent;

    /** 发送状态：PENDING-待发送，SENT-已发送，FAILED-发送失败 */
    private String sendStatus;

    /** 当前重试次数 */
    private Integer retryCount;

    /** 最大重试次数，超过后消息将进入死信队列 */
    private Integer maxRetries;

    /** 最近一次失败的错误信息 */
    private String lastError;

    /** 下次重试时间 */
    private LocalDateTime nextRetryAt;

    /** 实际发送成功时间 */
    private LocalDateTime sentAt;

    /** 记录创建时间，由 MyBatis-Plus 插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
