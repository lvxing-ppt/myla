package com.myla.common.core.exception;

import com.myla.common.core.constant.ResultCode;
import com.myla.common.core.util.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器。
 * <p>
 * 使用 Spring {@code @RestControllerAdvice} 统一捕获 Controller 层抛出的异常，
 * 并转换为标准 JSON 响应格式 {@link R}。
 * 根据异常类型进行差异化处理：
 * <ul>
 *   <li>{@link BusinessException} — 业务异常，WARN 级别日志，返回对应业务状态码</li>
 *   <li>{@link ParseException} — 解析异常，ERROR 级别日志，返回解析错误状态码</li>
 *   <li>{@link Exception} — 兜底未知异常，ERROR 级别日志（含堆栈），返回 500 内部错误</li>
 * </ul>
 * </p>
 *
 * @author MyLA Team
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常 {@link BusinessException}。
     * @param e 业务异常实例
     * @return 包含错误码和消息的失败响应
     */
    @ExceptionHandler(BusinessException.class)
    public R<Void> handleBusinessException(BusinessException e) {
        log.warn("Business exception: code={}, message={}", e.getCode(), e.getMessage());
        return R.fail(ResultCode.BAD_REQUEST, e.getMessage());
    }

    /**
     * 处理数据解析异常 {@link ParseException}。
     * @param e 解析异常实例
     * @return 包含解析错误码和消息的失败响应
     */
    @ExceptionHandler(ParseException.class)
    public R<Void> handleParseException(ParseException e) {
        log.error("Parse error: {}", e.getMessage());
        return R.fail(ResultCode.PARSE_ERROR, e.getMessage());
    }

    /**
     * 处理所有未被上述处理器捕获的异常（兜底处理）。
     * @param e 未知异常实例
     * @return 包含内部错误码的失败响应
     */
    @ExceptionHandler(Exception.class)
    public R<Void> handleException(Exception e) {
        log.error("Unexpected error", e);
        return R.fail(ResultCode.INTERNAL_ERROR);
    }
}
