package com.mlms.capl.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * LIS 通讯层配置属性（映射 Nacos 或 application.yml 中的 lis-comm 段）。
 * <p>
 * {@link RefreshScope} 确保 Nacos 配置变更时此 Bean 被重新创建，从而拿到最新的仪器列表。
 * </p>
 */
@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "lis-comm")
public class LisCommProperties {

    /** 驱动插件目录，默认 "./drivers" */
    private String driverDir = "./drivers";

    /** 仪器列表 */
    private List<InstrumentProps> instruments = new ArrayList<>();

    /** LIS 入站监听端口配置 */
    private List<LisInboundProps> lisInbound = new ArrayList<>();

    /** LIS 出站通道配置 */
    private List<LisOutboundProps> lisOutbound = new ArrayList<>();

    @Data
    public static class InstrumentProps {
        private String instrumentId;
        private String driverId;
        private ChannelProps channel;
        private String splitterType;
        private String parserType;
        private Map<String, Object> properties;
    }

    @Data
    public static class ChannelProps {
        private String type = "TCP";
        private String host;
        private int port;
        private String directory;
        private String filePattern;
        private int pollIntervalMs = 5000;
        private String serialPort;
        private int baudRate = 9600;
        private int reconnectDelayMs = 5000;
    }

    @Data
    public static class LisInboundProps {
        /** 医院编码 */
        private String hospitalCode;
        /** 本机监听端口 */
        private int port = 2575;
    }

    @Data
    public static class LisOutboundProps {
        /** 医院编码 */
        private String hospitalCode;
        /** 通道类型: HL7 / ASTM / HTTP */
        private String channelType = "HL7";
        /** 通道配置 JSON */
        private Map<String, Object> channelConfig;
        /** ACK 超时(秒) */
        private int ackTimeoutSec = 30;
    }
}
