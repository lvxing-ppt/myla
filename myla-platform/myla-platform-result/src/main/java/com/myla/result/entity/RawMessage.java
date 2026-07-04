package com.myla.result.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * MYLA 系统原始消息实体类。
 * 对应数据库表 raw_message，存储从检验仪器接收的原始通信消息。
 * 记录消息的收发方向、类型和原始内容，以及解析状态和错误信息。
 * 作为数据追溯链的最上游，保留完整的原始数据用于审计和问题排查。
 *
 * @author MYLA Team
 */
@Data
@TableName("raw_message")
public class RawMessage {
    /** 原始消息主键ID，数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 仪器ID，标识消息来自或发往的检验仪器 */
    private String instrumentId;

    /** 消息方向：INBOUND-入站（仪器到系统），OUTBOUND-出站（系统到仪器） */
    private String messageDirection;

    /** 消息类型：HL7、ASTM 等通信协议 */
    private String messageType;

    /** 原始消息内容（文本），保留仪器的原始输出不做任何修改 */
    private String rawContent;

    /** 解析状态：PENDING-待解析，PARSED-已解析，ERROR-解析失败 */
    private String parseStatus;

    /** 解析错误信息，记录解析失败的原因 */
    private String parseError;

    /** 消息接收时间 */
    private LocalDateTime receivedAt;
}
