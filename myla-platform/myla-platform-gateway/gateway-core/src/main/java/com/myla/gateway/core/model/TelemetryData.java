package com.myla.gateway.core.model;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class TelemetryData {
    private LocalDateTime timestamp = LocalDateTime.now();
    private Double cpuTemp;
    private Double ambientTemp;
    private Double humidity;
    private String powerStatus;
    private Integer reagentRemaining;
    private Long uptimeSeconds;
    private List<String> activeFaults;
}
