package com.myla.common.core.exception;

import com.myla.common.core.constant.ResultCode;

/**
 * 仪器异常类。
 * <p>
 * 继承自 {@link BusinessException}，专门用于表示与实验室仪器通信或操作相关的异常。
 * 额外携带仪器 ID 和连续失败次数，便于运维人员进行故障诊断和自动恢复决策。
 * </p>
 *
 * @author MyLA Team
 */
public class InstrumentException extends BusinessException {

    /** 发生异常的仪器 ID */
    private final String instrumentId;

    /** 连续失败次数，用于判断是否需要触发告警或自动重启 */
    private final int consecutiveFailures;

    /**
     * 构造仪器异常实例。
     * @param instrumentId 仪器唯一标识
     * @param message 异常描述消息
     * @param consecutiveFailures 连续失败次数
     */
    public InstrumentException(String instrumentId, String message, int consecutiveFailures) {
        super(ResultCode.INSTRUMENT_ERROR, message);
        this.instrumentId = instrumentId;
        this.consecutiveFailures = consecutiveFailures;
    }
}
