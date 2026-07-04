package com.myla.lis.inbound;

import com.myla.sample.entity.Sample;

/**
 * LIS 入站服务接口 — 二期实现。
 * <p>
 * 定义从医院 LIS/HIS 系统接收医嘱和患者信息的标准契约。
 * 二期根据每家医院的通信方式（HL7 MLLP / ASTM / HTTP / 文件）实现对应的 Channel，
 * 解析后调用本接口的方法创建样本。
 * </p>
 *
 * <h3>二期接入方式：</h3>
 * <ol>
 *   <li>实现本接口的具体类（如 {@code LisInboundServiceImpl}）</li>
 *   <li>在 {@code lis_config} 表配置该院区的通信参数和字段映射</li>
 *   <li>启动对应的 Channel 监听 LIS 消息</li>
 *   <li>收到消息 → 根据 {@code lis_config.order_mapping} 做字段映射 → 调用本接口</li>
 * </ol>
 *
 * <h3>一期状态：</h3>
 * <p>接口已定义，提供默认空实现 {@link LisInboundService.NoOp}。
 * 样本可通过 REST API 手动登记，仪器结果按 barcode 自动关联已登记的样本。</p>
 *
 * @author MyLA Team
 * @since 1.0 (一期预留接口，二期实现)
 */
public interface LisInboundService {

    /**
     * 从 LIS 接收检验医嘱，创建样本记录。
     * <p>对应 HL7 ORM^O01 或 ASTM 医嘱消息。</p>
     *
     * @param rawMessage 原始 HL7/ASTM 消息字节
     * @param messageType 消息类型: HL7 / ASTM
     * @return 创建的样本实体（含自动生成的 sampleId）
     */
    Sample receiveOrder(byte[] rawMessage, String messageType);

    /**
     * 从 LIS 接收患者信息更新。
     * <p>对应 HL7 ADT^A04/A08 等消息类型。</p>
     *
     * @param rawMessage 原始 HL7 消息字节
     */
    void receivePatientUpdate(byte[] rawMessage);

    /**
     * 根据条码查找已登记的样本。
     * <p>一期已实现：通过 SampleMapper 查 sample 表。</p>
     *
     * @param barcode 样本条码
     * @return 样本实体，不存在返回 null
     */
    Sample findByBarcode(String barcode);

    // ==================== 默认空实现 (一期占位) ====================

    /**
     * 一期的空实现。二期替换为对接了 {@code lis_config} 字段映射的真实实现。
     */
    @org.springframework.stereotype.Component("lisInboundService")
    class NoOp implements LisInboundService {

        @Override
        public Sample receiveOrder(byte[] rawMessage, String messageType) {
            // 二期实现: HL7/ASTM 解析 + lis_config.order_mapping 字段映射 + SampleService.register()
            throw new UnsupportedOperationException(
                "LIS inbound not implemented yet. Phase 2 will parse HL7 ORM^O01 / ASTM orders.");
        }

        @Override
        public void receivePatientUpdate(byte[] rawMessage) {
            // 二期实现: HL7 ADT 解析 + 更新 sample 表患者信息
            throw new UnsupportedOperationException(
                "LIS inbound not implemented yet. Phase 2 will parse HL7 ADT messages.");
        }

        @Override
        public Sample findByBarcode(String barcode) {
            // 一期通过 SampleMapper 直接查询，不走这里。
            return null;
        }
    }
}
