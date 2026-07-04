package com.myla.gateway.core.model;

import lombok.Data;
import java.util.Map;

@Data
public class CommandResult {
    private String commandId;
    private CommandStatus status;
    private Map<String, Object> output;
    private String errorMessage;
    private long elapsedMs;

    public enum CommandStatus { ACCEPTED, EXECUTING, COMPLETED, FAILED, TIMEOUT }
}
