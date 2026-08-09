package com.mlms.oes.common.api.dto;

import com.mlms.oes.common.api.enums.ResultType;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 统一检验结果数据传输对象。
 * <p>
 * 本系统内所有仪器上报的检验结果均被转换为统一的 {@code UnifiedResult} 结构，
 * 屏蔽不同仪器、不同协议的差异，供下游业务模块（如审核、报告、LIS 对接）消费。
 * </p>
 * <p>
 * 支持的结果类型包括：血培养阳性标志、菌种鉴定、药敏结果、质控结果。
 * 具体类型由 {@code resultType} 字段标识。
 * </p>
 *
 * @author MLMS Team
 */
@Data
public class UnifiedResult {

    /** 仪器唯一标识，用于追踪结果来源 */
    private String instrumentId;

    /** 样本条码 */
    private String sampleBarcode;

    /** 患者 ID */
    private String patientId;

    /** 患者姓名 */
    private String patientName;

    /** 病例/申请单号 */
    private String caseId;

    /** 结果类型枚举：血培养阳性标志 / 菌种鉴定 / 药敏 / 质控 */
    private ResultType resultType;

    /** 菌种代码（鉴定结果场景） */
    private String organismCode;

    /** 菌种名称（鉴定结果场景） */
    private String organismName;

    /** 鉴定置信度百分比，取值范围 0.0 ~ 100.0 */
    private Double identificationPercent;

    /** 药敏结果明细列表（药敏结果场景） */
    private List<AstResultDTO> astResults;

    /** 检验时间 */
    private LocalDateTime testTime;

    /** 仪器上报的原始报文文本 */
    private String rawMessage;
}
