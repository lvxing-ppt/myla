package com.mlms.oes.gateway.core.model;

import lombok.Data;
import java.util.Map;
import java.util.UUID;

/**
 * 仪器命令模型。
 * <p>
 * 封装向仪器发送的操作命令，包含命令类型、参数和超时设置。
 * 命令 ID 自动生成（UUID），用于跟踪命令生命周期。
 * </p>
 *
 * @author MLMS Team
 */
@Data
public class InstrumentCommand {

    /** 命令唯一 ID，自动生成 UUID */
    private String commandId = UUID.randomUUID().toString();

    /** 目标仪器 ID */
    private String instrumentId;

    /** 命令类型 */
    private CommandType type;

    /** 命令参数键值对 */
    private Map<String, Object> parameters;

    /** 命令超时时间，单位秒，默认 30 秒 */
    private int timeoutSeconds = 30;

    /**
     * 命令类型枚举。
     */
    public enum CommandType {
        /** 启动测试流程 */
        START_TEST,
        /** 终止/中止测试 */
        STOP_TEST,
        /** 选择鉴定/药敏卡类型 */
        SELECT_CARD,
        /** 查询仪器当前状态 */
        QUERY_STATUS,
        /** 发送医嘱/申请单信息 */
        SEND_ORDER
    }
}
