package com.myla.common.api.dto;

import com.myla.common.api.enums.ResultType;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class UnifiedResult {
    private String instrumentId;
    private String sampleBarcode;
    private String patientId;
    private String patientName;
    private String caseId;
    private ResultType resultType;
    private String organismCode;
    private String organismName;
    private Double identificationPercent;
    private List<AstResultDTO> astResults;
    private LocalDateTime testTime;
    private String rawMessage;
}
