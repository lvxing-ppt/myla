package com.myla.gateway.core.context;

import lombok.Data;
import java.util.Map;

@Data
public class DriverConfig {
    private String driverId;
    private String instrumentId;
    private ChannelConfig channel;
    private String splitterType;
    private String parserType;
    private Map<String, Object> properties;

    @Data
    public static class ChannelConfig {
        private String type;
        private String host;
        private int port;
        private String directory;
        private String filePattern;
        private int pollIntervalMs = 5000;
        private String serialPort;
        private int baudRate = 9600;
        private long reconnectDelayMs = 1000;
    }
}
