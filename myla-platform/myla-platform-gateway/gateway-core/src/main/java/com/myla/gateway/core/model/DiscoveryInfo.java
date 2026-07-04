package com.myla.gateway.core.model;

import lombok.Data;
import java.util.List;

@Data
public class DiscoveryInfo {
    private String manufacturer;
    private String model;
    private String serialNumber;
    private String firmwareVersion;
    private String hardwareRevision;
    private List<String> supportedCommands;
}
