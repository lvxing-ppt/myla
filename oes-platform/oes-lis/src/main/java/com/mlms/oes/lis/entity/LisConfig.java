package com.mlms.oes.lis.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * MLMS 系统 LIS 配置实体类。
 * 对应数据库表 lis_config，存储各医院与 LIS 系统的对接配置信息。
 * 包括通信通道类型（HL7/ASTM/HTTP）、各类映射规则（医嘱映射、检验项目编码映射、
 * 结果映射、细菌编码映射、抗生素编码映射）、重试策略和确认超时等参数。
 *
 * @author MLMS Team
 */
@Data
@TableName("lis_config")
public class LisConfig {
    /** 配置主键ID，数据库自增 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 医院编码，标识该配置所属的医院 */
    private String hospitalCode;

    /** 通信通道类型，如 HL7、ASTM、HTTP 等 */
    private String channelType;

    /** 入站通道配置（JSON 格式），包含监听端口等。入站：MLMS 本机监听，LIS 主动连接 */
    private String inboundConfig;

    /** 出站通道配置（JSON 格式），包含 LIS 服务器 IP、端口、URL 等。出站：MLMS 主动连接医院 LIS */
    private String outboundConfig;

    /** 医嘱映射规则（JSON 格式），将 HIS 医嘱编码映射为 LIS 检验项目编码 */
    private String orderMapping;

    /** 检验项目编码映射（JSON 格式），医院本地检验编码到标准编码的映射 */
    private String testCodeMap;

    /** 结果映射规则（JSON 格式），LIS 返回结果的字段映射配置 */
    private String resultMapping;

    /** 细菌/微生物编码映射（JSON 格式），细菌名称到标准编码的映射 */
    private String organismCodeMap;

    /** 抗生素编码映射（JSON 格式），抗生素名称到标准编码的映射 */
    private String antibioticCodeMap;

    /** 重试策略配置（JSON 格式），包含最大重试次数、重试间隔等 */
    private String retryPolicy;

    /** ACK 确认超时时间（秒），超过此时间未收到确认则视为发送失败 */
    private Integer ackTimeoutSec;

    /** 是否启用：0-禁用，1-启用 */
    private Integer enabled;

    /** 记录创建时间，由 MyBatis-Plus 插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 记录更新时间，由 MyBatis-Plus 插入和更新时自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
