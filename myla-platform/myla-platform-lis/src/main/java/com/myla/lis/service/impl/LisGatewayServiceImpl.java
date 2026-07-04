package com.myla.lis.service.impl;

import com.myla.lis.entity.OutboundMessage;
import com.myla.lis.mapper.OutboundMessageMapper;
import com.myla.lis.service.LisGatewayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * MYLA 系统 LIS 网关服务实现类。
 * 实现与外部 LIS 系统通信的业务逻辑。
 *
 * 发送结果流程：
 * 1. 生成唯一消息ID（UUID 去除连字符）
 * 2. 构建出站消息实体，设置默认状态为 PENDING、重试次数为 0、最大重试次数为 3
 * 3. 持久化消息到数据库，后续由消息消费者异步发送
 *
 * 重试消息流程：
 * 1. 根据消息ID查询消息记录
 * 2. 校验重试次数是否未超过上限
 * 3. 重置状态为 PENDING，递增重试计数，设置下次重试时间为 1 分钟后
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LisGatewayServiceImpl implements LisGatewayService {

    private final OutboundMessageMapper messageMapper;

    /**
     * 发送检验结果到指定医院的 LIS 系统。
     * 构建出站消息并持久化到数据库，设置初始状态为 PENDING。
     * 实际发送由 RabbitMQ 消费者（OutboundMessageConsumer）异步执行。
     *
     * @param hospitalCode   目标医院编码
     * @param messageContent 消息内容（HL7/ASTM 格式）
     */
    @Override
    public void sendResult(String hospitalCode, String messageContent) {
        OutboundMessage msg = new OutboundMessage();
        msg.setMessageId(UUID.randomUUID().toString().replace("-", ""));
        msg.setHospitalCode(hospitalCode);
        msg.setMessageType("RESULT");
        msg.setMessageContent(messageContent);
        msg.setSendStatus("PENDING");
        msg.setRetryCount(0);
        msg.setMaxRetries(3);
        messageMapper.insert(msg);

        log.info("LIS outbound message queued: messageId={}, hospitalCode={}", msg.getMessageId(), hospitalCode);
    }

    /**
     * 手动重试发送失败的消息。
     * 仅当消息存在且重试次数未超过最大限制时才执行重试。
     * 重试时重置状态为 PENDING，递增重试计数，设置下次重试时间为 1 分钟后。
     *
     * @param messageId 消息数据库主键ID
     */
    @Override
    public void retryMessage(Long messageId) {
        OutboundMessage msg = messageMapper.selectById(messageId);
        if (msg != null && msg.getRetryCount() < msg.getMaxRetries()) {
            msg.setSendStatus("PENDING");
            msg.setRetryCount(msg.getRetryCount() + 1);
            msg.setNextRetryAt(LocalDateTime.now().plusMinutes(1));
            messageMapper.updateById(msg);
            log.info("LIS message retry scheduled: messageId={}, retryCount={}", msg.getMessageId(), msg.getRetryCount());
        }
    }
}
