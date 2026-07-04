package com.myla.common.core.util;

import com.myla.common.core.constant.ResultCode;
import lombok.Data;

@Data
public class R<T> {
    private int code;
    private String message;
    private T data;

    private R(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> R<T> ok(T data) { return new R<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data); }
    public static <T> R<T> ok() { return ok(null); }
    public static <T> R<T> fail(ResultCode code) { return new R<>(code.getCode(), code.getMessage(), null); }
    public static <T> R<T> fail(ResultCode code, String detail) { return new R<>(code.getCode(), detail, null); }
}
