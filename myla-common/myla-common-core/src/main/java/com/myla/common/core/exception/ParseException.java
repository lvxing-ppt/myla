package com.myla.common.core.exception;

import com.myla.common.core.constant.ResultCode;

public class ParseException extends BusinessException {
    private final String rawText;

    public ParseException(String rawText, String errorDetail) {
        super(ResultCode.PARSE_ERROR, errorDetail);
        this.rawText = rawText;
    }
}
