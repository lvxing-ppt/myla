package com.myla.common.core.exception;

import com.myla.common.core.constant.ResultCode;
import lombok.Getter;

/**
 * 业务异常类。
 * <p>
 * 系统中的所有非预期业务情况均通过抛出此异常或其子类来表示。
 * 包含业务状态码和详细消息，由 {@link GlobalExceptionHandler} 统一拦截并转换为标准 API 响应。
 * </p>
 *
 * @author MyLA Team
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 业务状态码 */
    private final int code;

    /** 异常消息 */
    private final String message;

    /**
     * 使用预定义的 {@link ResultCode} 构造异常。
     * @param resultCode 预定义的结果码枚举值
     */
    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
        this.message = resultCode.getMessage();
    }

    /**
     * 使用预定义的 {@link ResultCode} 构造异常，并覆盖默认消息。
     * @param resultCode 预定义的结果码枚举值（取其 code）
     * @param detail 自定义详细消息，替换 resultCode 的默认消息
     */
    public BusinessException(ResultCode resultCode, String detail) {
        super(detail);
        this.code = resultCode.getCode();
        this.message = detail;
    }
}
