package com.myla.result.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * MYLA 系统药敏试验结果（AST）实体类。
 * 对应数据库表 ast_result，存储抗生素敏感性试验的详细结果。
 * 每条记录关联一个细菌鉴定结果（organism_result），
 * 包含抗生素信息、MIC 值、机器判读 SIR 结果和人工修正后的最终 SIR 判定。
 * 支持专家规则自动修正功能，记录修正前后的 SIR 值和修正原因。
 *
 * @author MYLA Team
 */
@Data
@TableName("ast_result")
public class AstResult {
    /** 药敏结果主键ID，数据库自增 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 关联的细菌鉴定结果ID，外键关联 organism_result 表 */
    private Long organismResultId;

    /** 抗生素编码（标准编码，如 WHONET 编码） */
    private String antibioticCode;

    /** 抗生素名称 */
    private String antibioticName;

    /** MIC 最低抑菌浓度值（数值型） */
    private BigDecimal micValue;

    /** MIC 值单位，如 ug/ml、mg/L */
    private String micUnit;

    /** 仪器判读的 SIR 结果：S-敏感，I-中介，R-耐药 */
    private String machineSir;

    /** 人工修正后的 SIR 结果：S-敏感，I-中介，R-耐药 */
    private String manualSir;

    /** 最终 SIR 判定结果，综合仪器判读和人工修正 */
    private String finalSir;

    /** 专家规则修正说明，记录自动修正的原因和规则名称 */
    private String expertRuleComment;

    /** 是否被专家规则自动修正：0-未修正，1-已修正 */
    private Integer isCorrected;

    /** 修正人（如为人工修正） */
    private String correctedBy;

    /** 修正时间 */
    private LocalDateTime correctedAt;

    /** 记录创建时间，由 MyBatis-Plus 插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
