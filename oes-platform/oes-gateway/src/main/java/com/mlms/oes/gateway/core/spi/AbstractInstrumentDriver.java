package com.mlms.oes.gateway.core.spi;

import com.mlms.oes.gateway.core.context.DriverConfig;
import com.mlms.oes.gateway.core.context.DriverContext;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 仪器驱动抽象基类 — 提供可靠性管道模板。
 * <p>
 * 所有 TCP 类仪器驱动的公共逻辑统一在此：
 * <ol>
 *   <li>幂等去重（SHA-256 + Redis SETNX）</li>
 *   <li>ACK/NAK 应答</li>
 *   <li>心跳刷新</li>
 *   <li>原始报文存档（分桢前）</li>
 *   <li>分桢 → 解析 → 发布 → 回调 Listener</li>
 *   <li>通道错误监听 + 健康上报</li>
 * </ol>
 * </p>
 *
 * <h3>子类只需实现：</h3>
 * <pre>
 * public class MyDriver extends AbstractInstrumentDriver {
 *     public MyDriver() {
 *         super(new TcpChannel(), new AstmSplitter(), new MyParser());
 *     }
 *     // + 元信息方法 (getDriverId, getDisplayName, getVersion, getMode, getMessageType, getDiscoveryInfo)
 * }
 * </pre>
 *
 * @author MLMS Team
 */
@Slf4j
public abstract class AbstractInstrumentDriver implements InstrumentDriver {

    /** ASTM ACK (0x06) — 确认 */
    protected static final byte ACK = 0x06;
    /** ASTM NAK (0x15) — 否定确认，通知仪器重发 */
    protected static final byte NAK = 0x15;

    protected final CommunicationChannel channel;
    protected final FrameSplitter splitter;
    protected final DataParser parser;

    protected DriverConfig config;
    protected DriverContext ctx;
    protected DataEventListener listener;

    protected AbstractInstrumentDriver(CommunicationChannel channel,
                                        FrameSplitter splitter,
                                        DataParser parser) {
        this.channel = channel;
        this.splitter = splitter;
        this.parser = parser;
    }

    // ==================== 子类实现 ====================

    /** 报文类型标签，如 "ASTM"、"HL7" */
    protected abstract String getMessageType();

    // ==================== 模板方法 ====================

    @Override
    public void initialize(DriverConfig config) {
        this.config = config;
        log.info("{} initialized for instrument {}", getDisplayName(), config.getInstrumentId());
    }

    /**
     * 启动驱动 —— 可靠性管道模板。
     * <p>子类通常不需要重写。如需自定义，可重写 {@link #processFrame} 或 {@link #handleError}。</p>
     */
    @Override
    public void start(DriverContext ctx) {
        this.ctx = ctx;
        List<byte[]> incompleteFrames = new ArrayList<>();

        // ====== 消息处理管道 ======
        channel.setMessageListener(rawBytes -> {
            boolean success = true;  // 初始 true，失败改 false
            try {
                // ① 幂等去重
                if (ctx.isDuplicate(rawBytes)) {
                    log.debug("[{}] duplicate, ACK skip", config.getInstrumentId());
                    sendAck(ACK);
                    return;
                }

                // ② 刷新心跳
                ctx.reportHealth(config.getInstrumentId(), "ONLINE", "data received");

                // ③ 原始报文存档（分桢前，防丢失）
                ctx.saveRawMessage(config.getInstrumentId(), getMessageType(), rawBytes);

                // ④ 分桢
                List<byte[]> frames = splitter.splitFrames(rawBytes, incompleteFrames);

                // ⑤ 逐帧处理
                for (byte[] frame : frames) {
                    try {
                        List<?> results = parser.parse(frame);
                        for (Object obj : results) {
                            if (obj instanceof com.mlms.oes.common.api.dto.UnifiedResult r) {
                                r.setInstrumentId(config.getInstrumentId());
                                if (listener != null) {
                                    listener.onResultReceived(r); // → ResultPersistenceService(@Transactional DB + afterCommit MQ)
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.error("[{}] parse failed: {}", config.getInstrumentId(), e.getMessage());
                        if (listener != null) {
                            listener.onParseFailed(new String(frame), e.getMessage());
                        }
                        success = false;  // 有任何一个帧失败 → NAK
                    }
                }
            } catch (Exception e) {
                success = false;
                handleError(e);
            } finally {
                // ⑥ 标记已处理 + ACK/NAK
                if (success) {
                    ctx.markProcessed(rawBytes);
                    sendAck(ACK);
                } else {
                    sendAck(NAK);  // NAK → 仪器会重发
                }
            }
        });

        // ====== 通道错误监听 ======
        channel.setErrorListener(error -> {
            log.error("[{}] channel error: {}", config.getInstrumentId(), error.getMessage());
            if (listener != null) {
                listener.onConnectionError(config.getInstrumentId(), error.getMessage(), 1);
            }
            ctx.reportHealth(config.getInstrumentId(), "ERROR", error.getMessage());
        });

        // ====== 打开通道 ======
        channel.open(config.getChannel());
        ctx.reportHealth(config.getInstrumentId(), "ONLINE", getDisplayName() + " started");
        log.info("{} started for instrument {}", getDisplayName(), config.getInstrumentId());
    }

    @Override
    public void stop() {
        channel.close();
        if (ctx != null) {
            ctx.reportHealth(config.getInstrumentId(), "OFFLINE", getDisplayName() + " stopped");
        }
        log.info("{} stopped for instrument {}", getDisplayName(),
            config != null ? config.getInstrumentId() : "unknown");
    }

    @Override
    public boolean testConnection() {
        return channel.isOpen();
    }

    @Override
    public void registerListener(DataEventListener listener) {
        this.listener = listener;
    }

    // ==================== 可重写钩子 ====================

    /** 子类可重写以自定义错误处理 */
    protected void handleError(Exception e) {
        log.error("[{}] processing error: {}", config.getInstrumentId(), e.getMessage());
        if (listener != null) {
            listener.onConnectionError(config.getInstrumentId(), e.getMessage(), 1);
        }
    }

    /** 向仪器发送 ACK/NAK，失败静默 */
    protected void sendAck(byte ack) {
        try { channel.send(new byte[]{ack}); }
        catch (Exception ignored) {}
    }

    // ==================== 便捷方法 ====================

    /** 获取当前仪器 ID */
    protected String getInstrumentId() {
        return config != null ? config.getInstrumentId() : "unknown";
    }
}
