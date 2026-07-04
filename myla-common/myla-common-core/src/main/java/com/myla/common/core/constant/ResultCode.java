package com.myla.common.core.constant;

import lombok.Getter;

@Getter
public enum ResultCode {
    SUCCESS(200, "操作成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未授权"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "系统内部错误"),

    INSTRUMENT_CONNECTION_ERROR(1001, "仪器连接异常"),
    INSTRUMENT_ERROR(1002, "仪器错误"),
    PARSE_ERROR(1003, "数据解析失败"),
    DRIVER_LOAD_ERROR(1004, "驱动加载失败"),

    SAMPLE_NOT_FOUND(2001, "样本不存在"),
    RESULT_NOT_FOUND(2002, "结果不存在"),
    DUPLICATE_BARCODE(2003, "条码重复"),
    INVALID_SAMPLE_STATUS(2004, "样本状态异常不允许此操作"),

    LIS_SEND_FAILED(3001, "LIS发送失败"),
    LIS_ORDER_PARSE_ERROR(3002, "LIS医嘱解析失败");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
