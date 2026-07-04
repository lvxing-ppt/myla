package com.myla.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MyLA 系统配置属性类。
 * <p>
 * 绑定 {@code application.yml} 中以 {@code myla} 为前缀的所有配置项。
 * 通过 Spring Boot 的 {@link ConfigurationProperties} 机制自动映射。
 * </p>
 *
 * <h3>配置示例（YAML）：</h3>
 * <pre>{@code
 * myla:
 *   gateway:
 *     driver-dir: ./drivers
 *     instruments:
 *       - driver-id: vitek2-v1.0
 *         instrument-id: VITEK2-LAB1-001
 *         channel:
 *           type: TCP
 *           port: 19001
 *         splitter-type: ASTM
 *         parser-type: vitek2-parser
 *   security:
 *     jwt-secret: your-secret-key
 *     jwt-expiration: 7200
 * }</pre>
 *
 * @author MyLA Team
 */
@Data
@Component
@ConfigurationProperties(prefix = "myla")
public class MylaProperties {

    /** 网关相关配置 */
    private Gateway gateway = new Gateway();

    /** 安全相关配置 */
    private Security security = new Security();

    /**
     * 网关配置子类。
     */
    @Data
    public static class Gateway {

        /** 驱动目录：存放仪器驱动插件 JAR 包的路径，默认 "./drivers" */
        private String driverDir = "./drivers";

        /** 仪器接入配置列表 */
        private List<InstrumentProperties> instruments = new ArrayList<>();
    }

    /**
     * 单台仪器接入配置。
     */
    @Data
    public static class InstrumentProperties {

        /** 驱动唯一标识，如 "vitek2-v1.0" */
        private String driverId;

        /** 仪器唯一标识，如 "VITEK2-LAB1-001" */
        private String instrumentId;

        /** 通信通道配置 */
        private ChannelProperties channel = new ChannelProperties();

        /** 分桢器类型，如 "ASTM"、"HL7-MLLP" */
        private String splitterType;

        /** 解析器类型，如 "vitek2-parser" */
        private String parserType;

        /** 扩展属性 */
        private Map<String, Object> properties = new HashMap<>();
    }

    /**
     * 通信通道配置。
     */
    @Data
    public static class ChannelProperties {

        /** 通道类型：TCP、FILE 或 SERIAL */
        private String type = "TCP";

        /** TCP 模式下：仪器 IP 地址或主机名 */
        private String host;

        /** TCP 模式下：监听端口 */
        private int port;

        /** FILE 模式下：监听的文件目录路径 */
        private String directory;

        /** FILE 模式下：文件名匹配的正则表达式 */
        private String filePattern;

        /** FILE 模式下：目录轮询间隔（毫秒） */
        private int pollIntervalMs = 5000;

        /** SERIAL 模式下：串口名称 */
        private String serialPort;

        /** SERIAL 模式下：波特率 */
        private int baudRate = 9600;

        /** 重连延迟（毫秒） */
        private long reconnectDelayMs = 1000;
    }

    /**
     * 安全配置子类。
     */
    @Data
    public static class Security {

        /** JWT 签名密钥，生产环境务必修改默认值 */
        private String jwtSecret = "change-me-in-production";

        /** JWT Token 过期时间，单位秒，默认 7200（2 小时） */
        private long jwtExpiration = 7200;
    }
}
