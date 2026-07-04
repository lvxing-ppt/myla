package com.myla.common.api.event;

/**
 * 实验室事件枚举。
 * <p>
 * 定义微生物实验室工作流中的关键业务事件，用于跨模块事件驱动通信。
 * 各模块通过发布/订阅这些事件实现松耦合协作。事件通过消息队列（RabbitMQ）进行传递。
 * </p>
 *
 * @author MyLA Team
 */
public enum LabEvent {

    /** 样本已登记：样本信息完成系统录入 */
    SAMPLE_REGISTERED,

    /** 样本已接收：实验室物理接收到样本 */
    SAMPLE_RECEIVED,

    /** 培养阳性：血培养或其他培养瓶报阳 */
    CULTURE_POSITIVE,

    /** 培养阴性：培养到期未生长，报告阴性 */
    CULTURE_NEGATIVE,

    /** 菌种已鉴定：仪器完成菌种鉴定并上报结果 */
    ORGANISM_IDENTIFIED,

    /** 药敏结果已接收：仪器完成药敏试验并上报结果 */
    AST_RESULT_RECEIVED,

    /** 结果已审核：专家完成结果审核 */
    RESULT_APPROVED,

    /** 结果已发布至 LIS：最终结果发送到 LIS 系统 */
    RESULT_RELEASED_TO_LIS,

    /** 危急值已检出：检测到需要立即通知临床的危急结果 */
    CRITICAL_VALUE_DETECTED,

    /** TAT 超时：检验周转时间超过预设阈值，触发告警 */
    TAT_THRESHOLD_EXCEEDED,

    /** 样本不匹配：样本信息与实际检测信息不一致 */
    SAMPLE_MISMATCH,

    /** 质控超范围：质控结果超出允许范围 */
    QC_OUT_OF_RANGE
}
