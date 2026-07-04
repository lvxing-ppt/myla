package com.myla.gateway.core.model;

import lombok.Data;
import java.util.Map;
import java.util.UUID;

@Data
public class MaintenanceCommand {
    private String commandId = UUID.randomUUID().toString();
    private String instrumentId;
    private MaintenanceCapability capability;
    private Map<String, Object> parameters;
}
