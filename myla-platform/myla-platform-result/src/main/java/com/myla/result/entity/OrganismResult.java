package com.myla.result.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * MYLA 系统细菌鉴定结果实体类。
 * 对应数据库表 organism_result，存储微生物鉴定（ID）的核心结果数据。
 * 每条记录关联一个样本，包含检出细菌的名称和编码、
 * 鉴定置信度（identificationPercent）、仪器信息、审核状态、
 * 以及原始仪器消息等完整追溯信息。
 * 审核流程支持批准（APPROVED）和拒绝（REJECTED）两种操作。
 *
 * @author MYLA Team
 */
@Data
@TableName("organism_result")
public class OrganismResult {
    /** 鉴定结果主键ID，数据库自增 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 业务结果ID（唯一标识），用于跨系统追踪 */
    private String resultId;

    /** 关联的样本ID，外键关联 sample 表 */
    private Long sampleId;

    /** 仪器ID，标识数据来源的检验仪器 */
    private String instrumentId;

    /** 细菌编码（标准编码，如 WHONET/LIS 编码） */
    private String organismCode;

    /** 细菌名称 */
    private String organismName;

    /** 鉴定置信度百分比，表示仪器对鉴定结果的置信程度 */
    private BigDecimal identificationPercent;

    /** 结果类型：ID-仅鉴定，AST-鉴定+药敏 */
    private String resultType;

    /** 检验时间 */
    private LocalDateTime testTime;

    /** 审核状态：PENDING-待审核，APPROVED-已批准，REJECTED-已拒绝 */
    private String reviewStatus;

    /** 审核人 */
    private String reviewedBy;

    /** 审核时间 */
    private LocalDateTime reviewedAt;

    /** 一级技术审核人 */
    private String techReviewedBy;
    /** 一级技术审核时间 */
    private LocalDateTime techReviewedAt;
    /** 二级临床审核人 */
    private String clinicalReviewedBy;
    /** 二级临床审核时间 */
    private LocalDateTime clinicalReviewedAt;

    /** 原始仪器消息文本，用于追溯和数据恢复 */
    private String rawMessage;

    /** 记录创建时间，由 MyBatis-Plus 插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 记录更新时间，由 MyBatis-Plus 插入和更新时自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
