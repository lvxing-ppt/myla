package com.mlms.oes.lis.inbound;

import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.v25.message.ORM_O01;
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
 * 基于 HAPI 解析 HL7 v2.5 ORM^O01 消息（检验医嘱），
 * 从 PATIENT 组下的 PID、ORDER 组下的 OBR、PV1、DG1 段提取 Sample 字段。
 * </p>
 */
@Slf4j
public class Hl7OrderParser {

    private final HapiContext context = new DefaultHapiContext();
    private final Parser parser = context.getPipeParser();

    public Map<String, String> parse(String hl7Message) throws Exception {
        Map<String, String> fields = FieldMapper.newRawFields();
        ORM_O01 msg = (ORM_O01) parser.parse(hl7Message);

        // ---- PATIENT → PID（优先用结构访问，失败则按行解析） ----
        try {
            PID pid = msg.getPATIENT().getPID();
            putIf(fields, "PID-3-1", pid.getPid3_PatientIdentifierList(0).getIDNumber().getValue());
            putIf(fields, "PID-5-1", pid.getPid5_PatientName(0).getFamilyName().getSurname().getValue());
            putIf(fields, "PID-5-2", pid.getPid5_PatientName(0).getGivenName().getValue());
            putIf(fields, "PID-7-1", pid.getPid7_DateTimeOfBirth().getTime().getValue());
            String gender = pid.getPid8_AdministrativeSex().getValue();
            if (gender != null && gender.length() > 1) gender = gender.substring(0, 1);
            putIf(fields, "PID-8-1", gender);
        } catch (Exception e) {
            log.warn("PID via HAPI failed ({}), trying line parse", e.getMessage());
            parsePidByLine(hl7Message, fields);
        }

        // ---- ORDER → OBR / PV1 / DG1 ----
        try {
            var order = msg.getORDER();
            OBR obr = (OBR) order.get("OBR");
            if (obr != null) {
                putIf(fields, "OBR-2-1", getEiValue(obr.getObr2_PlacerOrderNumber()));
                putIf(fields, "OBR-3-1", getEiValue(obr.getObr3_FillerOrderNumber()));
                putIf(fields, "OBR-4-1", obr.getObr4_UniversalServiceIdentifier().getIdentifier().getValue());
                putIf(fields, "OBR-4-2", obr.getObr4_UniversalServiceIdentifier().getText().getValue());
                putIf(fields, "OBR-7-1", obr.getObr7_ObservationDateTime().getTime().getValue());
                putIf(fields, "OBR-15-1", obr.getObr15_SpecimenSource().getSps1_SpecimenSourceNameOrCode().getText().getValue());
            }
            PV1 pv1 = null; DG1 dg1 = null;
            try { pv1 = (PV1) order.get("PV1"); } catch (Exception e) { /* ignore */ }
            if (pv1 != null) {
                putIf(fields, "PV1-3-1", pv1.getPv13_AssignedPatientLocation().getPointOfCare().getValue());
            }
            try { dg1 = (DG1) order.get("DG1"); } catch (Exception e) { /* ignore */ }
            if (dg1 != null) {
                putIf(fields, "DG1-3-1", dg1.getDg13_DiagnosisCodeDG1().getIdentifier().getValue());
            }
        } catch (Exception e) {
            log.warn("ORDER parse failed ({}), using line fallback", e.getMessage());
            parseByLine(hl7Message, fields);
        }

        return fields;
    }

    /** 从 EI 类型安全提取值 */
    private String getEiValue(ca.uhn.hl7v2.model.v25.datatype.EI ei) {
        try { return ei.getEntityIdentifier().getValue(); }
        catch (Exception e) { return null; }
    }

    private void putIf(Map<String, String> m, String k, String v) {
        if (v != null && !v.isBlank()) m.put(k, v);
    }

    /** 回退方案：按行解析 HL7 消息（管道符分割，不依赖 HAPI 组结构） */
    private void parseByLine(String hl7, Map<String, String> fields) {
        for (String line : hl7.split("\r")) {
            String[] f = line.split("\\|");
            switch (f[0]) {
                case "PID":
                    if (f.length > 3) putIf(fields, "PID-3-1", f[3]);
                    if (f.length > 5) putIf(fields, "PID-5-1", f[5]);
                    if (f.length > 6) putIf(fields, "PID-5-2", f[6]);
                    if (f.length > 8) putIf(fields, "PID-7-1", f[8]);
                    if (f.length > 9) putIf(fields, "PID-8-1", f[9]);
                    break;
                case "OBR":
                    if (f.length > 3) putIf(fields, "OBR-2-1", f[2] + "-1"); // Placer is index 2
                    if (f.length > 3) putIf(fields, "OBR-3-1", f[3]);        // Filler is index 3 (barcode)
                    if (f.length > 5) putIf(fields, "OBR-4-1", f[4]);
                    if (f.length > 8) putIf(fields, "OBR-7-1", f[7]);
                    if (f.length > 16) putIf(fields, "OBR-15-1", f[15]);
                    break;
                case "PV1":
                    if (f.length > 3) putIf(fields, "PV1-3-1", f[3]);
                    if (f.length > 4) putIf(fields, "PV1-3-2", f[4]);
                    break;
                case "DG1":
                    if (f.length > 4) putIf(fields, "DG1-3-1", f[3]);
                    if (f.length > 5) putIf(fields, "DG1-3-2", f[4]);
                    break;
            }
        }
    }

    /** 回退方案：按行解析 PID 段（管道符分割） */
    private void parsePidByLine(String hl7, Map<String, String> fields) {
        try {
            for (String line : hl7.split("\r")) {
                if (line.startsWith("PID")) {
                    String[] f = line.split("\\|");
                    if (f.length > 3) putIf(fields, "PID-3-1", f[3]);
                    if (f.length > 5) putIf(fields, "PID-5-1", f[5]);
                    if (f.length > 6) putIf(fields, "PID-5-2", f[6]);
                    if (f.length > 8) putIf(fields, "PID-7-1", f[8]);
                    if (f.length > 9) putIf(fields, "PID-8-1", f[9]);
                    break;
                }
            }
        } catch (Exception e) {
            log.warn("PID line parse also failed: {}", e.getMessage());
        }
    }

    /**
     * 从 PID-7（出生日期）换算年龄。
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
