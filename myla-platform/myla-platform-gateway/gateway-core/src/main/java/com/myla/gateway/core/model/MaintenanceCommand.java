package com.myla.gateway.core.model;

import lombok.Data;
import java.util.Map;
import java.util.UUID;

/**
 * 仪器维护命令模型。
 * <p>
 * 封装向仪器发送的维护操作命令，如固件升级、校准、自检等。
 * 命令 ID 自动生成（UUID），用于跟踪维护操作的生命周期。
 * </p>
 *
 * @author MyLA Team
 */
@Data
public class MaintenanceCommand {

    /** 命令唯一 ID，自动生成 UUID */
    private String commandId = UUID.randomUUID().toString();

    /** 目标仪器 ID */
    private String instrumentId;

    /** 维护能力类型，如 FIRMWARE_UPGRADE、CALIBRATE 等 */
    private MaintenanceCapability capability;

    /** 维护操作参数键值对 */
    private Map<String, Object> parameters;
}
