package com.myla.gateway.driver.vitek2;

import com.myla.common.api.enums.CommunicationMode;
import com.myla.gateway.channel.TcpChannel;
import com.myla.gateway.core.context.DriverConfig;
import com.myla.gateway.core.context.DriverContext;
import com.myla.gateway.core.model.*;
import com.myla.gateway.core.spi.*;
import com.myla.gateway.splitter.AstmSplitter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * VITEK 2 仪器驱动实现。
 * <p>
 * 适配生物梅里埃（bioMerieux）VITEK 2 微生物鉴定与药敏分析系统。
 * 通信模式为被动监听（PASSIVE_LISTEN），使用 TCP 通道接收仪器推送的 ASTM 报文。
 * </p>
 *
 * <h3>数据处理管道：</h3>
 * <pre>
 * TCP 原始字节 -> [AstmSplitter] -> ASTM 完整帧 -> [Vitek2Parser] -> UnifiedResult
 * </pre>
 *
 * <h3>分桢说明：</h3>
 * <p>
 * TCP 流式数据通过 {@link AstmSplitter} 切分为 ASTM 完整帧。
 * 使用 {@code incompleteFrames} 列表维护跨 TCP 读取的拼帧状态：
 * 当前次读取的尾部不完整帧会被保存，拼接到下次读取的数据头部继续处理。
 * </p>
 *
 * @author MyLA Team
 */
@Slf4j
public class Vitek2Driver implements InstrumentDriver {

    /** TCP 通信通道 */
    private final TcpChannel channel = new TcpChannel();

    /** ASTM 分桢器 */
    private final AstmSplitter splitter = new AstmSplitter();

    /** VITEK 2 专用解析器 */
    private final Vitek2Parser parser = new Vitek2Parser();

    /** 驱动配置 */
    private DriverConfig config;

    /** 驱动上下文 */
    private DriverContext ctx;

    /** 数据事件监听器 */
    private DataEventListener listener;

    /**
     * 获取驱动唯一标识。
     * @return 固定返回 "vitek2-v1.0"
     */
    @Override
    public String getDriverId() {
        return "vitek2-v1.0";
    }

    /**
     * 获取驱动显示名称。
     * @return 固定返回 "VITEK 2 Driver"
     */
    @Override
    public String getDisplayName() {
        return "VITEK 2 Driver";
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
     * <p>VITEK 2 作为 TCP 客户端主动推送数据，网关被动监听。</p>
     *
     * @return 固定返回 PASSIVE_LISTEN
     */
    @Override
    public CommunicationMode getMode() {
        return CommunicationMode.PASSIVE_LISTEN;
    }

    /**
     * 初始化驱动配置。
     * @param config 驱动配置
     */
    @Override
    public void initialize(DriverConfig config) {
        this.config = config;
        log.info("Vitek2Driver initialized for instrument {}", config.getInstrumentId());
    }

    /**
     * 启动驱动，开始监听 TCP 端口并处理 ASTM 报文。
     * <p>
     * 数据处理流程：
     * <ol>
     *   <li>保存原始报文到持久化存储</li>
     *   <li>调用 {@link AstmSplitter} 将原始字节切分为完整 ASTM 帧</li>
     *   <li>逐帧调用 {@link Vitek2Parser} 解析为 UnifiedResult</li>
     *   <li>将每条结果设置 instrumentId 后发布</li>
     *   <li>回调 {@link DataEventListener#onResultReceived}</li>
     *   <li>解析失败时回调 {@link DataEventListener#onParseFailed}</li>
     * </ol>
     * </p>
     *
     * @param ctx 驱动上下文
     */
    @Override
    public void start(DriverContext ctx) {
        this.ctx = ctx;

        // 维护跨 TCP 读取的未完成帧片段（用于拼帧）
        List<byte[]> incompleteFrames = new ArrayList<>();

        channel.setMessageListener(rawBytes -> {
            try {
                // 1. 保存原始报文
                ctx.saveRawMessage(config.getInstrumentId(), "ASTM", rawBytes);

                // 2. 分桢：将原始字节 + 上次残留拼接后切分为完整帧
                List<byte[]> frames = splitter.splitFrames(rawBytes, incompleteFrames);

                // 3. 逐帧解析
                for (byte[] frame : frames) {
                    try {
                        var results = parser.parse(frame);
                        for (var result : results) {
                            result.setInstrumentId(config.getInstrumentId());
                            ctx.publishResult(rawBytes);
                            if (listener != null) {
                                listener.onResultReceived(result);
                            }
                        }
                    } catch (Exception e) {
                        // 单帧解析失败不影响其他帧的处理
                        log.error("Parse failed for instrument {}: {}", config.getInstrumentId(), e.getMessage());
                        if (listener != null) {
                            listener.onParseFailed(new String(frame), e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                // 分桢过程异常
                log.error("Message processing error for instrument {}: {}", config.getInstrumentId(), e.getMessage());
                if (listener != null) {
                    listener.onConnectionError(config.getInstrumentId(), e.getMessage(), 1);
                }
            }
        });

        // 通道级错误监听
        channel.setErrorListener(error -> {
            log.error("Channel error for instrument {}: {}", config.getInstrumentId(), error.getMessage());
            if (listener != null) {
                listener.onConnectionError(config.getInstrumentId(), error.getMessage(), 1);
            }
            ctx.reportHealth(config.getInstrumentId(), "ERROR", error.getMessage());
        });

        // 打开 TCP 监听端口
        channel.open(config.getChannel());
        ctx.reportHealth(config.getInstrumentId(), "ONLINE", "Vitek2Driver started");
        log.info("Vitek2Driver started for instrument {}", config.getInstrumentId());
    }

    /**
     * 停止驱动，关闭 TCP 端口监听。
     */
    @Override
    public void stop() {
        channel.close();
        if (ctx != null) {
            ctx.reportHealth(config.getInstrumentId(), "OFFLINE", "Vitek2Driver stopped");
        }
        log.info("Vitek2Driver stopped for instrument {}", config.getInstrumentId());
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
        this.listener = listener;
    }

    /**
     * 获取仪器发现信息。
     * <p>返回 bioMerieux VITEK 2 的静态信息。</p>
     *
     * @return VITEK 2 发现信息对象
     */
    @Override
    public DiscoveryInfo getDiscoveryInfo() {
        DiscoveryInfo info = new DiscoveryInfo();
        info.setManufacturer("bioMerieux");
        info.setModel("VITEK 2");
        info.setSerialNumber("N/A");
        info.setFirmwareVersion("N/A");
        info.setHardwareRevision("N/A");
        info.setSupportedCommands(List.of());
        return info;
    }
}
