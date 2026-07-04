package com.myla.gateway.core.model;

import lombok.Data;
import java.util.Map;
import java.util.UUID;

@Data
public class InstrumentCommand {
    private String commandId = UUID.randomUUID().toString();
    private String instrumentId;
    private CommandType type;
    private Map<String, Object> parameters;
    private int timeoutSeconds = 30;

    public enum CommandType { START_TEST, STOP_TEST, SELECT_CARD, QUERY_STATUS, SEND_ORDER }
}
