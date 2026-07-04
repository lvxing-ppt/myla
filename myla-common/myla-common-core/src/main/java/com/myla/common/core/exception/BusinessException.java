package com.myla.common.core.exception;

import com.myla.common.core.constant.ResultCode;
import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final int code;
    private final String message;

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
        this.message = resultCode.getMessage();
    }

    public BusinessException(ResultCode resultCode, String detail) {
        super(detail);
        this.code = resultCode.getCode();
        this.message = detail;
    }
}
