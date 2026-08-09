package com.mlms.oes.common.api.enums;

/**
 * 检验结果类型枚举。
 * <p>
 * 标识统一结果对象 {@link com.mlms.oes.common.api.dto.UnifiedResult} 所承载的数据类型。
 * 一个仪器上报的数据可能属于其中一种或多种类型，业务模块根据此枚举进行分类处理。
 * </p>
 *
 * @author MLMS Team
 */
public enum ResultType {

    /**
     * 血培养阳性标志。
     * 血培养瓶报阳后触发，通常不包含具体菌种信息，仅作为阳性告警。
     */
    BLOOD_CULTURE_FLAG,

    /**
     * 菌种鉴定结果。
     * 包含菌种名称、鉴定置信度等信息。
     */
    ORGANISM_ID,

    /**
     * 药敏（AST）结果。
     * 包含各抗生素的 MIC 值和 SIR 判读结果。
     */
    AST,

    /**
     * 质控结果。
     * 来自质控菌株的测试结果，用于验证仪器和试剂的准确性。
     */
    QC
}
