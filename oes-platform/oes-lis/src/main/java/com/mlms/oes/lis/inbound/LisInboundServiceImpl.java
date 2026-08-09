package com.mlms.oes.lis.inbound;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mlms.oes.lis.entity.LisConfig;
import com.mlms.oes.lis.mapper.LisConfigMapper;
import com.mlms.oes.sample.entity.Sample;
import com.mlms.oes.sample.service.SampleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
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
