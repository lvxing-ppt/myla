package com.mlms.oes.lis.outbound;

import com.mlms.oes.lis.entity.LisConfig;
import com.mlms.oes.result.entity.AstResult;
import com.mlms.oes.result.entity.OrganismResult;
import com.mlms.oes.sample.entity.Sample;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * HL7 消息构造器。
 * <p>
 * 将内部结果数据（OrganismResult + AstResult + Sample）构造为 HL7 ORU^R01 消息。
 * 使用管道符（|）分隔的 HL7 v2.5 格式。
 * </p>
 *
 * <h3>消息结构：</h3>
 * <pre>
 * MSH — 消息头
 * PID — 患者信息（来自 Sample）
 * OBR — 检验医嘱（条码、标本类型）
 * OBX — 检验结果（菌种名称、药敏 MIC/SIR）
 * </pre>
 */
public class Hl7MessageBuilder {

    private static final DateTimeFormatter HL7_DTM = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * 构造 HL7 ORU^R01 消息。
     *
     * @param orgResult 细菌鉴定结果
     * @param asts      药敏结果列表
     * @param sample    样本信息
     * @param config    该医院的 LIS 配置（用于 result_mapping 等）
     * @return HL7 ORU^R01 消息字符串
     */
    public String buildOruR01(OrganismResult orgResult, List<AstResult> asts,
                               Sample sample, LisConfig config) {
        String now = LocalDateTime.now().format(HL7_DTM);
        String msgControlId = UUID.randomUUID().toString().replace("-", "");

        StringBuilder sb = new StringBuilder();

        // ---- MSH ----
        sb.append("MSH|^~\\&|MLMS|MLMS|LIS|").append(sample.getWardCode() != null ? sample.getWardCode() : "")
                .append("|").append(now).append("||ORU^R01|").append(msgControlId)
                .append("|P|2.5|||||UTF-8\r");

        // ---- PID ----
        sb.append("PID|1|").append(nullToEmpty(sample.getPatientId()))
                .append("|||").append(nullToEmpty(sample.getPatientName()))
                .append("||||").append(nullToEmpty(sample.getGender()))
                .append("||").append(sample.getAge() != null ? sample.getAge() : "")
                .append("||||||||||||\r");

        // ---- OBR ----
        sb.append("OBR|1||").append(nullToEmpty(sample.getBarcode()))
                .append("||").append(orgResult.getOrganismCode() != null ? orgResult.getOrganismCode() : "")
                .append("^").append(orgResult.getOrganismName() != null ? orgResult.getOrganismName() : "")
                .append("|||||||||||").append(nullToEmpty(sample.getSpecimenType()))
                .append("||||||||\r");

        // ---- OBX for organism ----
        sb.append("OBX|1|ST|ORGANISM||").append(nullToEmpty(orgResult.getOrganismName()))
                .append("||||||F|||").append(now).append("\r");

        // ---- OBX for identification percent ----
        if (orgResult.getIdentificationPercent() != null) {
            sb.append("OBX|2|NM|CONFIDENCE||").append(orgResult.getIdentificationPercent())
                    .append("|%|||||F|||").append(now).append("\r");
        }

        // ---- OBX for each AST result ----
        int obxSeq = 3;
        if (asts != null) {
            for (AstResult ast : asts) {
                sb.append("OBX|").append(obxSeq++).append("|ST|AST^")
                        .append(nullToEmpty(ast.getAntibioticName())).append("||")
                        .append(ast.getMicValue() != null ? ast.getMicValue() : "")
                        .append("|").append(ast.getMicUnit() != null ? ast.getMicUnit() : "ug/mL")
                        .append("|").append(nullToEmpty(ast.getFinalSir()))
                        .append("||||F|||").append(now).append("\r");
            }
        }

        return sb.toString();
    }

    private String nullToEmpty(String s) { return s != null ? s : ""; }
}
