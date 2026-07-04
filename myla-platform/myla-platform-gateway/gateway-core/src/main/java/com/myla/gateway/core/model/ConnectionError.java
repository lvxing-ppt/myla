package com.myla.gateway.core.model;

import lombok.Data;

/**
 * 连接错误模型。
 * <p>
 * 封装通信通道层发生的错误信息，通过错误监听器回调传递给上层驱动。
 * 包含通道类型、错误消息和可选的根因异常。
 * </p>
 *
 * @author MyLA Team
 */
@Data
public class ConnectionError {

    /** 发生错误的通道类型，如 "TCP"、"FILE"、"SERIAL" */
    private String channelType;

    /** 错误描述消息 */
    private String message;

    /** 导致错误的原始异常（可选） */
    private Throwable cause;
}
