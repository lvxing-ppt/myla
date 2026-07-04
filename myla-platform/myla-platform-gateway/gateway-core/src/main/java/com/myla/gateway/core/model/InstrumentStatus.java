package com.myla.gateway.core.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class InstrumentStatus {
    private String instrumentId;
    private Status status;
    private String message;
    private LocalDateTime timestamp = LocalDateTime.now();

    public enum Status { ONLINE, OFFLINE, BUSY, ERROR, MAINTENANCE }
}
