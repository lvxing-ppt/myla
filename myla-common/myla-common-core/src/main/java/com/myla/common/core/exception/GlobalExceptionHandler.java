package com.myla.common.core.exception;

import com.myla.common.core.constant.ResultCode;
import com.myla.common.core.util.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public R<Void> handleBusinessException(BusinessException e) {
        log.warn("Business exception: code={}, message={}", e.getCode(), e.getMessage());
        return R.fail(ResultCode.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(ParseException.class)
    public R<Void> handleParseException(ParseException e) {
        log.error("Parse error: {}", e.getMessage());
        return R.fail(ResultCode.PARSE_ERROR, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public R<Void> handleException(Exception e) {
        log.error("Unexpected error", e);
        return R.fail(ResultCode.INTERNAL_ERROR);
    }
}
