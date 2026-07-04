package com.myla.common.core.exception;

import com.myla.common.core.constant.ResultCode;

/**
 * 数据解析异常类。
 * <p>
 * 继承自 {@link BusinessException}，专门用于表示仪器原始报文解析失败的情况。
 * 额外携带原始报文文本，方便开发人员回溯分析解析失败的原因。
 * </p>
 *
 * @author MyLA Team
 */
public class ParseException extends BusinessException {

    /** 解析失败时的原始报文文本 */
    private final String rawText;

    /**
     * 构造解析异常实例。
     * @param rawText 原始报文文本（解析失败的数据）
     * @param errorDetail 详细的错误描述，说明解析失败的具体原因
     */
    public ParseException(String rawText, String errorDetail) {
        super(ResultCode.PARSE_ERROR, errorDetail);
        this.rawText = rawText;
    }
}
