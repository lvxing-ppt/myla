package com.myla.lis.inbound;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myla.sample.entity.Sample;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.Map;

/**
 * LIS 字段映射器。
 * <p>
 * 根据 lis_config.order_mapping JSON 将 HL7/ASTM 原始字段名映射到 Sample 实体字段。
 * 不同医院 LIS 的字段位置不同（如 A 医院的条码在 OBR-3，B 医院在 OBR-31），
 * 通过 JSON 配置灵活适配，无需修改代码。
 * </p>
 *
 * <h3>order_mapping JSON 格式示例：</h3>
 * <pre>{@code
 * {
 *   "barcode": "OBR-3-1",
 *   "patientId": "PID-3-1",
 *   "patientName": "PID-5-1",
 *   "gender": "PID-8-1",
 *   "age": "PID-7-1",
 *   "specimenType": "OBR-15-1",
 *   "collectTime": "OBR-7-1",
 *   "wardCode": "PV1-3-1",
 *   "wardName": "PV1-3-2",
 *   "diagnosis": "DG1-3-1"
 * }
 * }</pre>
 */
@Slf4j
public class FieldMapper {

    private static final ObjectMapper json = new ObjectMapper();

    /**
     * 将原始 HL7 字段映射到 Sample 实体。
     *
     * @param sample      待填充的 Sample（已有部分默认值）
     * @param rawFields   从 HL7 消息中提取的原始字段 Map<字段路径, 值>
     * @param mappingJson lis_config.order_mapping JSON 字符串
     * @return 填充后的 Sample
     */
    public Sample apply(Sample sample, Map<String, String> rawFields, String mappingJson) {
        if (mappingJson == null || mappingJson.isBlank()) {
            log.debug("No order_mapping configured, using raw fields as-is");
            return sample;
        }

        try {
            Map<String, String> mapping = json.readValue(mappingJson,
                    new TypeReference<Map<String, String>>() {});

            // 根据映射配置，从 rawFields 中取值设置到 Sample
            setIfPresent(sample, rawFields, mapping, "barcode",         "barcode");
            setIfPresent(sample, rawFields, mapping, "patientId",       "patientId");
            setIfPresent(sample, rawFields, mapping, "patientName",     "patientName");
            setIfPresent(sample, rawFields, mapping, "gender",          "gender");
            setAgeIfPresent(sample, rawFields, mapping);
            setIfPresent(sample, rawFields, mapping, "specimenType",    "specimenType");
            setIfPresent(sample, rawFields, mapping, "collectTime",     "collectTime");
            setIfPresent(sample, rawFields, mapping, "wardCode",        "wardCode");
            setIfPresent(sample, rawFields, mapping, "wardName",        "wardName");
            setIfPresent(sample, rawFields, mapping, "diagnosis",       "diagnosis");
            setIfPresent(sample, rawFields, mapping, "priority",        "priority");
            setIfPresent(sample, rawFields, mapping, "comment",         "comment");

        } catch (Exception e) {
            log.warn("Failed to apply order_mapping: {}", e.getMessage());
        }

        return sample;
    }

    /**
     * 根据映射配置从 rawFields 取值，设置到 Sample 的 String 类型字段。
     */
    private void setIfPresent(Sample sample, Map<String, String> rawFields,
                              Map<String, String> mapping,
                              String sampleField, String rawKey) {
        String lisPath = mapping.get(sampleField);
        if (lisPath != null) {
            String value = rawFields.get(lisPath);
            if (value != null && !value.isBlank()) {
                switch (sampleField) {
                    case "barcode"      -> sample.setBarcode(value);
                    case "patientId"    -> sample.setPatientId(value);
                    case "patientName"  -> sample.setPatientName(value);
                    case "gender"       -> sample.setGender(value);
                    case "specimenType" -> sample.setSpecimenType(value);
                    case "wardCode"     -> sample.setWardCode(value);
                    case "wardName"     -> sample.setWardName(value);
                    case "diagnosis"    -> sample.setDiagnosis(value);
                    case "priority"     -> sample.setPriority(value);
                    case "comment"      -> sample.setComment(value);
                }
            }
        }
    }

    /**
     * 设置年龄字段（需要 String → Integer 转换）。
     */
    private void setAgeIfPresent(Sample sample, Map<String, String> rawFields,
                                 Map<String, String> mapping) {
        String lisPath = mapping.get("age");
        if (lisPath != null) {
            String value = rawFields.get(lisPath);
            if (value != null && !value.isBlank()) {
                try {
                    sample.setAge(Integer.parseInt(value.trim()));
                } catch (NumberFormatException e) {
                    log.debug("Cannot parse age from value: {}", value);
                }
            }
        }
    }

    /**
     * 返回空的 rawFields Map 模板，供 Parser 往里面填充。
     */
    public static Map<String, String> newRawFields() {
        return new java.util.HashMap<>();
    }
}
