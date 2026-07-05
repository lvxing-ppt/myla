package com.myla.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 报告模板实体，对应 report_template 表。
 */
@Data
@TableName("report_template")
public class ReportTemplate {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 模板编码 */
    private String templateCode;

    /** 模板名称 */
    private String templateName;

    /** 模板类型: JASPER/EXCEL */
    private String templateType;

    /** 模板文件路径 */
    private String templatePath;

    /** 输出格式: PDF/EXCEL */
    private String outputFormat;

    /** 默认参数(JSON) */
    private String parameters;

    /** 是否启用 */
    private Integer enabled;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
