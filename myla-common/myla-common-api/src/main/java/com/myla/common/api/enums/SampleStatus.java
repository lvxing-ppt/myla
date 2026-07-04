package com.myla.common.api.enums;

public enum SampleStatus {
    REGISTERED("已登记"),
    INOCULATED("已接种"),
    INCUBATING("培养中"),
    PENDING_REVIEW("待审核"),
    APPROVED("已审核"),
    REJECTED("已退回"),
    RELEASED("已发布");

    private final String label;
    SampleStatus(String label) { this.label = label; }
    public String getLabel() { return label; }
}
