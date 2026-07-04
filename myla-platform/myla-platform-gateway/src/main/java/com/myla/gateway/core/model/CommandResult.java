package com.myla.gateway.core.model;

import lombok.Data;
import java.util.Map;

/**
 * 命令执行结果模型。
 * <p>
 * 封装向仪器发送命令后的执行结果，包含命令状态、输出数据、错误信息和耗时。
 * 由 {@link com.myla.gateway.core.spi.InstrumentDriver#executeCommand} 返回。
 * </p>
 *
 * @author MyLA Team
 */
@Data
public class CommandResult {

    /** 命令唯一 ID，与 {@link InstrumentCommand#getCommandId()} 对应 */
    private String commandId;

    /** 命令执行状态 */
    private CommandStatus status;

    /** 命令执行输出的键值对数据 */
    private Map<String, Object> output;

    /** 失败时的错误消息 */
    private String errorMessage;

    /** 命令执行耗时，单位毫秒 */
    private long elapsedMs;

    /**
     * 命令执行状态枚举。
     */
    public enum CommandStatus {
        /** 命令已被接受，等待执行 */
        ACCEPTED,
        /** 命令正在执行中 */
        EXECUTING,
        /** 命令执行成功完成 */
        COMPLETED,
        /** 命令执行失败 */
        FAILED,
        /** 命令执行超时 */
        TIMEOUT
    }
}
