package com.mlms.oes.common.core.constant;

import lombok.Getter;

/**
 * 统一结果码枚举。
 * <p>
 * 定义系统所有 API 响应的状态码及其对应的默认消息。
 * 编码规则：
 * <ul>
 *   <li>1xxx — 仪器/网关相关错误</li>
 *   <li>2xxx — 业务数据相关错误</li>
 *   <li>3xxx — LIS 系统对接相关错误</li>
 *   <li>2xx / 4xx / 5xx — 遵循 HTTP 标准状态码语义</li>
 * </ul>
 * 与 {@link com.mlms.oes.common.core.util.R} 配合使用，构成统一的 API 响应格式。
 * </p>
 *
 * @author MLMS Team
 */
@Getter
public enum ResultCode {

    /** 操作成功 */
    SUCCESS(200, "操作成功"),

    /** 请求参数错误（参数校验失败） */
    BAD_REQUEST(400, "请求参数错误"),

    /** 未授权（未登录或 token 过期） */
    UNAUTHORIZED(401, "未授权"),

    /** 无权限（已登录但无访问权限） */
    FORBIDDEN(403, "无权限"),

    /** 资源不存在 */
    NOT_FOUND(404, "资源不存在"),

    /** 系统内部错误（未知异常） */
    INTERNAL_ERROR(500, "系统内部错误"),

    // ---- 仪器/网关错误 (1xxx) ----

    /** 仪器连接异常（TCP 连接失败、超时等） */
    INSTRUMENT_CONNECTION_ERROR(1001, "仪器连接异常"),

    /** 仪器返回错误（仪器主动上报的错误） */
    INSTRUMENT_ERROR(1002, "仪器错误"),

    /** 数据解析失败（报文格式不符合预期） */
    PARSE_ERROR(1003, "数据解析失败"),

    /** 驱动加载失败（SPI 驱动初始化异常） */
    DRIVER_LOAD_ERROR(1004, "驱动加载失败"),

    // ---- 业务数据错误 (2xxx) ----

    /** 样本不存在 */
    SAMPLE_NOT_FOUND(2001, "样本不存在"),

    /** 检验结果不存在 */
    RESULT_NOT_FOUND(2002, "结果不存在"),

    /** 条码重复（同一条码已录入系统） */
    DUPLICATE_BARCODE(2003, "条码重复"),

    /** 样本状态异常，不允许当前操作（如已审核后不能再修改结果） */
    INVALID_SAMPLE_STATUS(2004, "样本状态异常不允许此操作"),

    // ---- LIS 对接错误 (3xxx) ----

    /** 向 LIS 发送结果失败 */
    LIS_SEND_FAILED(3001, "LIS发送失败"),

    /** 从 LIS 解析医嘱信息失败 */
    LIS_ORDER_PARSE_ERROR(3002, "LIS医嘱解析失败");

    /** 状态码 */
    private final int code;

    /** 默认消息 */
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
