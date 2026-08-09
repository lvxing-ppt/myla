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
