package com.myla.gateway.driver.proprietary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myla.common.api.dto.UnifiedResult;
import com.myla.common.api.enums.CommunicationMode;
import com.myla.gateway.core.context.DriverConfig;
import com.myla.gateway.core.context.DriverContext;
import com.myla.gateway.core.model.*;
import com.myla.gateway.core.spi.*;
import com.myla.gateway.channel.TcpChannel;
import com.myla.gateway.protocol.FrameType;
import com.myla.gateway.protocol.ProprietaryFrameCodec;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 私有协议仪器驱动实现。
 * <p>
 * 支持使用自定义二进制帧协议的仪器接入。通信模式为主动连接（ACTIVE_CONNECT），
 * 默认使用 TCP 通道。帧格式遵循 {@link ProprietaryFrameCodec} 定义的编解码规则，
 * 载荷为 JSON 格式。
 * </p>
 *
 * <h3>支持的帧类型：</h3>
 * <ul>
 *   <li>{@link FrameType#RESULT_PUSH} — 结果推送帧，载荷为 JSON 格式的 {@link UnifiedResult}</li>
 *   <li>{@link FrameType#TELEMETRY} — 遥测数据帧，载荷为 JSON 格式的 {@link TelemetryData}</li>
 *   <li>{@link FrameType#HEARTBEAT} — 心跳帧，仅打印 DEBUG 日志</li>
 *   <li>{@link FrameType#DISCOVERY} — 发现请求帧，打印 INFO 日志</li>
 *   <li>{@link FrameType#ERROR} — 错误帧，上报连接错误</li>
 * </ul>
 *
 * <h3>处理流程：</h3>
 * <ol>
 *   <li>通道收到原始字节 -> 保存原始报文</li>
 *   <li>调用 {@link ProprietaryFrameCodec#decode(byte[])} 解码帧</li>
 *   <li>根据帧类型分派到不同处理逻辑</li>
 *   <li>结果帧解析 JSON 后回调 {@link DataEventListener}</li>
 *   <li>遥测帧解析 JSON 后回调 {@link TelemetryListener}</li>
 * </ol>
 *
 * @author MyLA Team
 */
@Slf4j
public class ProprietaryProtocolDriver implements InstrumentDriver {

    /** 通信通道，默认使用 TCP */
    private final CommunicationChannel channel;

    /** JSON 解析器 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 驱动配置 */
    private DriverConfig config;

    /** 驱动上下文 */
    private DriverContext ctx;

    /** 数据事件监听器 */
    private DataEventListener dataListener;

    /** 遥测数据监听器 */
    private TelemetryListener telemetryListener;

    /**
     * 默认构造函数，使用 TCP 通道。
     */
    public ProprietaryProtocolDriver() {
        this(new TcpChannel());
    }

    /**
     * 指定通信通道的构造函数，用于测试或扩展。
     * @param channel 通信通道实例
     */
    public ProprietaryProtocolDriver(CommunicationChannel channel) {
        this.channel = channel;
    }

    /**
     * 获取驱动唯一标识。
     * @return 固定返回 "proprietary-v1.0"
     */
    @Override
    public String getDriverId() {
        return "proprietary-v1.0";
    }

    /**
     * 获取驱动显示名称。
     * @return 固定返回 "Proprietary Protocol Driver"
     */
    @Override
    public String getDisplayName() {
        return "Proprietary Protocol Driver";
    }

    /**
     * 获取驱动版本号。
     * @return 固定返回 "1.0"
     */
    @Override
    public String getVersion() {
        return "1.0";
    }

    /**
     * 获取通信模式。
     * @return 固定返回 ACTIVE_CONNECT（主动连接模式）
     */
    @Override
    public CommunicationMode getMode() {
        return CommunicationMode.ACTIVE_CONNECT;
    }

    /**
     * 初始化驱动配置。
     * @param config 驱动配置
     */
    @Override
    public void initialize(DriverConfig config) {
        this.config = config;
        log.info("ProprietaryProtocolDriver initialized for instrument {}", config.getInstrumentId());
    }

    /**
     * 启动驱动，建立 TCP 连接并开始接收数据。
     * <p>
     * 设置通道的消息监听器：
     * <ol>
     *   <li>保存原始报文</li>
     *   <li>解码二进制帧（使用 {@link ProprietaryFrameCodec}）</li>
     *   <li>根据帧类型分派处理：
     *     <ul>
     *       <li>RESULT_PUSH -> 解析 JSON 为 UnifiedResult，回调数据监听器</li>
     *       <li>TELEMETRY -> 解析 JSON 为 TelemetryData，回调遥测监听器</li>
     *       <li>HEARTBEAT -> 仅打印 DEBUG 日志</li>
     *       <li>DISCOVERY -> 打印 INFO 日志</li>
     *       <li>ERROR -> 回调 onConnectionError</li>
     *       <li>其他 -> 打印 DEBUG 日志</li>
     *     </ul>
     *   </li>
     * </ol>
     * 处理异常时回调 onParseFailed。
     * </p>
     *
     * @param ctx 驱动上下文
     */
    @Override
    public void start(DriverContext ctx) {
        this.ctx = ctx;

        // 设置消息监听器：解码帧并根据类型分派处理
        channel.setMessageListener(rawBytes -> {
            try {
                // 1. 保存原始报文
                ctx.saveRawMessage(config.getInstrumentId(), "PROPRIETARY", rawBytes);

                // 2. 解码二进制帧
                ProprietaryFrameCodec.DecodedFrame decoded = ProprietaryFrameCodec.decode(rawBytes);
                FrameType type = decoded.type();

                // 3. 根据帧类型分派处理逻辑
                switch (type) {
                    case RESULT_PUSH -> {
                        // 结果推送帧：解析 JSON 载荷为 UnifiedResult
                        UnifiedResult result = objectMapper.readValue(decoded.payload(), UnifiedResult.class);
                        result.setInstrumentId(config.getInstrumentId());
                        ctx.publishResult(rawBytes);
                        if (dataListener != null) {
                            dataListener.onResultReceived(result);
                        }
                    }
                    case TELEMETRY -> {
                        // 遥测帧：解析 JSON 载荷为 TelemetryData
                        TelemetryData telemetry = objectMapper.readValue(decoded.payload(), TelemetryData.class);
                        if (telemetryListener != null) {
                            telemetryListener.onTelemetry(config.getInstrumentId(), telemetry);
                        }
                    }
                    case HEARTBEAT -> {
                        // 心跳帧：仅记录调试日志
                        log.debug("Heartbeat received from instrument {}", config.getInstrumentId());
                    }
                    case DISCOVERY -> {
                        // 发现帧：仪器主动上报身份信息
                        log.info("Discovery request from instrument {}", config.getInstrumentId());
                    }
                    case ERROR -> {
                        // 错误帧：仪器主动上报错误
                        String errorMsg = decoded.payload() != null ? new String(decoded.payload()) : "Unknown error";
                        log.error("Error frame from instrument {}: {}", config.getInstrumentId(), errorMsg);
                        if (dataListener != null) {
                            dataListener.onConnectionError(config.getInstrumentId(), errorMsg, 1);
                        }
                    }
                    default -> log.debug("Unhandled frame type {} from instrument {}", type, config.getInstrumentId());
                }
            } catch (Exception e) {
                // 处理异常：帧解码失败或 JSON 解析失败
                log.error("Failed to process frame from instrument {}: {}", config.getInstrumentId(), e.getMessage());
                if (dataListener != null) {
                    dataListener.onParseFailed(new String(rawBytes), e.getMessage());
                }
            }
        });

        // 设置错误监听器：通道级错误（如 TCP 断连）
        channel.setErrorListener(error -> {
            log.error("Channel error for instrument {}: {}", config.getInstrumentId(), error.getMessage());
            ctx.reportHealth(config.getInstrumentId(), "ERROR", error.getMessage());
            if (dataListener != null) {
                dataListener.onConnectionError(config.getInstrumentId(), error.getMessage(), 1);
            }
        });

        // 打开通信通道
        channel.open(config.getChannel());
        ctx.reportHealth(config.getInstrumentId(), "ONLINE", "ProprietaryProtocolDriver started");
        log.info("ProprietaryProtocolDriver started for instrument {}", config.getInstrumentId());
    }

    /**
     * 停止驱动，关闭 TCP 连接。
     */
    @Override
    public void stop() {
        channel.close();
        if (ctx != null) {
            ctx.reportHealth(config.getInstrumentId(), "OFFLINE", "ProprietaryProtocolDriver stopped");
        }
        log.info("ProprietaryProtocolDriver stopped for instrument {}", config.getInstrumentId());
    }

    /**
     * 测试连接状态。
     * @return true 如果 TCP 通道处于打开状态
     */
    @Override
    public boolean testConnection() {
        return channel.isOpen();
    }

    /**
     * 注册数据事件监听器。
     * @param listener 数据事件监听器
     */
    @Override
    public void registerListener(DataEventListener listener) {
        this.dataListener = listener;
    }

    /**
     * 注册遥测数据监听器。
     * @param listener 遥测数据监听器
     */
    @Override
    public void registerTelemetryListener(TelemetryListener listener) {
        this.telemetryListener = listener;
    }

    /**
     * 获取仪器发现信息。
     * <p>私有协议驱动无法自动发现仪器信息，返回通用占位值。</p>
     *
     * @return 包含占位信息的发现信息对象
     */
    @Override
    public DiscoveryInfo getDiscoveryInfo() {
        DiscoveryInfo info = new DiscoveryInfo();
        info.setManufacturer("Generic");
        info.setModel("Proprietary Protocol");
        info.setSerialNumber("N/A");
        info.setFirmwareVersion("N/A");
        info.setHardwareRevision("N/A");
        info.setSupportedCommands(List.of());
        return info;
    }
}
