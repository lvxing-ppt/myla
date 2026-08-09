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
