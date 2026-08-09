package com.mlms.oes.lis.inbound;

import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.v25.segment.*;
import ca.uhn.hl7v2.parser.Parser;
import ca.uhn.hl7v2.parser.PipeParser;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * HL7 ORM^O01 医嘱消息解析器。
 * <p>
 * 基于 HAPI 解析 HL7 v2.x ORM^O01 消息（检验医嘱），
 * 从 PID、OBR、PV1、DG1 段提取 Sample 所需字段。
 * </p>
 *
 * <h3>提取的字段（写入 rawFields）：</h3>
 * <ul>
 *   <li>PID-3-1  → patientId</li>
 *   <li>PID-5-1  → patientName</li>
   <li>PID-8-1  → gender</li>
 *   <li>PID-7-1  → age</li>
 *   <li>OBR-2-1  → barcode</li>
 *   <li>OBR-15-1 → specimenType</li>
 *   <li>OBR-7-1  → collectTime</li>
 *   <li>PV1-3-1  → wardCode</li>
 *   <li>DG1-3-1  → diagnosis</li>
 * </ul>
 */
@Slf4j
public class Hl7OrderParser {

    private final HapiContext context = new DefaultHapiContext();
    private final Parser parser = context.getPipeParser();

    /**
     * 解析 HL7 ORM^O01 消息，提取原始字段。
     *
     * @param hl7Message HL7 消息字符串（管道符分隔格式）
     * @return Map<字段路径, 值>，如 {"PID-3-1": "P12345", "OBR-2-1": "BC20260809001"}
     * @throws ca.uhn.hl7v2.HL7Exception 如果消息格式不符合 HL7 标准
     */
    public Map<String, String> parse(String hl7Message) throws Exception {
        Map<String, String> fields = FieldMapper.newRawFields();

        Message msg = parser.parse(hl7Message);

        // ---- PID 段 ----
        try {
            PID pid = (PID) msg.get("PID");
            fields.put("PID-3-1", pid.getPid3_PatientIdentifierList(0).getIDNumber().getValue());
            fields.put("PID-5-1", pid.getPid5_PatientName(0).getFamilyName().getSurname().getValue());
            fields.put("PID-5-2", pid.getPid5_PatientName(0).getGivenName().getValue());
            fields.put("PID-7-1", pid.getPid7_DateTimeOfBirth().getTime().getValue());
            fields.put("PID-8-1", pid.getPid8_AdministrativeSex().getValue());
        } catch (Exception e) {
            log.debug("PID segment parse skipped: {}", e.getMessage());
        }

        // ---- OBR 段 ----
        try {
            OBR obr = (OBR) msg.get("OBR");
            fields.put("OBR-2-1",  obr.getObr2_PlacerOrderNumber().getEntityIdentifier().getValue());
            fields.put("OBR-3-1",  obr.getObr3_FillerOrderNumber().getEntityIdentifier().getValue());
            fields.put("OBR-4-1",  obr.getObr4_UniversalServiceIdentifier().getIdentifier().getValue());
            fields.put("OBR-4-2",  obr.getObr4_UniversalServiceIdentifier().getText().getValue());
            fields.put("OBR-7-1",  obr.getObr7_ObservationDateTime().getTime().getValue());
            fields.put("OBR-15-1", obr.getObr15_SpecimenSource().getSps1_SpecimenSourceNameOrCode().getText().getValue());
        } catch (Exception e) {
            log.debug("OBR segment parse skipped: {}", e.getMessage());
        }

        // ---- PV1 段 ----
        try {
            PV1 pv1 = (PV1) msg.get("PV1");
            fields.put("PV1-3-1", pv1.getPv13_AssignedPatientLocation().getPointOfCare().getValue());
            fields.put("PV1-3-2", pv1.getPv13_AssignedPatientLocation().getRoom().getValue());
        } catch (Exception e) {
            log.debug("PV1 segment parse skipped: {}", e.getMessage());
        }

        // ---- DG1 段 ----
        try {
            DG1 dg1 = (DG1) msg.get("DG1");
            fields.put("DG1-3-1", dg1.getDg13_DiagnosisCodeDG1().getIdentifier().getValue());
            fields.put("DG1-3-2", dg1.getDg13_DiagnosisCodeDG1().getText().getValue());
        } catch (Exception e) {
            log.debug("DG1 segment parse skipped: {}", e.getMessage());
        }

        return fields;
    }

    /**
     * 从 PID-7（出生日期）换算年龄。
     * 若 HAPI 提取的 PID-7 是日期格式，在 rawFields 中追加 age 字段。
     */
    public static void deriveAge(Map<String, String> fields) {
        String dob = fields.get("PID-7-1");
        if (dob != null && dob.length() >= 8) {
            try {
                LocalDateTime birth = LocalDateTime.parse(dob.substring(0, 8),
                        DateTimeFormatter.ofPattern("yyyyMMdd"));
                int age = java.time.Period.between(birth.toLocalDate(),
                        LocalDateTime.now().toLocalDate()).getYears();
                fields.put("age", String.valueOf(age));
            } catch (Exception e) {
                log.debug("Cannot derive age from DOB: {}", dob);
            }
        }
    }
}
