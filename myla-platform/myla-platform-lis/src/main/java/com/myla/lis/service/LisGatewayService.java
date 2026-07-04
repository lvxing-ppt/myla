package com.myla.lis.service;

import com.myla.lis.entity.OutboundMessage;

/**
 * MYLA 系统 LIS 网关服务接口。
 * 定义与外部 LIS 系统通信的核心业务操作。
 * 包括将检验结果发送到指定医院的 LIS 系统，
 * 以及对失败消息进行手动重试。
 */
public interface LisGatewayService {

    /**
     * 发送检验结果到指定医院的 LIS 系统。
     * 创建出站消息记录，将状态设置为 PENDING，
     * 后续由消息消费者通过配置的通道（HL7/ASTM/HTTP）异步发送。
     *
     * @param hospitalCode   目标医院编码
     * @param messageContent 消息内容（通常为 HL7/ASTM 格式）
     */
    void sendResult(String hospitalCode, String messageContent);

    /**
     * 手动重试发送失败的消息。
     * 检查消息的重试次数是否未超过最大限制，
     * 若可以重试则重新设置为 PENDING 状态并递增重试计数。
     *
     * @param messageId 消息数据库主键ID
     */
    void retryMessage(Long messageId);
}
