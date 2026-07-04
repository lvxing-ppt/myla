package com.myla.common.core.exception;

public class InstrumentException extends BusinessException {
    private final String instrumentId;
    private final int consecutiveFailures;

    public InstrumentException(String instrumentId, String message, int consecutiveFailures) {
        super(ResultCode.INSTRUMENT_ERROR, message);
        this.instrumentId = instrumentId;
        this.consecutiveFailures = consecutiveFailures;
    }
}
