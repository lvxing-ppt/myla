package com.myla.gateway.core.model;

import lombok.Data;

@Data
public class ConnectionError {
    private String channelType;
    private String message;
    private Throwable cause;
}
