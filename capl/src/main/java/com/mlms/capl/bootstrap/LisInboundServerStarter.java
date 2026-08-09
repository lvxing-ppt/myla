package com.mlms.capl.bootstrap;

import com.mlms.capl.config.LisCommProperties.LisInboundProps;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.DefaultThreadFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.DisposableBean;

import java.nio.charset.StandardCharsets;
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
 * 从 YAML 配置读取医院→端口映射，为每个医院启动独立的 Netty ServerBootstrap。
 * 收到 HL7 消息后轻量解析 MSH-9/MSH-10，包装 JSON 发到 lis.inbound 队列，
 * 并自动回复 HL7 ACK（MSA|AA）。
 * </p>
 *
 * <h3>线程模型：</h3>
 * <pre>
 * bossGroup (1 线程) → accept 新连接 → 分配给 workerGroup
 * workerGroup (4 线程) → MLLP 解码 → HL7 处理 → 写 ACK
 * </pre>
 *
 * <h3>Pipeline：</h3>
 * <pre>
 * IdleStateHandler(60s) → MllpFrameDecoder → LisInboundHandler(parse+ack+mq)
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

    private static final int READER_IDLE_SECONDS = 60;

    private final RabbitTemplate rabbitTemplate;

    /** hospitalCode → 绑定的 Netty Channel */
    private final Map<String, Channel> serverChannels = new ConcurrentHashMap<>();

    public LisInboundServerStarter(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
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
                                // 60s 无读 → 断连
                                .addLast(new IdleStateHandler(READER_IDLE_SECONDS, 0, 0, TimeUnit.SECONDS))
                                // MLLP 帧解码: VT ... FS CR
                                .addLast(new MllpFrameDecoder())
                                // 业务处理: 解析 HL7 → 发 ACK → 投递 MQ
                                .addLast(new LisInboundHandler(hospitalCode, rabbitTemplate));
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

        private static final byte VT = 0x0B;
        private static final byte FS = 0x1C;
        private static final byte CR = 0x0D;

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
            while (in.readableBytes() > 0) {
                // 找 VT 标记帧头
                int vtIdx = indexOf(in, VT);
                if (vtIdx < 0) {
                    // 没有 VT，跳过全部（可能是垃圾数据）
                    in.skipBytes(in.readableBytes());
                    return;
                }
                // 跳过 VT 之前的数据
                in.skipBytes(vtIdx);
                in.skipBytes(1); // 跳过 VT

                // 找 FS+CR 标记帧尾
                int fsIdx = indexOf(in, FS);
                if (fsIdx < 0) {
                    // FS 还没到，回退读指针等待更多数据
                    in.readerIndex(in.readerIndex() - 1);
                    return;
                }
                if (in.readableBytes() < fsIdx + 2) {
                    // FS 后面不够 CR 的长度，回退等待
                    in.readerIndex(in.readerIndex() - 1);
                    return;
                }
                if (in.getByte(in.readerIndex() + fsIdx + 1) != CR) {
                    // 不是有效的 FS+CR，跳过这个 FS 继续找
                    in.skipBytes(fsIdx + 1);
                    continue;
                }

                // 提取 HL7 消息体 (VT 和 FS+CR 之间的内容)
                byte[] hl7 = new byte[fsIdx];
                in.readBytes(hl7);
                in.skipBytes(2); // 跳过 FS + CR
                out.add(hl7);
            }
        }

        private static int indexOf(ByteBuf in, byte b) {
            for (int i = 0; i < in.readableBytes(); i++) {
                if (in.getByte(in.readerIndex() + i) == b) {
                    return i;
                }
            }
            return -1;
        }
    }

    /**
     * LIS 入站业务处理器 — 解析 HL7 + 发送 ACK + 投递 RabbitMQ。
     */
    @ChannelHandler.Sharable
    static class LisInboundHandler extends SimpleChannelInboundHandler<byte[]> {

        private static final DateTimeFormatter DTM_FMT =
                DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

        private final String hospitalCode;
        private final RabbitTemplate rabbitTemplate;

        LisInboundHandler(String hospitalCode, RabbitTemplate rabbitTemplate) {
            this.hospitalCode = hospitalCode;
            this.rabbitTemplate = rabbitTemplate;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, byte[] hl7Bytes) {
            String hl7 = new String(hl7Bytes, StandardCharsets.UTF_8).trim();
            if (hl7.isEmpty()) return;

            String msgType = extract(hl7, 8);       // MSH-9
            String msgControlId = extract(hl7, 9);  // MSH-10

            try {
                Map<String, String> msg = new HashMap<>();
                msg.put("hospitalCode", hospitalCode);
                msg.put("messageType", msgType);
                msg.put("messageControlId", msgControlId);
                msg.put("rawMessage", hl7);

                rabbitTemplate.convertAndSend("myla.lis", "lis.inbound", msg);
                log.info("[LIS-IN] published to lis.inbound: hospital={}, type={}, msgId={}",
                        hospitalCode, msgType, msgControlId);

                writeAck(ctx, hl7, "AA", "OK");
            } catch (Exception e) {
                log.error("Failed to process HL7 from {}: {}", hospitalCode, e.getMessage());
                writeAck(ctx, hl7, "AR", e.getMessage());
            }
        }

        /** 从 HL7 MSH 段中提取第 n 个字段（0-indexed） */
        private static String extract(String hl7, int fieldIdx) {
            try {
                String[] segs = hl7.split("\r|\n");
                if (segs.length > 0 && segs[0].startsWith("MSH")) {
                    String[] fields = segs[0].split("\\|");
                    if (fields.length > fieldIdx) return fields[fieldIdx];
                }
            } catch (Exception ignored) {}
            return fieldIdx == 9 ? "" : "UNKNOWN";
        }

        /** 构造 HL7 ACK (MLLP 帧) 并写回客户端 */
        private static void writeAck(ChannelHandlerContext ctx, String request,
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
            } catch (Exception ignored) {}

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

            ctx.writeAndFlush(ctx.alloc().buffer(mllpFrame.length).writeBytes(mllpFrame));
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

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            if (evt instanceof IdleStateEvent) {
                log.warn("LIS idle timeout ({}s), closing: hospital={}, remote={}",
                        READER_IDLE_SECONDS, hospitalCode, ctx.channel().remoteAddress());
                ctx.close();
            }
        }
    }
}
