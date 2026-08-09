package com.mlms.oes.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * MLMS 系统危急值预警实体类。
 * 对应数据库表 critical_value_alert，存储危急值预警的完整信息。
 * 当检验结果满足危急值判定条件时，工作流引擎自动创建预警记录。
 * 包括预警原因、预警级别、通知方式（短信/邮件/电话）、通知目标、
 * 通知状态、确认信息和升级次数等字段。
 * 支持预警升级机制：若预警在指定时间内未被确认，可自动升级通知级别。
 *
 * @author MLMS Team
 */
@Data
@TableName("critical_value_alert")
public class CriticalValueAlert {
    /** 预警主键ID，数据库自增 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 关联的细菌鉴定结果ID，外键关联 organism_result 表 */
    private Long organismResultId;

    /** 检出细菌名称 */
    private String organismName;

    /** 预警原因，描述触发预警的具体情况（如：检出多重耐药菌） */
    private String alertReason;

    /** 预警级别：CRITICAL-危急，HIGH-高，MEDIUM-中，LOW-低 */
    private String alertLevel;

    /** 通知方式（逗号分隔），如 SMS,EMAIL,PHONE */
    private String notifyMethods;

    /** 通知目标（逗号分隔），如具体手机号、邮箱、用户ID */
    private String notifyTargets;

    /** 通知状态：PENDING-待通知，SENT-已发送，CONFIRMED-已确认，ESCALATED-已升级 */
    private String notifyStatus;

    /** 确认时间（预警被人工确认的时间） */
    private LocalDateTime confirmTime;

    /** 确认人 */
    private String confirmBy;

    /** 升级次数，记录预警被自动升级的次数 */
    private Integer escalateCount;

    /** 记录创建时间，由 MyBatis-Plus 插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
