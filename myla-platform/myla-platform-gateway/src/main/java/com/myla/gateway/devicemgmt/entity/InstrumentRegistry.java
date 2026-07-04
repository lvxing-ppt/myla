package com.myla.gateway.devicemgmt.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 仪器注册表实体，对应 instrument_registry 表。
 * 记录所有接入仪器的注册信息、运行状态和最后心跳时间。
 */
@Data
@TableName("instrument_registry")
public class InstrumentRegistry {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 系统内唯一仪器编号 */
    private String instrumentId;

    /** 关联驱动 ID */
    private String driverId;

    /** 制造商 */
    private String manufacturer;

    /** 型号 */
    private String model;

    /** 序列号 */
    private String serialNumber;

    /** 固件版本 */
    private String firmwareVer;

    /** 硬件版本 */
    private String hardwareRev;

    /** 实验室位置 */
    private String location;

    /** 状态: ONLINE / OFFLINE / BUSY / ERROR / MAINTENANCE */
    private String status;

    /** 注册时间 */
    private LocalDateTime registeredAt;

    /** 最后在线时间（心跳） */
    private LocalDateTime lastSeenAt;

    /** 通道配置 JSON: {"type":"TCP","port":19002,"splitterType":"ASTM","parserType":"vitek2-parser"} */
    private String channelConfig;
}
