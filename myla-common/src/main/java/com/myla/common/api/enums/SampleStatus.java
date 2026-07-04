package com.myla.common.api.enums;

/**
 * 样本状态枚举。
 * <p>
 * 描述一份微生物样本从登记到发布的全生命周期状态流转。
 * 每个状态对应一个中文标签，用于前端展示和日志记录。
 * </p>
 * <p>
 * 状态流转路径：
 * <pre>
 * REGISTERED -> INOCULATED -> INCUBATING -> PENDING_REVIEW -> APPROVED -> RELEASED
 *                                                   \-> REJECTED (可重新进入 PENDING_REVIEW)
 * </pre>
 * </p>
 *
 * @author MyLA Team
 */
public enum SampleStatus {

    /** 已登记：样本信息已录入系统，等待接种 */
    REGISTERED("已登记"),

    /** 已接种：样本已接种到培养基/鉴定卡上 */
    INOCULATED("已接种"),

    /** 培养中：正在孵育，等待仪器检测结果 */
    INCUBATING("培养中"),

    /** 待审核：已有初步结果，等待微生物专家审核 */
    PENDING_REVIEW("待审核"),

    /** 已审核：结果已通过审核 */
    APPROVED("已审核"),

    /** 已退回：审核不通过，结果被退回 */
    REJECTED("已退回"),

    /** 已发布：结果已发布至 LIS/HIS 系统 */
    RELEASED("已发布");

    /** 状态的中文标签 */
    private final String label;

    SampleStatus(String label) {
        this.label = label;
    }

    /**
     * 获取状态的中文标签。
     * @return 中文标签字符串
     */
    public String getLabel() {
        return label;
    }
}
