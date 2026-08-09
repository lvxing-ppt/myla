package com.mlms.oes.gateway.core.model;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 仪器遥测数据模型。
 * <p>
 * 封装仪器定期上报的运行环境数据和硬件状态信息。
 * 由 {@link com.mlms.oes.gateway.core.spi.TelemetryListener} 接收并传递给监控模块。
 * </p>
 *
 * @author MLMS Team
 */
@Data
public class TelemetryData {

    /** 遥测数据采集时间戳，默认当前时间 */
    private LocalDateTime timestamp = LocalDateTime.now();

    /** CPU 温度，单位摄氏度 */
    private Double cpuTemp;

    /** 环境温度，单位摄氏度 */
    private Double ambientTemp;

    /** 环境湿度，百分比 */
    private Double humidity;

    /** 电源状态，如 "NORMAL"、"BATTERY"、"UPS" */
    private String powerStatus;

    /** 试剂剩余量（测试次数） */
    private Integer reagentRemaining;

    /** 仪器运行时长，单位秒 */
    private Long uptimeSeconds;

    /** 当前活跃的故障代码列表 */
    private List<String> activeFaults;
}
