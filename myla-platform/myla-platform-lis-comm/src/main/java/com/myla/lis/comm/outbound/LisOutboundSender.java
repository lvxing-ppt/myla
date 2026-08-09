package com.myla.lis.comm.outbound;

import lombok.Data;
import java.util.Map;

/**
 * LIS 出站发送策略接口（通讯层侧 — 零业务依赖）。
 * <p>
 * 每种通道类型（HL7 MLLP / ASTM TCP / HTTP）提供对应的实现。
 * 消息格式为自包含的 Map，不依赖 LisConfig/OutboundMessage 实体。
 * </p>
 */
public interface LisOutboundSender {

    String getChannelType();

    SendResult send(Map<String, Object> channelConfig, String messageContent, String hospitalCode);

    @Data
    class SendResult {
        private final boolean success;
        private final String error;

        public static SendResult ok() { return new SendResult(true, null); }
        public static SendResult fail(String error) { return new SendResult(false, error); }
    }
}
