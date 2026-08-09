package com.mlms.oes.gateway.core.model;

/**
 * 仪器维护能力枚举。
 * <p>
 * 声明仪器支持的维护操作类型。驱动通过实现
 * {@link com.mlms.oes.gateway.core.spi.InstrumentDriver#getMaintenanceCapabilities()}
 * 返回其支持的能力列表，上层根据此列表决定可执行的维护操作。
 * </p>
 *
 * @author MLMS Team
 */
public enum MaintenanceCapability {

    /** 固件升级：向仪器推送并安装新固件版本 */
    FIRMWARE_UPGRADE,

    /** 校准：执行仪器校准程序 */
    CALIBRATE,

    /** 自检：执行仪器自检程序 */
    SELF_TEST,

    /** 复位：将仪器恢复到初始状态 */
    RESET,

    /** 关机：安全关闭仪器 */
    SHUTDOWN,

    /** 重启：重新启动仪器 */
    RESTART
}
