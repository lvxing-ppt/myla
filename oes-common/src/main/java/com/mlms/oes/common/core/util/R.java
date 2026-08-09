package com.mlms.oes.common.core.util;

import com.mlms.oes.common.core.constant.ResultCode;
import lombok.Data;

/**
 * 统一 API 响应封装类。
 * <p>
 * 所有 Controller 方法的返回值均使用此类进行包装，确保前端接收到的 JSON 结构一致。
 * 泛型 {@code T} 代表响应数据的类型，无数据时使用 {@code R<Void>}。
 * </p>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 成功返回数据
 * return R.ok(sample);
 *
 * // 成功返回（无数据）
 * return R.ok();
 *
 * // 失败返回（使用预定义状态码）
 * return R.fail(ResultCode.SAMPLE_NOT_FOUND);
 *
 * // 失败返回（自定义消息）
 * return R.fail(ResultCode.PARSE_ERROR, "ASTM报文头部缺失");
 * }</pre>
 *
 * @param <T> 响应数据的类型
 * @author MLMS Team
 */
@Data
public class R<T> {

    /** HTTP 状态码 / 业务状态码 */
    private int code;

    /** 操作结果消息 */
    private String message;

    /** 响应数据体，可为 null */
    private T data;

    private R(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * 构建成功响应（携带数据）。
     * @param data 响应数据
     * @param <T> 数据类型
     * @return 包含数据的成功响应
     */
    public static <T> R<T> ok(T data) {
        return new R<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    /**
     * 构建成功响应（无数据体）。
     * @param <T> 数据类型（通常为 Void）
     * @return 无数据的成功响应
     */
    public static <T> R<T> ok() {
        return ok(null);
    }

    /**
     * 构建失败响应（使用默认消息）。
     * @param code 预定义的结果码
     * @param <T> 数据类型（通常为 Void）
     * @return 失败响应
     */
    public static <T> R<T> fail(ResultCode code) {
        return new R<>(code.getCode(), code.getMessage(), null);
    }

    /**
     * 构建失败响应（自定义详细消息）。
     * @param code 预定义的结果码（取其 code 值）
     * @param detail 自定义详细错误消息
     * @param <T> 数据类型（通常为 Void）
     * @return 带自定义消息的失败响应
     */
    public static <T> R<T> fail(ResultCode code, String detail) {
        return new R<>(code.getCode(), detail, null);
    }
}
