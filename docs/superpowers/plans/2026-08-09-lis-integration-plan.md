# LIS 对接完整实现 — 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现 myla-platform-lis 模块与医院 LIS/HIS 系统的双向对接——入站（接收HL7医嘱/患者更新）和出站（发送检验结果到外部LIS）

**Architecture:** 入站：LisInboundServer 按医院独立端口监听 TCP MLLP → HAPI 解析 HL7 → FieldMapper → SampleService.register()。出站：ResultReleasedConsumer 监听 RESULT_RELEASED_TO_LIS → Hl7MessageBuilder 构造 HL7 ORU^R01 → LisGatewayService 写 outbound_message → OutboundMessageConsumer 通过 Hl7MllpSender/AstmTcpSender/HttpSender 真实发送

**Tech Stack:** Java 17, Spring Boot, MyBatis-Plus, RabbitMQ, HAPI HL7 v2.5, Lombok

**Spec:** [2026-08-09-lis-integration-design.md](../specs/2026-08-09-lis-integration-design.md)

---

### Task 1: Add HAPI dependency

**Files:**
- Modify: `myla-platform/myla-platform-lis/pom.xml`

- [ ] **Step 1: Add HAPI dependencies**

Add inside `<dependencies>` after the existing `spring-boot-starter-amqp`:

```xml
<!-- HAPI HL7 v2.x parser -->
<dependency>
    <groupId>ca.uhn.hapi</groupId>
    <artifactId>hapi-base</artifactId>
    <version>2.3</version>
</dependency>
<dependency>
    <groupId>ca.uhn.hapi</groupId>
    <artifactId>hapi-structures-v25</artifactId>
    <version>2.3</version>
</dependency>
```

- [ ] **Step 2: Verify Maven resolves the dependency**

```bash
cd g:/myla && mvn dependency:resolve -pl myla-platform/myla-platform-lis -am -q 2>&1 | tail -5
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add myla-platform/myla-platform-lis/pom.xml
git commit -m "feat(lis): add HAPI HL7 v2.5 dependency"
```

---

### Task 2: Create LisConfigMapper

**Files:**
- Create: `myla-platform/myla-platform-lis/src/main/java/com/myla/lis/mapper/LisConfigMapper.java`

- [ ] **Step 1: Create LisConfigMapper**

```java
package com.myla.lis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.myla.lis.entity.LisConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * LIS 配置数据访问层。
 * 继承 MyBatis-Plus BaseMapper 获得内置 CRUD，
 * 提供按医院编码查询和查询所有已启用入站配置的方法。
 */
@Mapper
public interface LisConfigMapper extends BaseMapper<LisConfig> {

    /**
     * 根据医院编码查询 LIS 配置。
     * @param hospitalCode 医院编码
     * @return LIS 配置实体，不存在返回 null
     */
    @Select("SELECT * FROM lis_config WHERE hospital_code = #{hospitalCode}")
    LisConfig selectByHospitalCode(String hospitalCode);

    /**
     * 查询所有已启用的、配置了入站通道的 LIS 配置。
     * channel_type IN ('HL7', 'ASTM') AND enabled = 1
     * @return 已启用的入站配置列表
     */
    @Select("SELECT * FROM lis_config WHERE channel_type IN ('HL7','ASTM') AND enabled = 1")
    List<LisConfig> selectEnabledInbound();
}
```

- [ ] **Step 2: Verify compilation**

```bash
cd g:/myla && mvn compile -pl myla-platform/myla-platform-lis -am -q 2>&1 | tail -5
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add myla-platform/myla-platform-lis/src/main/java/com/myla/lis/mapper/LisConfigMapper.java
git commit -m "feat(lis): add LisConfigMapper for lis_config queries"
```

---

### Task 3: Update LisInboundService interface

**Files:**
- Modify: `myla-platform/myla-platform-lis/src/main/java/com/myla/lis/inbound/LisInboundService.java`

- [ ] **Step 1: Replace the interface — remove NoOp, add hospitalCode**

The entire file content:

```java
package com.myla.lis.inbound;

import com.myla.sample.entity.Sample;

/**
 * LIS 入站服务接口。
 * <p>
 * 定义从医院 LIS/HIS 系统接收医嘱和患者信息的标准契约。
 * 根据每家医院的通信方式（HL7 MLLP / ASTM / HTTP / 文件）实现对应的 Channel，
 * 解析后调用本接口的方法创建样本。
 * </p>
 *
 * <h3>接入方式：</h3>
 * <ol>
 *   <li>实现本接口的具体类（{@code LisInboundServiceImpl}）</li>
 *   <li>在 {@code lis_config} 表配置该院区的通信参数和字段映射</li>
 *   <li>启动对应的 LisInboundServer 监听 LIS 消息</li>
 *   <li>收到消息 → 根据 {@code lis_config.order_mapping} 做字段映射 → 调用本接口</li>
 * </ol>
 *
 * @author MyLA Team
 * @since 1.0
 */
public interface LisInboundService {

    /**
     * 从 LIS 接收检验医嘱，创建样本记录。
     * <p>对应 HL7 ORM^O01 或 ASTM 医嘱消息。</p>
     *
     * @param hospitalCode 医院编码（从监听端口映射获取）
     * @param rawMessage   原始 HL7/ASTM 消息字节
     * @param messageType  消息类型: HL7 / ASTM
     * @return 创建的样本实体（含自动生成的 sampleId）
     */
    Sample receiveOrder(String hospitalCode, byte[] rawMessage, String messageType);

    /**
     * 从 LIS 接收患者信息更新。
     * <p>对应 HL7 ADT^A04/A08 等消息类型。更新该患者所有非终态 Sample 的基本信息。</p>
     *
     * @param hospitalCode 医院编码
     * @param rawMessage   原始 HL7 消息字节
     */
    void receivePatientUpdate(String hospitalCode, byte[] rawMessage);

    /**
     * 根据条码查找已登记的样本。
     *
     * @param barcode 样本条码
     * @return 样本实体，不存在返回 null
     */
    Sample findByBarcode(String barcode);
}
```

- [ ] **Step 2: Verify compilation**

```bash
cd g:/myla && mvn compile -pl myla-platform/myla-platform-lis -am -q 2>&1 | tail -5
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add myla-platform/myla-platform-lis/src/main/java/com/myla/lis/inbound/LisInboundService.java
git commit -m "feat(lis): update LisInboundService — remove NoOp, add hospitalCode params"
```

---

### Task 4: Create FieldMapper

**Files:**
- Create: `myla-platform/myla-platform-lis/src/main/java/com/myla/lis/inbound/FieldMapper.java`

- [ ] **Step 1: Create FieldMapper**

```java
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
     *
     * @param sample    Sample 实体
     * @param rawFields 原始字段 Map
     * @param mapping   order_mapping 映射 Map
     * @param sampleField Sample 中的字段名（用于从 mapping 查 LIS 路径）
     * @param rawKey    写入 rawFields 时使用的 key（通常与 sampleField 相同）
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
```

- [ ] **Step 2: Verify compilation**

```bash
cd g:/myla && mvn compile -pl myla-platform/myla-platform-lis -am -q 2>&1 | tail -5
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add myla-platform/myla-platform-lis/src/main/java/com/myla/lis/inbound/FieldMapper.java
git commit -m "feat(lis): add FieldMapper for lis_config.order_mapping field mapping"
```

---

### Task 5: Create Hl7OrderParser

**Files:**
- Create: `myla-platform/myla-platform-lis/src/main/java/com/myla/lis/inbound/Hl7OrderParser.java`

- [ ] **Step 1: Create Hl7OrderParser**

```java
package com.myla.lis.inbound;

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
 *   <li>PID-8-1  → gender</li>
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
            fields.put("OBR-2-1",  obr.getObr2_PlacerOrderNumber(0).getEntityIdentifier().getValue());
            fields.put("OBR-3-1",  obr.getObr3_FillerOrderNumber(0).getEntityIdentifier().getValue());
            fields.put("OBR-4-1",  obr.getObr4_UniversalServiceIdentifier().getIdentifier().getValue());
            fields.put("OBR-4-2",  obr.getObr4_UniversalServiceIdentifier().getText().getValue());
            fields.put("OBR-7-1",  obr.getObr7_ObservationDateTime().getTime().getValue());
            fields.put("OBR-15-1", obr.getObr15_SpecimenSource().getText().getValue());
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
```

- [ ] **Step 2: Verify compilation**

```bash
cd g:/myla && mvn compile -pl myla-platform/myla-platform-lis -am -q 2>&1 | tail -5
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add myla-platform/myla-platform-lis/src/main/java/com/myla/lis/inbound/Hl7OrderParser.java
git commit -m "feat(lis): add Hl7OrderParser for HL7 ORM^O01 message parsing"
```

---

### Task 6: Create Hl7AdtParser

**Files:**
- Create: `myla-platform/myla-platform-lis/src/main/java/com/myla/lis/inbound/Hl7AdtParser.java`

- [ ] **Step 1: Create Hl7AdtParser**

```java
package com.myla.lis.inbound;

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
```

- [ ] **Step 2: Verify compilation**

```bash
cd g:/myla && mvn compile -pl myla-platform/myla-platform-lis -am -q 2>&1 | tail -5
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add myla-platform/myla-platform-lis/src/main/java/com/myla/lis/inbound/Hl7AdtParser.java
git commit -m "feat(lis): add Hl7AdtParser for HL7 ADT^A04/A08 message parsing"
```

---

### Task 7: Create LisInboundServiceImpl

**Files:**
- Create: `myla-platform/myla-platform-lis/src/main/java/com/myla/lis/inbound/LisInboundServiceImpl.java`

- [ ] **Step 1: Create LisInboundServiceImpl**

```java
package com.myla.lis.inbound;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.myla.lis.entity.LisConfig;
import com.myla.lis.mapper.LisConfigMapper;
import com.myla.sample.entity.Sample;
import com.myla.sample.service.SampleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

/**
 * LIS 入站服务实现。
 * <p>
 * 实现从医院 LIS/HIS 接收医嘱和患者信息更新的核心业务逻辑。
 * </p>
 *
 * <h3>receiveOrder 流程：</h3>
 * <ol>
 *   <li>HAPI 解析 HL7 消息为 rawFields</li>
 *   <li>衍算年龄（从 PID-7 出生日期）</li>
 *   <li>查 lis_config 获取该医院的 order_mapping</li>
 *   <li>FieldMapper 映射 → Sample</li>
 *   <li>设置 sourceSystem = "LIS"</li>
 *   <li>SampleService.register(sample)</li>
 * </ol>
 *
 * <h3>receivePatientUpdate 流程：</h3>
 * <ol>
 *   <li>HAPI 解析 ADT 消息为更新字段</li>
 *   <li>按 patientId 查找所有非终态 Sample</li>
 *   <li>批量更新 patientName/gender/ward 等</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LisInboundServiceImpl implements LisInboundService {

    private final SampleService sampleService;
    private final LisConfigMapper configMapper;

    private final Hl7OrderParser orderParser = new Hl7OrderParser();
    private final Hl7AdtParser adtParser = new Hl7AdtParser();
    private final FieldMapper fieldMapper = new FieldMapper();

    /** 终态集合 — 已结束的样本不再更新患者信息 */
    private static final Set<String> TERMINAL = Set.of(
            "COMPLETED", "REJECTED", "CULTURE_NEGATIVE", "CULTURE_CONTAMINATED");

    @Override
    public Sample receiveOrder(String hospitalCode, byte[] rawMessage, String messageType) {
        String hl7 = new String(rawMessage, StandardCharsets.UTF_8).trim();
        log.info("LIS inbound order from hospital={}, messageType={}, length={}",
                hospitalCode, messageType, hl7.length());

        try {
            // 1. HAPI 解析
            Map<String, String> rawFields = orderParser.parse(hl7);
            Hl7OrderParser.deriveAge(rawFields);

            // 2. 加载医院配置
            LisConfig config = configMapper.selectByHospitalCode(hospitalCode);

            // 3. 字段映射
            Sample sample = new Sample();
            sample.setSourceSystem("LIS");
            fieldMapper.apply(sample, rawFields,
                    config != null ? config.getOrderMapping() : null);

            // 4. 注册
            Sample saved = sampleService.register(sample);
            log.info("Sample created from LIS order: sampleId={}, barcode={}",
                    saved.getSampleId(), saved.getBarcode());
            return saved;

        } catch (Exception e) {
            log.error("Failed to process LIS order from hospital={}: {}", hospitalCode, e.getMessage());
            throw new RuntimeException("LIS order processing failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void receivePatientUpdate(String hospitalCode, byte[] rawMessage) {
        String hl7 = new String(rawMessage, StandardCharsets.UTF_8).trim();
        log.info("LIS ADT from hospital={}, length={}", hospitalCode, hl7.length());

        try {
            Map<String, String> updates = adtParser.parse(hl7);
            String patientId = updates.get("patientId");
            if (patientId == null) {
                log.warn("ADT message has no patientId — cannot update");
                return;
            }

            // 查找该患者所有非终态样本
            var samples = sampleService.list(
                    new LambdaQueryWrapper<Sample>()
                            .eq(Sample::getPatientId, patientId)
                            .notIn(Sample::getStatus, TERMINAL));

            for (Sample s : samples) {
                boolean changed = false;
                if (updates.containsKey("patientName")) { s.setPatientName(updates.get("patientName")); changed = true; }
                if (updates.containsKey("gender"))      { s.setGender(updates.get("gender")); changed = true; }
                if (updates.containsKey("wardCode"))    { s.setWardCode(updates.get("wardCode")); changed = true; }
                if (updates.containsKey("wardName"))    { s.setWardName(updates.get("wardName")); changed = true; }
                if (changed) {
                    sampleService.updateById(s);
                }
            }
            log.info("ADT applied: patientId={}, updated {} sample(s)", patientId, samples.size());

        } catch (Exception e) {
            log.error("Failed to process ADT from hospital={}: {}", hospitalCode, e.getMessage());
        }
    }

    @Override
    public Sample findByBarcode(String barcode) {
        return sampleService.getByBarcode(barcode);
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
cd g:/myla && mvn compile -pl myla-platform/myla-platform-lis -am -q 2>&1 | tail -5
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add myla-platform/myla-platform-lis/src/main/java/com/myla/lis/inbound/LisInboundServiceImpl.java
git commit -m "feat(lis): add LisInboundServiceImpl — real inbound order/ADT processing"
```

---

### Task 8: Create LisInboundServer

**Files:**
- Create: `myla-platform/myla-platform-lis/src/main/java/com/myla/lis/inbound/LisInboundServer.java`

- [ ] **Step 1: Create LisInboundServer**

```java
package com.myla.lis.inbound;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myla.lis.mapper.LisConfigMapper;
import com.myla.lis.entity.LisConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * LIS 入站 TCP MLLP 服务器。
 * <p>
 * 为每个启用 HL7 入站的医院启动一个独立的 TCP 监听端口。
 * 收到 MLLP 帧（VT...FS CR）后解析 HL7 消息，调用 LisInboundService，
 * 然后返回 HL7 ACK（MSA^AA 或 MSA^AR）。
 * </p>
 *
 * <h3>生命周期：</h3>
 * <ul>
 *   <li>ApplicationReadyEvent → 查询 lis_config，为每个 enabled HL7 医院启动监听线程</li>
 *   <li>destroy() → 关闭所有监听线程和 ServerSocket</li>
 * </ul>
 *
 * <h3>MLLP 帧格式：</h3>
 * <pre>VT(0x0B) [HL7 Message] FS(0x1C) CR(0x0D)</pre>
 */
@Slf4j
@RequiredArgsConstructor
public class LisInboundServer implements DisposableBean {

    private final LisConfigMapper configMapper;
    private final LisInboundService inboundService;

    private final Map<String, ServerSocket> servers = new ConcurrentHashMap<>();
    private final Map<String, Thread> threads = new ConcurrentHashMap<>();
    private static final ObjectMapper jsonMapper = new ObjectMapper();

    // MLLP 帧分隔符
    private static final byte VT = 0x0B;
    private static final byte FS = 0x1C;
    private static final byte CR = 0x0D;

    /**
     * 应用启动后自动开启所有入站监听。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void startAll() {
        List<LisConfig> configs = configMapper.selectEnabledInbound();
        log.info("Starting LIS inbound servers for {} hospital(s)", configs.size());

        for (LisConfig cfg : configs) {
            startForHospital(cfg);
        }
    }

    /**
     * 为指定医院启动 MLLP 监听。
     */
    private void startForHospital(LisConfig cfg) {
        try {
            int port = extractPort(cfg.getChannelConfig());
            ServerSocket ss = new ServerSocket(port);
            servers.put(cfg.getHospitalCode(), ss);

            AtomicBoolean running = new AtomicBoolean(true);
            Thread t = Thread.ofVirtual().name("lis-inbound-" + cfg.getHospitalCode()).start(() -> {
                log.info("LIS inbound listener started: hospital={}, port={}", cfg.getHospitalCode(), port);
                while (running.get()) {
                    try {
                        Socket client = ss.accept();
                        client.setSoTimeout(30_000); // read timeout 30s
                        Thread.ofVirtual().start(() -> handleConnection(client, cfg));
                    } catch (IOException e) {
                        if (running.get()) {
                            log.error("Accept error for hospital={}: {}", cfg.getHospitalCode(), e.getMessage());
                        }
                    }
                }
            });
            threads.put(cfg.getHospitalCode(), t);

        } catch (Exception e) {
            log.error("Failed to start LIS inbound for hospital={}: {}", cfg.getHospitalCode(), e.getMessage());
        }
    }

    /**
     * 处理单个 TCP 连接 — 读取 MLLP 帧，处理，回 ACK。
     */
    private void handleConnection(Socket client, LisConfig cfg) {
        try (client; InputStream in = client.getInputStream();
             OutputStream out = client.getOutputStream()) {

            // 读取直到流结束
            byte[] buf = in.readAllBytes();
            if (buf.length == 0) return;

            // MLLP 帧切分：找 VT 开头、FS+CR 结尾
            int start = -1;
            for (int i = 0; i < buf.length - 1; i++) {
                if (buf[i] == VT) start = i;
                if (start >= 0 && buf[i] == FS && buf[i + 1] == CR) {
                    // 提取 HL7 消息（VT 之后、FS 之前）
                    int msgStart = start + 1;
                    int msgEnd = i;
                    byte[] hl7Frame = new byte[msgEnd - msgStart + 1];
                    System.arraycopy(buf, msgStart, hl7Frame, 0, hl7Frame.length);
                    String hl7 = new String(hl7Frame, StandardCharsets.UTF_8).trim();

                    // 识别消息类型
                    String msgType = identifyMsgType(hl7);

                    try {
                        // 处理
                        if (msgType.contains("ORM") || msgType.contains("O01")) {
                            inboundService.receiveOrder(cfg.getHospitalCode(), hl7.getBytes(StandardCharsets.UTF_8), "HL7");
                        } else if (msgType.contains("ADT")) {
                            inboundService.receivePatientUpdate(cfg.getHospitalCode(), hl7.getBytes(StandardCharsets.UTF_8));
                        }

                        // 回 ACK
                        sendAck(out, hl7, "AA", "OK");
                    } catch (Exception e) {
                        log.error("Failed to process HL7 from {}: {}", cfg.getHospitalCode(), e.getMessage());
                        sendAck(out, hl7, "AR", e.getMessage());
                    }

                    start = -1; // 重置，继续下一帧
                }
            }
        } catch (IOException e) {
            log.debug("Connection closed for hospital={}: {}", cfg.getHospitalCode(), e.getMessage());
        }
    }

    /**
     * 从 MSH-9 识别消息类型。
     */
    private String identifyMsgType(String hl7) {
        try {
            String[] segs = hl7.split("\r|\n");
            if (segs.length > 0 && segs[0].startsWith("MSH")) {
                String[] fields = segs[0].split("\\|");
                if (fields.length > 9) return fields[9]; // MSH-9
            }
        } catch (Exception ignored) {}
        return "UNKNOWN";
    }

    /**
     * 构造并发送 HL7 ACK 消息。
     */
    private void sendAck(OutputStream out, String request, String ackCode, String text) throws IOException {
        // 从请求中提取 MSH 字段用于构造 ACK
        String msh3 = ""; // sending application
        String msh4 = ""; // sending facility
        String msh5 = ""; // receiving application
        String msh6 = ""; // receiving facility
        String msh10 = ""; // message control ID

        try {
            String[] segs = request.split("\r|\n");
            if (segs.length > 0 && segs[0].startsWith("MSH")) {
                String[] f = segs[0].split("\\|");
                if (f.length > 3) msh3 = f[3];
                if (f.length > 4) msh4 = f[4];
                if (f.length > 5) msh5 = f[5];
                if (f.length > 6) msh6 = f[6];
                if (f.length > 10) msh10 = f[10];
            }
        } catch (Exception ignored) {}

        String ack = String.format(
                "MSH|^~\\&|%s|%s|%s|%s|%s||ACK|%s|P|2.5\r" +
                "MSA|%s|%s|%s\r",
                msh5, msh6, msh3, msh4,
                java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")),
                java.util.UUID.randomUUID().toString().replace("-", ""),
                ackCode, msh10, text);

        byte[] ackBytes = ack.getBytes(StandardCharsets.UTF_8);
        // MLLP 封装
        byte[] mllpFrame = new byte[ackBytes.length + 3];
        mllpFrame[0] = VT;
        System.arraycopy(ackBytes, 0, mllpFrame, 1, ackBytes.length);
        mllpFrame[ackBytes.length + 1] = FS;
        mllpFrame[ackBytes.length + 2] = CR;
        out.write(mllpFrame);
        out.flush();
    }

    /**
     * 从 channel_config JSON 提取端口号。
     */
    private int extractPort(String channelConfig) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = jsonMapper.readValue(channelConfig, Map.class);
            Object portObj = map.get("port");
            if (portObj instanceof Number n) return n.intValue();
        } catch (Exception ignored) {}
        return 2575; // 默认 HL7 MLLP 端口
    }

    /**
     * 停止所有监听。
     */
    @Override
    public void destroy() {
        log.info("Shutting down LIS inbound servers...");
        servers.forEach((code, ss) -> {
            try { ss.close(); } catch (IOException ignored) {}
        });
        threads.forEach((code, t) -> t.interrupt());
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
cd g:/myla && mvn compile -pl myla-platform/myla-platform-lis -am -q 2>&1 | tail -5
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add myla-platform/myla-platform-lis/src/main/java/com/myla/lis/inbound/LisInboundServer.java
git commit -m "feat(lis): add LisInboundServer — per-hospital TCP MLLP listener"
```

---

### Task 9: Create LisOutboundSender interface

**Files:**
- Create: `myla-platform/myla-platform-lis/src/main/java/com/myla/lis/outbound/LisOutboundSender.java`

- [ ] **Step 1: Create LisOutboundSender interface + SendResult**

```java
package com.myla.lis.outbound;

import com.myla.lis.entity.LisConfig;
import com.myla.lis.entity.OutboundMessage;
import lombok.Data;

/**
 * LIS 出站发送策略接口。
 * <p>
 * 每种通道类型（HL7 MLLP / ASTM TCP / HTTP）提供对应的实现。
 * OutboundMessageConsumer 根据 lis_config.channel_type 选择对应的 Sender。
 * </p>
 */
public interface LisOutboundSender {

    /**
     * 获取通道类型标识，与 lis_config.channel_type 匹配。
     * @return 如 "HL7", "ASTM", "HTTP"
     */
    String getChannelType();

    /**
     * 发送消息到外部 LIS 系统。
     *
     * @param msg    出站消息（含消息内容和目标医院）
     * @param config 该医院的 LIS 配置（含通道参数、超时等）
     * @return 发送结果（success + 失败时的 error）
     */
    SendResult send(OutboundMessage msg, LisConfig config);

    /**
     * 测试与 LIS 系统的连接是否可用。
     *
     * @param config 该医院的 LIS 配置
     * @return true 如果连接可用
     */
    boolean testConnection(LisConfig config);

    @Data
    class SendResult {
        private final boolean success;
        private final String error;

        public static SendResult ok() { return new SendResult(true, null); }
        public static SendResult fail(String error) { return new SendResult(false, error); }
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
cd g:/myla && mvn compile -pl myla-platform/myla-platform-lis -am -q 2>&1 | tail -5
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add myla-platform/myla-platform-lis/src/main/java/com/myla/lis/outbound/LisOutboundSender.java
git commit -m "feat(lis): add LisOutboundSender interface for outbound strategy"
```

---

### Task 10: Create Hl7MllpSender

**Files:**
- Create: `myla-platform/myla-platform-lis/src/main/java/com/myla/lis/outbound/Hl7MllpSender.java`

- [ ] **Step 1: Create Hl7MllpSender**

```java
package com.myla.lis.outbound;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myla.lis.entity.LisConfig;
import com.myla.lis.entity.OutboundMessage;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * HL7 MLLP 发送器。
 * <p>
 * 通过 TCP MLLP 协议将 HL7 消息发送到 LIS 系统。
 * 短连接模式：每次发送建立一次 TCP 连接，等待 ACK 后关闭。
 * </p>
 *
 * <h3>发送流程：</h3>
 * <ol>
 *   <li>从 channel_config JSON 读取 IP 和端口</li>
 *   <li>建立 TCP 连接</li>
 *   <li>发送 MLLP 帧：VT + HL7 + FS + CR</li>
 *   <li>等待 ACK（超时 = ack_timeout_sec）</li>
 *   <li>验证 ACK 中的 MSA-1 是否为 AA</li>
 *   <li>关闭连接</li>
 * </ol>
 */
@Slf4j
public class Hl7MllpSender implements LisOutboundSender {

    private static final byte VT = 0x0B;
    private static final byte FS = 0x1C;
    private static final byte CR = 0x0D;
    private static final ObjectMapper jsonMapper = new ObjectMapper();

    @Override
    public String getChannelType() {
        return "HL7";
    }

    @Override
    public SendResult send(OutboundMessage msg, LisConfig config) {
        try {
            String channelCfgJson = config.getChannelConfig();
            if (channelCfgJson == null || channelCfgJson.isBlank()) {
                return SendResult.fail("channel_config is empty");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> cfg = jsonMapper.readValue(channelCfgJson, Map.class);
            String host = (String) cfg.getOrDefault("host", "localhost");
            int port = cfg.get("port") instanceof Number n ? n.intValue() : 2575;
            int timeout = config.getAckTimeoutSec() != null ? config.getAckTimeoutSec() * 1000 : 30_000;

            try (Socket sock = new Socket(host, port)) {
                sock.setSoTimeout(timeout);
                OutputStream out = sock.getOutputStream();
                InputStream in = sock.getInputStream();

                // MLLP 封装
                byte[] hl7 = msg.getMessageContent().getBytes(StandardCharsets.UTF_8);
                byte[] frame = new byte[hl7.length + 3];
                frame[0] = VT;
                System.arraycopy(hl7, 0, frame, 1, hl7.length);
                frame[hl7.length + 1] = FS;
                frame[hl7.length + 2] = CR;
                out.write(frame);
                out.flush();

                // 等待 ACK
                byte[] ackBuf = in.readAllBytes();
                String ack = new String(ackBuf, StandardCharsets.UTF_8).trim();
                if (ack.contains("MSA|AA")) {
                    log.info("HL7 MLLP sent OK: messageId={}, hospital={}",
                            msg.getMessageId(), msg.getHospitalCode());
                    return SendResult.ok();
                } else if (ack.contains("MSA|AR") || ack.contains("MSA|AE")) {
                    return SendResult.fail("LIS rejected: " + ack.substring(0, Math.min(200, ack.length())));
                } else {
                    return SendResult.fail("Unexpected ACK: " + ack.substring(0, Math.min(200, ack.length())));
                }
            }
        } catch (Exception e) {
            log.error("HL7 MLLP send failed: messageId={}, hospital={}, error={}",
                    msg.getMessageId(), msg.getHospitalCode(), e.getMessage());
            return SendResult.fail(e.getMessage());
        }
    }

    @Override
    public boolean testConnection(LisConfig config) {
        try {
            String channelCfgJson = config.getChannelConfig();
            if (channelCfgJson == null) return false;
            @SuppressWarnings("unchecked")
            Map<String, Object> cfg = jsonMapper.readValue(channelCfgJson, Map.class);
            String host = (String) cfg.getOrDefault("host", "localhost");
            int port = cfg.get("port") instanceof Number n ? n.intValue() : 2575;
            try (Socket s = new Socket(host, port)) {
                s.setSoTimeout(3000);
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
cd g:/myla && mvn compile -pl myla-platform/myla-platform-lis -am -q 2>&1 | tail -5
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add myla-platform/myla-platform-lis/src/main/java/com/myla/lis/outbound/Hl7MllpSender.java
git commit -m "feat(lis): add Hl7MllpSender for HL7 MLLP outbound sending"
```

---

### Task 11: Create AstmTcpSender

**Files:**
- Create: `myla-platform/myla-platform-lis/src/main/java/com/myla/lis/outbound/AstmTcpSender.java`

- [ ] **Step 1: Create AstmTcpSender**

```java
package com.myla.lis.outbound;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myla.lis.entity.LisConfig;
import com.myla.lis.entity.OutboundMessage;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * ASTM TCP 发送器。
 * <p>
 * 通过 TCP 连接以 ASTM E1394 帧格式（STX...ETX）发送结果到 LIS。
 * 发送后等待 ACK(0x06) 确认。
 * </p>
 */
@Slf4j
public class AstmTcpSender implements LisOutboundSender {

    private static final byte STX = 0x02;
    private static final byte ETX = 0x03;
    private static final byte ACK = 0x06;
    private static final byte NAK = 0x15;
    private static final ObjectMapper jsonMapper = new ObjectMapper();

    @Override
    public String getChannelType() {
        return "ASTM";
    }

    @Override
    public SendResult send(OutboundMessage msg, LisConfig config) {
        try {
            String channelCfgJson = config.getChannelConfig();
            if (channelCfgJson == null || channelCfgJson.isBlank()) {
                return SendResult.fail("channel_config is empty");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> cfg = jsonMapper.readValue(channelCfgJson, Map.class);
            String host = (String) cfg.getOrDefault("host", "localhost");
            int port = cfg.get("port") instanceof Number n ? n.intValue() : 2000;
            int timeout = config.getAckTimeoutSec() != null ? config.getAckTimeoutSec() * 1000 : 30_000;

            try (Socket sock = new Socket(host, port)) {
                sock.setSoTimeout(timeout);
                OutputStream out = sock.getOutputStream();
                InputStream in = sock.getInputStream();

                // ASTM 帧封装
                byte[] content = msg.getMessageContent().getBytes(StandardCharsets.UTF_8);
                byte[] frame = new byte[content.length + 2];
                frame[0] = STX;
                System.arraycopy(content, 0, frame, 1, content.length);
                frame[frame.length - 1] = ETX;
                out.write(frame);
                out.flush();

                // 等待 ACK
                int response = in.read();
                if (response == ACK) {
                    log.info("ASTM sent OK: messageId={}, hospital={}",
                            msg.getMessageId(), msg.getHospitalCode());
                    return SendResult.ok();
                } else if (response == NAK) {
                    return SendResult.fail("LIS returned NAK");
                } else {
                    return SendResult.fail("Unexpected response: 0x" + Integer.toHexString(response));
                }
            }
        } catch (Exception e) {
            log.error("ASTM send failed: messageId={}, hospital={}, error={}",
                    msg.getMessageId(), msg.getHospitalCode(), e.getMessage());
            return SendResult.fail(e.getMessage());
        }
    }

    @Override
    public boolean testConnection(LisConfig config) {
        try {
            String channelCfgJson = config.getChannelConfig();
            if (channelCfgJson == null) return false;
            @SuppressWarnings("unchecked")
            Map<String, Object> cfg = jsonMapper.readValue(channelCfgJson, Map.class);
            String host = (String) cfg.getOrDefault("host", "localhost");
            int port = cfg.get("port") instanceof Number n ? n.intValue() : 2000;
            try (Socket s = new Socket(host, port)) {
                s.setSoTimeout(3000);
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
cd g:/myla && mvn compile -pl myla-platform/myla-platform-lis -am -q 2>&1 | tail -5
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add myla-platform/myla-platform-lis/src/main/java/com/myla/lis/outbound/AstmTcpSender.java
git commit -m "feat(lis): add AstmTcpSender for ASTM TCP outbound sending"
```

---

### Task 12: Create HttpSender

**Files:**
- Create: `myla-platform/myla-platform-lis/src/main/java/com/myla/lis/outbound/HttpSender.java`

- [ ] **Step 1: Create HttpSender**

```java
package com.myla.lis.outbound;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myla.lis.entity.LisConfig;
import com.myla.lis.entity.OutboundMessage;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * HTTP 发送器。
 * <p>
 * 通过 HTTP POST 将消息以 JSON 格式发送到 LIS 系统。
 * 请求体：{"hospitalCode": "...", "messageType": "...", "messageContent": "..."}
 * 验证 HTTP 200 响应即认为发送成功。
 * </p>
 */
@Slf4j
public class HttpSender implements LisOutboundSender {

    private static final ObjectMapper jsonMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public String getChannelType() {
        return "HTTP";
    }

    @Override
    public SendResult send(OutboundMessage msg, LisConfig config) {
        try {
            String channelCfgJson = config.getChannelConfig();
            if (channelCfgJson == null || channelCfgJson.isBlank()) {
                return SendResult.fail("channel_config is empty");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> cfg = jsonMapper.readValue(channelCfgJson, Map.class);
            String url = (String) cfg.get("url");
            if (url == null || url.isBlank()) {
                return SendResult.fail("channel_config.url is missing");
            }
            int timeout = config.getAckTimeoutSec() != null ? config.getAckTimeoutSec() : 30;

            // 构造 JSON 请求体
            Map<String, String> body = Map.of(
                    "hospitalCode", msg.getHospitalCode(),
                    "messageType", msg.getMessageType(),
                    "messageContent", msg.getMessageContent());
            byte[] bodyBytes = jsonMapper.writeValueAsBytes(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(timeout))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(bodyBytes))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() == 200) {
                log.info("HTTP sent OK: messageId={}, hospital={}, status={}",
                        msg.getMessageId(), msg.getHospitalCode(), response.statusCode());
                return SendResult.ok();
            } else {
                return SendResult.fail("HTTP " + response.statusCode() + ": "
                        + response.body().substring(0, Math.min(200, response.body().length())));
            }
        } catch (Exception e) {
            log.error("HTTP send failed: messageId={}, hospital={}, error={}",
                    msg.getMessageId(), msg.getHospitalCode(), e.getMessage());
            return SendResult.fail(e.getMessage());
        }
    }

    @Override
    public boolean testConnection(LisConfig config) {
        try {
            String channelCfgJson = config.getChannelConfig();
            if (channelCfgJson == null) return false;
            @SuppressWarnings("unchecked")
            Map<String, Object> cfg = jsonMapper.readValue(channelCfgJson, Map.class);
            String url = (String) cfg.get("url");
            if (url == null) return false;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET().build();
            HttpResponse<Void> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.discarding());
            return response.statusCode() < 500;
        } catch (Exception e) {
            return false;
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
cd g:/myla && mvn compile -pl myla-platform/myla-platform-lis -am -q 2>&1 | tail -5
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add myla-platform/myla-platform-lis/src/main/java/com/myla/lis/outbound/HttpSender.java
git commit -m "feat(lis): add HttpSender for HTTP outbound sending"
```

---

### Task 13: Create Hl7MessageBuilder

**Files:**
- Create: `myla-platform/myla-platform-lis/src/main/java/com/myla/lis/outbound/Hl7MessageBuilder.java`

- [ ] **Step 1: Create Hl7MessageBuilder**

```java
package com.myla.lis.outbound;

import com.myla.lis.entity.LisConfig;
import com.myla.result.entity.AstResult;
import com.myla.result.entity.OrganismResult;
import com.myla.sample.entity.Sample;

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
        sb.append("MSH|^~\\&|MYLA|MYLA|LIS|").append(sample.getWardCode() != null ? sample.getWardCode() : "")
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
```

- [ ] **Step 2: Verify compilation**

```bash
cd g:/myla && mvn compile -pl myla-platform/myla-platform-lis -am -q 2>&1 | tail -5
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add myla-platform/myla-platform-lis/src/main/java/com/myla/lis/outbound/Hl7MessageBuilder.java
git commit -m "feat(lis): add Hl7MessageBuilder for HL7 ORU^R01 message construction"
```

---

### Task 14: Create ResultReleasedConsumer

**Files:**
- Create: `myla-platform/myla-platform-lis/src/main/java/com/myla/lis/consumer/ResultReleasedConsumer.java`
- Modify: `myla-server/src/main/java/com/myla/server/config/RabbitMqConfig.java`

- [ ] **Step 1: Add new queue and binding to RabbitMqConfig**

Add these beans to `RabbitMqConfig.java` after the existing `outboundDlqQueue()` method:

```java
/**
 * LIS 出站触发队列。
 * <p>TopicExchange myla.workflow 的第二订阅者，与 lab.event 队列同时收到消息。
 * 消费者（ResultReleasedConsumer）过滤 RESULT_RELEASED_TO_LIS 事件，
 * 构造 HL7 消息后调用 LisGatewayService.sendResult()。</p>
 */
@Bean
public Queue lisOutboundTriggerQueue() {
    return QueueBuilder.durable("lis.outbound.trigger").build();
}
```

Add this binding after the existing `labEventBinding()` method:

```java
/**
 * 绑定：lab.event 路由键 → lisOutboundTriggerQueue（LIS 出站触发）。
 */
@Bean
public Binding lisOutboundTriggerBinding() {
    return BindingBuilder.bind(lisOutboundTriggerQueue()).to(workflowExchange()).with("lab.event");
}
```

- [ ] **Step 2: Create ResultReleasedConsumer**

```java
package com.myla.lis.consumer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.myla.common.api.event.LabEvent;
import com.myla.lis.entity.LisConfig;
import com.myla.lis.mapper.LisConfigMapper;
import com.myla.lis.outbound.Hl7MessageBuilder;
import com.myla.lis.service.LisGatewayService;
import com.myla.result.entity.AstResult;
import com.myla.result.entity.OrganismResult;
import com.myla.result.mapper.AstResultMapper;
import com.myla.result.mapper.OrganismResultMapper;
import com.myla.sample.entity.Sample;
import com.myla.sample.mapper.SampleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 结果发布 LIS 消费者。
 * <p>
 * 监听 lis.outbound.trigger 队列，接收 RESULT_RELEASED_TO_LIS 事件，
 * 加载结果和样本数据，构造 HL7 ORU^R01 消息，调用 LisGatewayService 排队发送。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResultReleasedConsumer {

    private final OrganismResultMapper organismResultMapper;
    private final AstResultMapper astResultMapper;
    private final SampleMapper sampleMapper;
    private final LisConfigMapper configMapper;
    private final LisGatewayService lisGatewayService;
    private final Hl7MessageBuilder messageBuilder = new Hl7MessageBuilder();

    /**
     * 监听 lab.event 事件，仅处理 RESULT_RELEASED_TO_LIS。
     * <p>
     * 注意：LabEvent 通过 Jackson 反序列化时为字符串，不是枚举对象。
     * 因此使用 String 接收并比较。
     * </p>
     */
    @RabbitListener(queues = "lis.outbound.trigger")
    public void onLabEvent(String event) {
        if (!"RESULT_RELEASED_TO_LIS".equals(event)) {
            return; // 忽略其他事件
        }

        log.info("LIS outbound triggered by RESULT_RELEASED_TO_LIS");

        // 1. 查找所有已发布但未发送的结果（review_status = RELEASED）
        List<OrganismResult> released = organismResultMapper.selectList(
                new LambdaQueryWrapper<OrganismResult>()
                        .eq(OrganismResult::getReviewStatus, "RELEASED"));

        for (OrganismResult orgResult : released) {
            try {
                // 2. 加载样本
                Sample sample = sampleMapper.selectById(orgResult.getSampleId());
                if (sample == null) {
                    log.warn("Sample not found for organismResult.id={}", orgResult.getId());
                    continue;
                }

                // 3. 加载 AST 结果
                List<AstResult> asts = astResultMapper.selectList(
                        new LambdaQueryWrapper<AstResult>()
                                .eq(AstResult::getOrganismResultId, orgResult.getId()));

                // 4. 确定目标医院 — 从样本的 sourceSystem 或 wardCode 推测
                String hospitalCode = deriveHospitalCode(sample);

                // 5. 加载 LIS 配置
                LisConfig config = configMapper.selectByHospitalCode(hospitalCode);
                if (config == null || config.getEnabled() != 1) {
                    log.debug("No enabled LIS config for hospital={}", hospitalCode);
                    continue;
                }

                // 6. 构造 HL7 ORU^R01 消息
                String hl7 = messageBuilder.buildOruR01(orgResult, asts, sample, config);

                // 7. 排队发送
                lisGatewayService.sendResult(hospitalCode, hl7);

            } catch (Exception e) {
                log.error("Failed to build LIS message for organismResult.id={}: {}",
                        orgResult.getId(), e.getMessage());
            }
        }
    }

    /**
     * 从样本信息推导医院编码。
     * 优先使用 sourceSystem，其次使用 wardCode 前缀。
     */
    private String deriveHospitalCode(Sample sample) {
        if (sample.getSourceSystem() != null && !sample.getSourceSystem().isBlank()
                && !"LIS".equals(sample.getSourceSystem()) && !"MANUAL".equals(sample.getSourceSystem())) {
            return sample.getSourceSystem();
        }
        if (sample.getWardCode() != null && !sample.getWardCode().isBlank()) {
            return sample.getWardCode();
        }
        return "DEFAULT";
    }
}
```

- [ ] **Step 3: Verify compilation**

```bash
cd g:/myla && mvn compile -pl myla-platform/myla-platform-lis -am -q 2>&1 | tail -5
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add myla-platform/myla-platform-lis/src/main/java/com/myla/lis/consumer/ResultReleasedConsumer.java
git add myla-server/src/main/java/com/myla/server/config/RabbitMqConfig.java
git commit -m "feat(lis): add ResultReleasedConsumer + lis.outbound.trigger queue"
```

---

### Task 15: Update OutboundMessageConsumer

**Files:**
- Modify: `myla-platform/myla-platform-lis/src/main/java/com/myla/lis/consumer/OutboundMessageConsumer.java`

- [ ] **Step 1: Rewrite OutboundMessageConsumer to inject senders**

The entire replacement file content:

```java
package com.myla.lis.consumer;

import com.myla.lis.entity.LisConfig;
import com.myla.lis.entity.OutboundMessage;
import com.myla.lis.mapper.LisConfigMapper;
import com.myla.lis.mapper.OutboundMessageMapper;
import com.myla.lis.outbound.LisOutboundSender;
import com.myla.lis.outbound.LisOutboundSender.SendResult;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * LIS 出站消息消费者。
 * <p>
 * 监听 outbound.msg 队列，根据 lis_config.channel_type 选择对应的
 * LisOutboundSender（HL7 MLLP / ASTM TCP / HTTP）实际发送消息到外部 LIS。
 * </p>
 *
 * <h3>发送流程：</h3>
 * <ol>
 *   <li>从队列 "outbound.msg" 消费出站消息</li>
 *   <li>查 lis_config 获取该医院的通道配置</li>
 *   <li>选择匹配的 LisOutboundSender</li>
 *   <li>调用 sender.send() 真实发送</li>
 *   <li>成功 → SENT + ACK</li>
 *   <li>失败 → 重试 / DLQ</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboundMessageConsumer {

    private final OutboundMessageMapper messageMapper;
    private final LisConfigMapper configMapper;
    private final List<LisOutboundSender> senders;

    @RabbitListener(queues = "outbound.msg")
    public void onOutboundMessage(OutboundMessage msg, Message message, Channel channel,
                                   @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        try {
            log.info("Processing outbound message: messageId={}, hospital={}, type={}",
                    msg.getMessageId(), msg.getHospitalCode(), msg.getMessageType());

            // 1. 加载 LIS 配置
            LisConfig config = configMapper.selectByHospitalCode(msg.getHospitalCode());
            if (config == null) {
                log.error("No LIS config for hospital={}, sending to DLQ", msg.getHospitalCode());
                channel.basicNack(deliveryTag, false, false); // DLQ
                return;
            }

            // 2. 选择匹配的 sender
            LisOutboundSender sender = senders.stream()
                    .filter(s -> s.getChannelType().equalsIgnoreCase(config.getChannelType()))
                    .findFirst()
                    .orElse(null);

            if (sender == null) {
                log.warn("No sender for channel_type={}, marking as SENT (no-op)", config.getChannelType());
                msg.setSendStatus("SENT");
                msg.setSentAt(LocalDateTime.now());
                messageMapper.updateById(msg);
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 3. 真实发送
            SendResult result = sender.send(msg, config);

            if (result.isSuccess()) {
                msg.setSendStatus("SENT");
                msg.setSentAt(LocalDateTime.now());
                messageMapper.updateById(msg);
                channel.basicAck(deliveryTag, false);
                log.info("Outbound message sent: messageId={}", msg.getMessageId());
            } else {
                log.error("Send failed: messageId={}, error={}", msg.getMessageId(), result.getError());
                msg.setSendStatus("FAILED");
                msg.setLastError(result.getError());
                messageMapper.updateById(msg);
                handleFailure(msg, channel, deliveryTag);
            }
        } catch (Exception e) {
            log.error("Outbound processing error: messageId={}, error={}",
                    msg.getMessageId(), e.getMessage());
            msg.setSendStatus("FAILED");
            msg.setLastError(e.getMessage());
            messageMapper.updateById(msg);
            handleFailure(msg, channel, deliveryTag);
        }
    }

    private void handleFailure(OutboundMessage msg, Channel channel, long deliveryTag) {
        try {
            if (msg.getRetryCount() >= msg.getMaxRetries()) {
                channel.basicNack(deliveryTag, false, false); // → DLQ
            } else {
                msg.setRetryCount(msg.getRetryCount() + 1);
                msg.setNextRetryAt(LocalDateTime.now().plusMinutes(1));
                messageMapper.updateById(msg);
                channel.basicNack(deliveryTag, false, true); // requeue
            }
        } catch (IOException e) {
            log.error("Failed to nack message", e);
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
cd g:/myla && mvn compile -pl myla-platform/myla-platform-lis -am -q 2>&1 | tail -5
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add myla-platform/myla-platform-lis/src/main/java/com/myla/lis/consumer/OutboundMessageConsumer.java
git commit -m "feat(lis): update OutboundMessageConsumer with real sender dispatch"
```

---

### Task 16: Create LisAutoConfiguration

**Files:**
- Create: `myla-platform/myla-platform-lis/src/main/java/com/myla/lis/config/LisAutoConfiguration.java`

- [ ] **Step 1: Create LisAutoConfiguration**

```java
package com.myla.lis.config;

import com.myla.lis.inbound.LisInboundServer;
import com.myla.lis.inbound.LisInboundService;
import com.myla.lis.mapper.LisConfigMapper;
import com.myla.lis.outbound.AstmTcpSender;
import com.myla.lis.outbound.Hl7MllpSender;
import com.myla.lis.outbound.HttpSender;
import com.myla.lis.outbound.LisOutboundSender;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * LIS 模块自动配置。
 * <p>
 * 注册 LisInboundServer、各 LisOutboundSender 实现、组件扫描和 Mapper 扫描。
 * </p>
 */
@Configuration
@ComponentScan("com.myla.lis")
@MapperScan("com.myla.lis.mapper")
public class LisAutoConfiguration {

    /** LIS 入站 TCP MLLP 服务器 */
    @Bean
    public LisInboundServer lisInboundServer(LisConfigMapper configMapper,
                                              LisInboundService inboundService) {
        return new LisInboundServer(configMapper, inboundService);
    }

    /** HL7 MLLP 出站发送器 */
    @Bean
    public LisOutboundSender hl7MllpSender() {
        return new Hl7MllpSender();
    }

    /** ASTM TCP 出站发送器 */
    @Bean
    public LisOutboundSender astmTcpSender() {
        return new AstmTcpSender();
    }

    /** HTTP 出站发送器 */
    @Bean
    public LisOutboundSender httpSender() {
        return new HttpSender();
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
cd g:/myla && mvn compile -pl myla-platform/myla-platform-lis -am -q 2>&1 | tail -5
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add myla-platform/myla-platform-lis/src/main/java/com/myla/lis/config/LisAutoConfiguration.java
git commit -m "feat(lis): add LisAutoConfiguration with bean definitions"
```

---

### Task 17: Full build verification

- [ ] **Step 1: Full Maven build**

```bash
cd g:/myla && mvn compile -q 2>&1 | tail -10
```

Expected: BUILD SUCCESS

- [ ] **Step 2: Verify all new files exist**

```bash
cd g:/myla && ls -la \
  myla-platform/myla-platform-lis/src/main/java/com/myla/lis/mapper/LisConfigMapper.java \
  myla-platform/myla-platform-lis/src/main/java/com/myla/lis/inbound/FieldMapper.java \
  myla-platform/myla-platform-lis/src/main/java/com/myla/lis/inbound/Hl7OrderParser.java \
  myla-platform/myla-platform-lis/src/main/java/com/myla/lis/inbound/Hl7AdtParser.java \
  myla-platform/myla-platform-lis/src/main/java/com/myla/lis/inbound/LisInboundServiceImpl.java \
  myla-platform/myla-platform-lis/src/main/java/com/myla/lis/inbound/LisInboundServer.java \
  myla-platform/myla-platform-lis/src/main/java/com/myla/lis/outbound/LisOutboundSender.java \
  myla-platform/myla-platform-lis/src/main/java/com/myla/lis/outbound/Hl7MllpSender.java \
  myla-platform/myla-platform-lis/src/main/java/com/myla/lis/outbound/AstmTcpSender.java \
  myla-platform/myla-platform-lis/src/main/java/com/myla/lis/outbound/HttpSender.java \
  myla-platform/myla-platform-lis/src/main/java/com/myla/lis/outbound/Hl7MessageBuilder.java \
  myla-platform/myla-platform-lis/src/main/java/com/myla/lis/consumer/ResultReleasedConsumer.java \
  myla-platform/myla-platform-lis/src/main/java/com/myla/lis/config/LisAutoConfiguration.java
```

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "chore(lis): finalize LIS integration implementation"
```
