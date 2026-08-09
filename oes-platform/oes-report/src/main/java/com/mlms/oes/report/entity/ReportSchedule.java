package com.mlms.oes.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 定时报告调度实体，对应 report_schedule 表。
 */
@Data
@TableName("report_schedule")
public class ReportSchedule {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 调度名称 */
    private String scheduleName;

    /** 关联模板编码 */
    private String templateCode;

    /** Quartz cron 表达式 */
    private String cronExpr;

    /** 接收人列表(JSON) */
    private String recipients;

    /** 通知方式: EMAIL/SMS */
    private String notifyMethod;

    /** 是否启用 */
    private Integer enabled;

    /** 上次运行时间 */
    private LocalDateTime lastRunAt;

    /** 下次运行时间 */
    private LocalDateTime nextRunAt;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
