package com.myla.gateway.core.context;

import lombok.Data;
import java.util.Map;

/**
 * 仪器驱动配置数据模型。
 * <p>
 * 包含一条完整的仪器接入配置，涵盖驱动标识、仪器标识、通道参数、分桢器/解析器选型
 * 以及扩展属性。由外部配置文件（如 YAML/JSON）反序列化后传入驱动层。
 * </p>
 *
 * @author MyLA Team
 */
@Data
public class DriverConfig {

    /** 驱动唯一标识，如 "vitek2-v1.0" */
    private String driverId;

    /** 仪器唯一标识，如 "VITEK2-LAB1-001" */
    private String instrumentId;

    /** 通信通道配置，定义仪器连接方式及其参数 */
    private ChannelConfig channel;

    /** 分桢器类型，如 "ASTM"、"HL7-MLLP"；为空时由驱动自行决定 */
    private String splitterType;

    /** 解析器类型，如 "vitek2-parser"；为空时由驱动自行决定 */
    private String parserType;

    /** 扩展属性，存放驱动特有的配置项 */
    private Map<String, Object> properties;

    /**
     * 通信通道配置子模型。
     * <p>
     * 定义与仪器建立通信连接所需的全部参数。
     * 根据 {@code type} 字段的值（TCP/FILE/SERIAL），使用其中对应的参数子集。
     * </p>
     */
    @Data
    public static class ChannelConfig {

        /** 通道类型：TCP、FILE 或 SERIAL */
        private String type;

        /** TCP 模式下：仪器 IP 地址或主机名 */
        private String host;

        /** TCP 模式下：仪器监听端口 */
        private int port;

        /** FILE 模式下：监听的文件目录路径 */
        private String directory;

        /** FILE 模式下：文件名匹配的正则表达式，默认 ".*\\.txt" */
        private String filePattern;

        /** FILE 模式下：目录轮询间隔，单位毫秒，默认 5000 */
        private int pollIntervalMs = 5000;

        /** SERIAL 模式下：串口名称，如 "COM1" 或 "/dev/ttyS0" */
        private String serialPort;

        /** SERIAL 模式下：波特率，默认 9600 */
        private int baudRate = 9600;

        /** 连接失败后的重连延迟，单位毫秒，默认 1000 */
        private long reconnectDelayMs = 1000;
    }
}
