package com.mlms.oes.lis.inbound;

import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.v25.segment.PID;
import ca.uhn.hl7v2.model.v25.segment.PV1;
import ca.uhn.hl7v2.parser.PipeParser;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * HL7 ADT 患者信息更新解析器。
 * <p>
 * 基于 HAPI 解析 HL7 ADT^A04（患者注册）、ADT^A08（患者信息更新）等消息，
 * 提取患者基本信息变更字段。
 * </p>
 *
 * <h3>支持的 ADT 消息类型：</h3>
 * <ul>
 *   <li>ADT^A04 — 患者注册（Register a patient）</li>
 *   <li>ADT^A08 — 患者信息更新（Update patient information）</li>
 * </ul>
 */
@Slf4j
public class Hl7AdtParser {

    private final HapiContext context = new DefaultHapiContext();
    private final PipeParser parser = context.getPipeParser();

    /**
     * 解析 HL7 ADT 消息，提取变更字段。
     *
     * @param hl7Message HL7 消息字符串
     * @return Map<Sample字段名, 新值>，如 {"patientName": "张三", "gender": "M"}
     *         以及固定的 "patientId" 用于查找目标 Sample
     */
    public Map<String, String> parse(String hl7Message) throws Exception {
        Map<String, String> updates = new HashMap<>();

        Message msg = parser.parse(hl7Message);

        // ---- PID 段 ----
        try {
            PID pid = (PID) msg.get("PID");
            String patientId = pid.getPid3_PatientIdentifierList(0).getIDNumber().getValue();
            if (patientId != null) updates.put("patientId", patientId);

            String name = pid.getPid5_PatientName(0).getFamilyName().getSurname().getValue();
            if (name != null) updates.put("patientName", name);

            String gender = pid.getPid8_AdministrativeSex().getValue();
            if (gender != null) updates.put("gender", gender);
        } catch (Exception e) {
            log.debug("PID segment parse skipped: {}", e.getMessage());
        }

        // ---- PV1 段 ----
        try {
            PV1 pv1 = (PV1) msg.get("PV1");
            String wardCode = pv1.getPv13_AssignedPatientLocation().getPointOfCare().getValue();
            if (wardCode != null) updates.put("wardCode", wardCode);

            String wardName = pv1.getPv13_AssignedPatientLocation().getRoom().getValue();
            if (wardName != null) updates.put("wardName", wardName);
        } catch (Exception e) {
            log.debug("PV1 segment parse skipped: {}", e.getMessage());
        }

        return updates;
    }
}
