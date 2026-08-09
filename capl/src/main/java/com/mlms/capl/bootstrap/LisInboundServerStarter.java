package com.mlms.capl.bootstrap;

import com.mlms.capl.config.LisCommProperties.LisInboundProps;
import com.mlms.oes.common.core.constant.MqBinding;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.concurrent.DefaultThreadFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.file.Paths;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * LIS 入站 TCP MLLP 服务器启动器（Netty NIO 实现）。
 * <p>
 * 从 YAML/Nacos 配置读取医院→端口映射，为每个医院启动独立的 Netty ServerBootstrap。
 * 收到 HL7 消息后轻量解析 MSH-9/MSH-10，通过 Redis SETNX 去重，
 * 包装 JSON 发到 lis.inbound 队列，并自动回复 HL7 ACK（MSA|AA）。
 * </p>
 *
 * <h3>线程模型：</h3>
 * <pre>
 * bossGroup (1 线程) → accept 新连接 → 分配给 workerGroup
 * workerGroup (4 线程) → MLLP 解码 → HL7 处理 → Redis 去重 → 写 ACK
 * </pre>
 *
 * <h3>Pipeline：</h3>
 * <pre>
 * MllpFrameDecoder → LisInboundHandler(parse+redisDedup+ack+mq)
 * </pre>
 */
@Slf4j
public class LisInboundServerStarter implements DisposableBean {

    /** 全局共享的 boss 线程组 */
    private static final EventLoopGroup SHARED_BOSS_GROUP =
            new NioEventLoopGroup(1, new DefaultThreadFactory("lis-inbound-boss", true));

    /** 全局共享的 worker 线程组 */
    private static final EventLoopGroup SHARED_WORKER_GROUP =
            new NioEventLoopGroup(4, new DefaultThreadFactory("lis-inbound-worker", true));

    /** 引用计数，无实例时关闭线程组 */
    private static final AtomicInteger INSTANCE_COUNT = new AtomicInteger(0);

    /** Redis 去重 key 前缀 */
    private static final String DEDUP_PREFIX = "lis:inbound:dedup:";

    /** 去重窗口（分钟），与 LIS 重试窗口对齐 */
    private static final Duration DEDUP_TTL = Duration.ofMinutes(5);

    /** MQ 故障时本地兜底缓冲区根目录 */
    private static final String FAILOVER_BASE_DIR = "./failover-buffer";

    /** MQ 故障重放间隔（秒） */
    private static final int FAILOVER_REPLAY_INTERVAL_SEC = 10;

    private final RabbitTemplate rabbitTemplate;
    private final StringRedisTemplate redis;

    /** hospitalCode → 绑定的 Netty Channel */
    private final Map<String, Channel> serverChannels = new ConcurrentHashMap<>();

    /** hospitalCode → MQ 故障本地兜底缓冲区 */
    private final Map<String, FailoverBuffer> failoverBuffers = new ConcurrentHashMap<>();

    public LisInboundServerStarter(RabbitTemplate rabbitTemplate,
                                   StringRedisTemplate redis) {
        this.rabbitTemplate = rabbitTemplate;
        this.redis = redis;
        INSTANCE_COUNT.incrementAndGet();
    }

    // ==================== Public API ====================

    /**
     * 为每个配置的医院启动独立的 TCP 监听端口。
     */
    public void startAll(List<LisInboundProps> inbounds) {
        if (inbounds == null || inbounds.isEmpty()) {
            log.info("No LIS inbound ports configured");
            return;
        }
        log.info("Starting {} LIS inbound listener(s) with Netty NIO", inbounds.size());
        for (LisInboundProps cfg : inbounds) {
            startForHospital(cfg.getHospitalCode(), cfg.getPort());
        }
    }

    // ==================== Private ====================

    private void startForHospital(String hospitalCode, int port) {
        // 每个院区独立的 MQ 故障本地兜底缓冲区
        FailoverBuffer failover = new FailoverBuffer(
                Paths.get(FAILOVER_BASE_DIR), hospitalCode,
                rabbitTemplate, FAILOVER_REPLAY_INTERVAL_SEC);
        failoverBuffers.put(hospitalCode, failover);

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(SHARED_BOSS_GROUP, SHARED_WORKER_GROUP)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 128)
                .option(ChannelOption.SO_REUSEADDR, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline()
                                // 60s 无数据读 → 关闭空闲连接，释放半帧缓冲区
                                .addLast(new ReadTimeoutHandler(60, TimeUnit.SECONDS))
                                // MLLP 帧解码: VT ... FS CR
                                .addLast(new MllpFrameDecoder())
                                // 业务处理: 解析 HL7 → Redis 去重 → MQ (↓) → ACK
                                .addLast(new LisInboundHandler(
                                        hospitalCode, rabbitTemplate, redis,
                                        DEDUP_PREFIX, DEDUP_TTL, failover));
                    }
                });

        try {
            Channel ch = bootstrap.bind(port).sync().channel();
            serverChannels.put(hospitalCode, ch);
            log.info("LIS inbound listener started (Netty): hospital={}, port={}", hospitalCode, port);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to bind port " + port + " for hospital=" + hospitalCode, e);
        }
    }

    // ==================== Shutdown ====================

    @Override
    public void destroy() {
        log.info("Shutting down LIS inbound servers (Netty)...");
        serverChannels.forEach((code, ch) -> {
            ch.close().awaitUninterruptibly(3000);
            log.info("LIS inbound stopped: hospital={}", code);
        });
        serverChannels.clear();

        // 关闭 MQ 故障本地兜底缓冲区（自动 flush + 停止定时重放）
        failoverBuffers.forEach((code, fb) -> {
            fb.close();
            log.info("FailoverBuffer closed: hospital={}", code);
        });
        failoverBuffers.clear();

        int remaining = INSTANCE_COUNT.decrementAndGet();
        if (remaining <= 0) {
            SHARED_WORKER_GROUP.shutdownGracefully(2, 5, TimeUnit.SECONDS);
            SHARED_BOSS_GROUP.shutdownGracefully(2, 5, TimeUnit.SECONDS);
            log.info("LIS inbound shared thread pools shut down");
        }
    }

    // ==================== Netty Handlers ====================

    /**
     * MLLP 帧解码器 — 从 TCP 字节流中切出 HL7 消息帧。
     * <p>
     * MLLP 帧格式: &lt;VT&gt; HL7_message &lt;FS&gt;&lt;CR&gt;
     * 该解码器处理粘包/拆包，只将完整的 HL7 消息内容（不含 VT/FS/CR）传给下游。
     * </p>
     */
    static class MllpFrameDecoder extends ByteToMessageDecoder {

        /** HL7 单帧消息体（VT 到 FS 之间）最大 128KB */
        private static final int MAX_FRAME_LENGTH = 128 * 1024;
        /** 总缓冲区上限 512KB，防止无 VT 垃圾数据撑爆内存 */
        private static final int MAX_BUFFER_SIZE = 512 * 1024;

        private static final byte VT = 0x0B;
        private static final byte FS = 0x1C;
        private static final byte CR = 0x0D;

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
            while (in.readableBytes() > 0) {
                // 1. 找帧头 VT（0x0B）
                int vtIdx = indexOf(in, VT);
                if (vtIdx < 0) {
                    // 无 VT：若积压超过总上限则为无帧头垃圾流，关连接
                    if (in.readableBytes() > MAX_BUFFER_SIZE) {
                        log.error("No MLLP frame header (VT) found in {} bytes, closing connection: {}",
                                in.readableBytes(), ctx.channel().remoteAddress());
                        ctx.close();
                        in.skipBytes(in.readableBytes());
                    }
                    return;
                }

                // 丢弃 VT 之前的垃圾字节，记录回退锚点
                if (vtIdx > 0) {
                    in.skipBytes(vtIdx);
                }
                int vtPos = in.readerIndex();
                in.skipBytes(1); // 跳过 VT，进入消息体

                // 2. 在当前帧内搜索帧尾 FS+CR（0x1C 0x0D）
                boolean validTerminator = false;
                while (!validTerminator) {
                    int fsIdx = indexOf(in, FS);
                    if (fsIdx < 0) {
                        // FS 尚未到达 → 回退到 VT，等待后续 TCP 数据
                        in.readerIndex(vtPos);
                        return;
                    }

                    // 单帧消息体超长保护
                    if (fsIdx > MAX_FRAME_LENGTH) {
                        log.error("MLLP frame body exceeds max length {} bytes, closing connection: {}",
                                MAX_FRAME_LENGTH, ctx.channel().remoteAddress());
                        ctx.close();
                        in.skipBytes(in.readableBytes());
                        return;
                    }

                    // FS 是缓冲区最后一个字节，CR 还没到
                    if (in.readableBytes() <= fsIdx + 1) {
                        in.readerIndex(vtPos);
                        return;
                    }

                    if (in.getByte(in.readerIndex() + fsIdx + 1) != CR) {
                        // FS 后不是 CR → 帧尾损坏，整个帧不可恢复。
                        // 检查非 CR 字节是否为下帧 VT，是则只跳到 FS 保留 VT
                        int skipLen = in.getByte(in.readerIndex() + fsIdx + 1) == VT
                                ? fsIdx + 1   // 保留 VT 给外层循环
                                : fsIdx + 2;  // 跳过 body + FS + 垃圾字节
                        in.skipBytes(skipLen);
                        break;
                    }

                    // 有效帧尾 FS+CR：提取消息体，跳过 FS+CR
                    byte[] hl7 = new byte[fsIdx];
                    in.readBytes(hl7);
                    in.skipBytes(2);
                    out.add(hl7);
                    validTerminator = true;
                }
            }
        }

        /** 搜索单个字节在缓冲区中的相对偏移（相对于 readerIndex），-1 表示未找到 */
        private static int indexOf(ByteBuf in, byte target) {
            for (int i = 0; i < in.readableBytes(); i++) {
                if (in.getByte(in.readerIndex() + i) == target) {
                    return i;
                }
            }
            return -1;
        }
    }

    /**
     * 携带完整消息体的 CorrelationData，用于 publisher confirm NACK 时自动回退到 FailoverBuffer。
     * <p>
     * 普通 CorrelationData 只带 msgControlId，NACK 时无法恢复消息体。
     * 此类在 NACK 回调中可直接调用 {@link FailoverBuffer#buffer} 落地。
     * </p>
     */
    public static class LisCorrelationData extends CorrelationData {
        public final FailoverBuffer failoverBuffer;
        public final String hospitalCode;
        public final String hl7;
        public final String msgType;

        public LisCorrelationData(String msgControlId, FailoverBuffer failoverBuffer,
                                  String hospitalCode, String hl7, String msgType) {
            super(msgControlId);
            this.failoverBuffer = failoverBuffer;
            this.hospitalCode = hospitalCode;
            this.hl7 = hl7;
            this.msgType = msgType;
        }
    }

    /**
     * LIS 入站业务处理器 — 解析 HL7 + Redis 分布式幂等去重 + ACK + 投递 RabbitMQ。
     * <p>
     * 每个医院端口对应同一个 handler 实例，所有连接共享。
     * 去重基于 Redis SETNX，支持多实例部署。
     * </p>
     */
    static class LisInboundHandler extends SimpleChannelInboundHandler<byte[]> {

        private static final DateTimeFormatter DTM_FMT =
                DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

        private final String hospitalCode;
        private final RabbitTemplate rabbitTemplate;
        private final StringRedisTemplate redis;
        private final String dedupPrefix;
        private final Duration dedupTtl;
        private final FailoverBuffer failoverBuffer;

        LisInboundHandler(String hospitalCode, RabbitTemplate rabbitTemplate,
                          StringRedisTemplate redis, String dedupPrefix, Duration dedupTtl,
                          FailoverBuffer failoverBuffer) {
            this.hospitalCode = hospitalCode;
            this.rabbitTemplate = rabbitTemplate;
            this.redis = redis;
            this.dedupPrefix = dedupPrefix;
            this.dedupTtl = dedupTtl;
            this.failoverBuffer = failoverBuffer;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, byte[] hl7Bytes) {
            String hl7 = new String(hl7Bytes, StandardCharsets.UTF_8).trim();
            if (hl7.isEmpty()) return;

            String msgType = extract(hl7, 8);       // MSH-9
            String msgControlId = extract(hl7, 9);  // MSH-10
            String redisKey = null;

            try {
                // Redis 分布式幂等去重：SETNX + TTL
                if (!msgControlId.isEmpty()) {
                    redisKey = dedupPrefix + hospitalCode + ":" + msgControlId;
                    Boolean isNew = redis.opsForValue().setIfAbsent(redisKey, "1", dedupTtl);
                    if (!Boolean.TRUE.equals(isNew)) {
                        log.info("[LIS-IN] duplicate message, ACK only: hospital={}, msgId={}",
                                hospitalCode, msgControlId);
                        writeAck(ctx, hl7, "AA", "OK");
                        return;
                    }
                } else {
                    log.warn("[LIS-IN] empty MSH-10 (message control ID), dedup disabled: hospital={}",
                            hospitalCode);
                }

                // 投递到 RabbitMQ。convertAndSend 是异步的，不等待 broker 确认；
                // 通过 LisCommConfig 中配置的 publisher confirms 回调感知投递失败。
                // 同步阶段失败（连接断开等）→ catch 中渐进式重试 → 重试耗尽后 FailoverBuffer 兜底。
                // NACK 阶段失败 → LisCorrelationData 携带 failoverBuffer 引用，回调中自动落地。
                Map<String, String> msg = new HashMap<>();
                msg.put("hospitalCode", hospitalCode);
                msg.put("messageType", msgType);
                msg.put("messageControlId", msgControlId);
                msg.put("rawMessage", hl7);

                rabbitTemplate.convertAndSend(
                        MqBinding.LIS_INBOUND.getExchange(),
                        MqBinding.LIS_INBOUND.getRoutingKey(),
                        msg,
                        new LisCorrelationData(msgControlId, failoverBuffer,
                                hospitalCode, hl7, msgType));
                log.info("[LIS-IN] published to lis.inbound: hospital={}, type={}, msgId={}",
                        hospitalCode, msgType, msgControlId);

                writeAck(ctx, hl7, "AA", "OK");
            } catch (Exception e) {
                // MQ 发送失败 → 渐进式重试 3 次（1s/2s/3s 递增间隔），覆盖 MQ 短暂抖动场景
                log.warn("[LIS-IN] MQ send failed, will retry: hospital={}, msgId={}, error={}",
                        hospitalCode, msgControlId, e.getMessage());

                Map<String, String> msg = new HashMap<>();
                msg.put("hospitalCode", hospitalCode);
                msg.put("messageType", msgType);
                msg.put("messageControlId", msgControlId);
                msg.put("rawMessage", hl7);

                boolean sent = false;
                for (int attempt = 1; attempt <= 3; attempt++) {
                    long delay = attempt * 1000L;
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.warn("[LIS-IN] MQ retry interrupted: hospital={}, msgId={}",
                                hospitalCode, msgControlId);
                        break;
                    }
                    try {
                        rabbitTemplate.convertAndSend(
                                MqBinding.LIS_INBOUND.getExchange(),
                                MqBinding.LIS_INBOUND.getRoutingKey(),
                                msg,
                                new LisCorrelationData(msgControlId, failoverBuffer,
                                        hospitalCode, hl7, msgType));
                        sent = true;
                        log.info("[LIS-IN] MQ retry {}/3 succeeded: hospital={}, msgId={}",
                                attempt, hospitalCode, msgControlId);
                        break;
                    } catch (Exception retryEx) {
                        log.warn("[LIS-IN] MQ retry {}/3 failed: hospital={}, msgId={}, error={}",
                                attempt, hospitalCode, msgControlId, retryEx.getMessage());
                    }
                }

                if (sent) {
                    writeAck(ctx, hl7, "AA", "OK");
                } else {
                    // 3 次重试均失败 → 本地持久化兜底，保留去重键抵御 LIS 重传
                    log.warn("[LIS-IN] MQ unavailable after 3 retries, buffering locally: hospital={}, msgId={}",
                            hospitalCode, msgControlId);
                    boolean buffered = failoverBuffer.buffer(hospitalCode, hl7, msgControlId, msgType);
                    if (buffered) {
                        writeAck(ctx, hl7, "AA", "OK");
                    } else {
                        log.error("[LIS-IN] failover buffer write failed, returning AR for retry: hospital={}, msgId={}",
                                hospitalCode, msgControlId);
                        if (redisKey != null) {
                            try { redis.delete(redisKey); } catch (Exception ignored) {}
                        }
                        writeAck(ctx, hl7, "AR", "Buffer unavailable");
                    }
                }
            }
        }

        /** 从 HL7 MSH 段中提取第 n 个字段（0-indexed，MSH-1 为 field 0） */
        private static String extract(String hl7, int fieldIdx) {
            try {
                String[] segs = hl7.split("\r|\n");
                if (segs.length > 0 && segs[0].startsWith("MSH")) {
                    String[] fields = segs[0].split("\\|");
                    if (fields.length > fieldIdx) return fields[fieldIdx];
                }
            } catch (Exception e) {
                log.warn("[LIS-IN] failed to extract field {} from MSH: {}", fieldIdx, e.getMessage());
            }
            return fieldIdx == 9 ? "" : "UNKNOWN";
        }

        /** 构造 HL7 ACK (MLLP 帧) 并写回客户端 */
        private void writeAck(ChannelHandlerContext ctx, String request,
                              String ackCode, String text) {
            String msh3 = "", msh4 = "", msh5 = "", msh6 = "", msh10 = "";
            try {
                String[] segs = request.split("\r|\n");
                if (segs.length > 0 && segs[0].startsWith("MSH")) {
                    String[] f = segs[0].split("\\|");
                    if (f.length > 2) msh3 = f[2];
                    if (f.length > 3) msh4 = f[3];
                    if (f.length > 4) msh5 = f[4];
                    if (f.length > 5) msh6 = f[5];
                    if (f.length > 9) msh10 = f[9];
                }
            } catch (Exception e) {
                log.debug("[LIS-IN] failed to parse MSH for ACK: {}", e.getMessage());
            }

            String ack = String.format(
                    "MSH|^~\\&|%s|%s|%s|%s|%s||ACK|%s|P|2.5\r" +
                    "MSA|%s|%s|%s\r",
                    msh5, msh6, msh3, msh4,
                    LocalDateTime.now().format(DTM_FMT),
                    UUID.randomUUID().toString().replace("-", ""),
                    ackCode, msh10, text);

            byte[] ackBytes = ack.getBytes(StandardCharsets.UTF_8);
            byte[] mllpFrame = new byte[ackBytes.length + 3];
            mllpFrame[0] = 0x0B;  // VT
            System.arraycopy(ackBytes, 0, mllpFrame, 1, ackBytes.length);
            mllpFrame[ackBytes.length + 1] = 0x1C;  // FS
            mllpFrame[ackBytes.length + 2] = 0x0D;  // CR;

            ctx.writeAndFlush(ctx.alloc().buffer(mllpFrame.length).writeBytes(mllpFrame))
                    .addListener((ChannelFutureListener) future -> {
                        if (!future.isSuccess()) {
                            log.warn("[LIS-IN] ACK write failed: hospital={}, remote={}, error={}",
                                    hospitalCode, ctx.channel().remoteAddress(),
                                    future.cause() != null ? future.cause().getMessage() : "unknown");
                        }
                    });
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            log.debug("LIS connected: hospital={}, remote={}", hospitalCode, ctx.channel().remoteAddress());
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            log.debug("LIS disconnected: hospital={}, remote={}", hospitalCode, ctx.channel().remoteAddress());
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.error("LIS inbound channel error: hospital={}, remote={}, error={}",
                    hospitalCode, ctx.channel().remoteAddress(), cause.getMessage());
            ctx.close();
        }
    }
}
