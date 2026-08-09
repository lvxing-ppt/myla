package com.mlms.oes.common.api.dto;

import lombok.Data;

/**
 * 药敏结果（AST，Antimicrobial Susceptibility Testing）数据传输对象。
 * <p>
 * 用于承载单个抗生素的药敏试验结果，包括 MIC 值、SIR 判读结果以及专家规则评注。
 * 通常作为 {@link UnifiedResult} 的子列表字段使用。
 * </p>
 *
 * @author MLMS Team
 */
@Data
public class AstResultDTO {

    /** 抗生素代码，如 "AMP"、"CIP" 等 */
    private String antibioticCode;

    /** 抗生素名称，如 "氨苄西林"、"环丙沙星" 等 */
    private String antibioticName;

    /** 最低抑菌浓度（MIC）数值 */
    private Double micValue;

    /** MIC 单位，如 "μg/mL" */
    private String micUnit;

    /** SIR 判读结果字符串：S=敏感，I=中介，R=耐药 */
    private String sirInterpretation;

    /** 仪器原始判读的 SIR 结果 */
    private String machineSIR;

    /** 人工修正后的 SIR 结果 */
    private String manualSIR;

    /** 最终生效的 SIR 结果（综合仪器判读与人工修正） */
    private String finalSIR;

    /** 专家规则评注，如 "ESBL 阳性：所有青霉素类应报告为耐药" */
    private String expertRuleComment;
}
