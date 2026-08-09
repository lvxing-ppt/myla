package com.mlms.oes.gateway.channel;

import com.mlms.oes.gateway.core.context.DriverConfig;
import com.mlms.oes.gateway.core.model.ConnectionError;
import com.mlms.oes.gateway.core.spi.CommunicationChannel;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.DefaultThreadFactory;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * 基于 Netty 的 TCP 通信通道实现。
 * <p>
 * 实现 {@link CommunicationChannel} 接口，使用 Netty NIO 框架进行高性能 TCP 通信。
 * 相比基础 {@link TcpChannel}，本实现提供：
 * <ul>
 *   <li>多连接并发支持 — 多台仪器可同时连接同一端口</li>
 *   <li>非阻塞 I/O — 基于 epoll NIO 模型，4 个 worker 线程即可处理数百连接</li>
 *   <li>空闲检测 — 60 秒无数据自动断连，防止僵死连接</li>
 *   <li>优雅停机 — shutdownGracefully() 确保在途数据写完再关闭</li>
 *   <li>连接数限制 — 默认最大 10 个并发连接，防止资源耗尽</li>
 * </ul>
 * </p>
 *
 * <h3>线程模型：</h3>
 * <pre>
 * bossGroup (1 线程) → accept 新连接 → 分配给 workerGroup
 * workerGroup (4 线程) → 处理 IO 读写 → 回调 messageListener
 * </pre>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * NettyTcpChannel channel = new NettyTcpChannel();
 * channel.setMessageListener(data -> processData(data));
 * channel.setErrorListener(err -> handleError(err));
 *
 * DriverConfig.ChannelConfig config = new DriverConfig.ChannelConfig();
 * config.setPort(9001);
 * channel.open(config);
 *
 * // ... 使用通道 ...
 *
 * channel.close();
 * }</pre>
 *
 * @author MLMS Team
 */
@Slf4j
public class NettyTcpChannel implements CommunicationChannel {

    /** 全局共享的 boss 线程组 — 负责 accept 新连接 */
    private static final EventLoopGroup SHARED_BOSS_GROUP =
            new NioEventLoopGroup(1, new DefaultThreadFactory("netty-boss", true));

    /** 全局共享的 worker 线程组 — 负责 IO 读写 */
    private static final EventLoopGroup SHARED_WORKER_GROUP =
            new NioEventLoopGroup(4, new DefaultThreadFactory("netty-worker", true));

    /** 引用计数，当没有 NettyTcpChannel 实例使用时才真正关闭线程组 */
    private static final AtomicInteger INSTANCE_COUNT = new AtomicInteger(0);

    /** 最大并发连接数 */
    private static final int MAX_CONNECTIONS = 10;

    /** 空闲检测超时（秒），超过此时间无数据交互则断开连接 */
    private static final int READER_IDLE_SECONDS = 60;

    /** 消息监听器 — 收到完整数据时回调 */
    private Consumer<byte[]> messageListener;

    /** 错误监听器 — 发生通道错误时回调 */
    private Consumer<ConnectionError> errorListener;

    /** 当前绑定的服务端 Channel */
    private Channel serverChannel;

    /** 通道运行状态标志 */
    private volatile boolean running;

    /** 当前活跃连接数 */
    private final AtomicInteger activeConnections = new AtomicInteger(0);

    /** 绑定的端口号 */
    private int bindPort;

    /**
     * 获取通道类型标识。
     * @return 固定返回 "TCP-NETTY"
     */
    @Override
    public String getChannelType() {
        return "TCP-NETTY";
    }

    /**
     * 启动 Netty TCP 服务器，开始在指定端口监听仪器连接。
     * <p>
     * 使用共享的 EventLoopGroup 避免每个通道实例都创建独立线程。
     * 首次 open 时引用计数递增，close 时递减，计数归零时真正关闭线程组。
     * </p>
     *
     * @param config 通道配置，需提供 port（监听端口）
     * @throws RuntimeException 如果端口绑定失败
     */
    @Override
    public void open(DriverConfig.ChannelConfig config) {
        this.bindPort = config.getPort();
        INSTANCE_COUNT.incrementAndGet();

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(SHARED_BOSS_GROUP, SHARED_WORKER_GROUP)
                .channel(NioServerSocketChannel.class)
                // accept 队列长度
                .option(ChannelOption.SO_BACKLOG, 128)
                // 快速重用端口（重启时不需等待 TIME_WAIT）
                .option(ChannelOption.SO_REUSEADDR, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
                // 接收缓冲区 64KB
                .childOption(ChannelOption.SO_RCVBUF, 65536)
                // 发送缓冲区 64KB
                .childOption(ChannelOption.SO_SNDBUF, 65536)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        // 连接数限制
                        if (activeConnections.get() >= MAX_CONNECTIONS) {
                            log.warn("Max connections ({}) reached, rejecting: {}",
                                    MAX_CONNECTIONS, ch.remoteAddress());
                            ch.close();
                            return;
                        }
                        activeConnections.incrementAndGet();
                        log.info("Instrument connected: {} (active={})",
                                ch.remoteAddress(), activeConnections.get());

                        ch.pipeline()
                                // 空闲检测：60 秒无读 → 断连
                                .addLast(new IdleStateHandler(
                                        READER_IDLE_SECONDS, 0, 0, TimeUnit.SECONDS))
                                // 业务处理器
                                .addLast(new InstrumentChannelHandler());
                    }
                });

        try {
            serverChannel = bootstrap.bind(config.getPort()).sync().channel();
            running = true;
            log.info("NettyTcpChannel started on port {} (active instances={})",
                    config.getPort(), INSTANCE_COUNT.get());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            INSTANCE_COUNT.decrementAndGet();
            throw new RuntimeException("Failed to bind port " + config.getPort(), e);
        }
    }

    /**
     * 关闭 Netty TCP 通道。
     * <p>
     * 先关闭绑定的 ServerSocketChannel（停止接受新连接），再向下传递 channelClosed 事件
     * 通知各连接 Channel 的 handler 关闭其连接。优雅停机等待 3 秒。
     * 当所有 NettyTcpChannel 实例都关闭后，才真正关闭共享线程组。
     * </p>
     */
    @Override
    public void close() {
        running = false;
        if (serverChannel != null) {
            serverChannel.close().awaitUninterruptibly(3000);
        }
        int remaining = INSTANCE_COUNT.decrementAndGet();
        if (remaining <= 0) {
            SHARED_WORKER_GROUP.shutdownGracefully(2, 5, TimeUnit.SECONDS);
            SHARED_BOSS_GROUP.shutdownGracefully(2, 5, TimeUnit.SECONDS);
            log.info("NettyTcpChannel all instances closed, shared thread pools shut down");
        } else {
            log.info("NettyTcpChannel closed on port {} ({} instances remaining)",
                    bindPort, remaining);
        }
    }

    /**
     * 检查通道是否处于运行状态。
     * @return true 如果通道正在监听
     */
    @Override
    public boolean isOpen() {
        return running && serverChannel != null && serverChannel.isActive();
    }

    /**
     * 向最近连接的仪器发送数据。
     * <p><b>注意：</b>由于可能有多台仪器连接，此方法发送到当前活跃连接中
     * 最近建立的那个。如需向特定仪器发送，应通过 Driver 层管理 Channel 引用。</p>
     *
     * @param data 待发送的字节数组
     * @throws RuntimeException 如果没有活跃连接或发送失败
     */
    @Override
    public void send(byte[] data) {
        if (serverChannel == null) {
            throw new RuntimeException("No active connection");
        }
        // 通过 ServerChannel 没有直接发送到子 Channel 的途径，
        // 由 InstrumentChannelHandler 维护 latestChannel 引用
        Channel ch = InstrumentChannelHandler.latestChannel;
        if (ch == null || !ch.isActive()) {
            throw new RuntimeException("No active instrument connection");
        }
        ByteBuf buf = ch.alloc().buffer(data.length);
        buf.writeBytes(data);
        ch.writeAndFlush(buf).addListener((ChannelFutureListener) future -> {
            if (!future.isSuccess()) {
                log.error("Send failed to {}", ch.remoteAddress(), future.cause());
            }
        });
    }

    @Override
    public void setMessageListener(Consumer<byte[]> l) {
        this.messageListener = l;
    }

    @Override
    public void setErrorListener(Consumer<ConnectionError> l) {
        this.errorListener = l;
    }

    // ==================== 内部 Netty Handler ====================

    /**
     * Netty Channel 的业务处理器。
     * <p>每个仪器连接对应一个 Handler 实例。负责：</p>
     * <ul>
     *   <li>接收数据 → 回调 messageListener</li>
     *   <li>连接断开 → 递减连接计数</li>
     *   <li>异常发生 → 回调 errorListener</li>
     *   <li>空闲超时 → 主动断连</li>
     * </ul>
     */
    @ChannelHandler.Sharable
    private class InstrumentChannelHandler extends SimpleChannelInboundHandler<ByteBuf> {

        /** 最近活跃的 Channel，用于 send() 方法定位发送目标 */
        static volatile Channel latestChannel;

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            latestChannel = ctx.channel();
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
            // 从 ByteBuf 中读取所有可读字节
            byte[] data = new byte[msg.readableBytes()];
            msg.readBytes(data);

            if (messageListener != null) {
                try {
                    messageListener.accept(data);
                } catch (Exception e) {
                    log.error("Message listener error for {}", ctx.channel().remoteAddress(), e);
                }
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            int remaining = activeConnections.decrementAndGet();
            log.info("Instrument disconnected: {} (active={})",
                    ctx.channel().remoteAddress(), remaining);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.error("Channel exception for {}: {}",
                    ctx.channel().remoteAddress(), cause.getMessage());
            if (errorListener != null) {
                ConnectionError err = new ConnectionError();
                err.setChannelType("TCP-NETTY");
                err.setMessage(cause.getMessage());
                err.setCause(cause);
                errorListener.accept(err);
            }
            ctx.close();
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            if (evt instanceof IdleStateEvent) {
                log.warn("Instrument idle timeout ({}s), closing connection: {}",
                        READER_IDLE_SECONDS, ctx.channel().remoteAddress());
                ctx.close();
            }
        }
    }
}
