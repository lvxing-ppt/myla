package com.myla.common.api.dto;

import lombok.Data;

@Data
public class AstResultDTO {
    private String antibioticCode;
    private String antibioticName;
    private Double micValue;
    private String micUnit;
    private String sirInterpretation;
    private String machineSIR;
    private String manualSIR;
    private String expertRuleComment;
}
