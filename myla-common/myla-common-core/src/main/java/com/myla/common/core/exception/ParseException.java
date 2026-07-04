package com.myla.common.core.exception;

public class ParseException extends BusinessException {
    private final String rawText;

    public ParseException(String rawText, String errorDetail) {
        super(ResultCode.PARSE_ERROR, errorDetail);
        this.rawText = rawText;
    }
}
