package com.mlms.oes.gateway.core.model;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 仪器状态模型。
 * <p>
 * 描述一台仪器的运行时状态，包括在线状态、状态消息和时间戳。
 * 用于监控面板展示和健康检查。
 * </p>
 *
 * @author MLMS Team
 */
@Data
public class InstrumentStatus {

    /** 仪器唯一标识 */
    private String instrumentId;

    /** 当前状态 */
    private Status status;

    /** 状态描述消息 */
    private String message;

    /** 状态更新时间戳，默认当前时间 */
    private LocalDateTime timestamp = LocalDateTime.now();

    /**
     * 仪器状态枚举。
     */
    public enum Status {
        /** 在线：仪器连接正常，数据通信正常 */
        ONLINE,
        /** 离线：仪器未连接或已断开 */
        OFFLINE,
        /** 忙碌：仪器正在执行测试任务 */
        BUSY,
        /** 错误：仪器或通信发生异常 */
        ERROR,
        /** 维护中：仪器处于维护/校准/升级状态 */
        MAINTENANCE
    }
}
