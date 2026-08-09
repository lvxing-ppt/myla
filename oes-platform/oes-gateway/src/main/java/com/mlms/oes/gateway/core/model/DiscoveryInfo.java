package com.mlms.oes.gateway.core.model;

import lombok.Data;
import java.util.List;

/**
 * 仪器发现信息模型。
 * <p>
 * 描述一台仪器的静态属性，包括制造商、型号、序列号、固件/硬件版本
 * 以及该仪器支持的命令列表。通常从仪器端获取或由驱动硬编码提供。
 * </p>
 *
 * @author MLMS Team
 */
@Data
public class DiscoveryInfo {

    /** 制造商名称，如 "bioMerieux" */
    private String manufacturer;

    /** 仪器型号，如 "VITEK 2" */
    private String model;

    /** 仪器序列号 */
    private String serialNumber;

    /** 固件版本号 */
    private String firmwareVersion;

    /** 硬件版本号 */
    private String hardwareRevision;

    /** 该仪器支持的命令名称列表 */
    private List<String> supportedCommands;
}
