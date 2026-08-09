package com.mlms.oes.gateway.channel;

import com.mlms.oes.gateway.core.spi.CommunicationChannel;
import com.mlms.oes.gateway.core.context.DriverConfig;
import com.mlms.oes.gateway.core.model.ConnectionError;
import lombok.extern.slf4j.Slf4j;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * TCP 通信通道实现（基于 JDK 原生 ServerSocket）。
 * <p>
 * 实现 {@link CommunicationChannel} 接口，使用阻塞 I/O 模型进行 TCP 通信。
 * 适用于一期快速验证和少量仪器连接的场景。
 * 对于需要多连接并发、高吞吐量的生产环境，请使用 {@link NettyTcpChannel}。
 * </p>
 *
 * <h3>线程模型：</h3>
 * <p>accept 循环和 IO 读取在命名线程池 "tcp-ch-{port}" 中运行，
 * close() 时线程池优雅关闭（等待 3 秒）。</p>
 *
 * <h3>限制：</h3>
 * <ul>
 *   <li>单连接 — 同一时间只处理一个仪器连接（适用于一对一驱动绑定场景）</li>
 *   <li>阻塞 I/O — read() 阻塞直到有数据到达</li>
 * </ul>
 *
 * @author MLMS Team
 * @see NettyTcpChannel
 */
@Slf4j
public class TcpChannel implements CommunicationChannel {

    /** TCP 服务端 Socket，用于监听仪器连接 */
    private ServerSocket serverSocket;

    /** 当前已建立连接的仪器 Socket */
    private Socket socket;

    /** 输入流，用于接收仪器数据 */
    private InputStream in;

    /** 输出流，用于向仪器发送指令 */
    private OutputStream out;

    /** 线程池，负责 accept 和 IO 读取 */
    private ExecutorService executor;

    /** 消息监听器 */
    private Consumer<byte[]> messageListener;

    /** 错误监听器 */
    private Consumer<ConnectionError> errorListener;

    /** 通道运行状态标志 */
    private volatile boolean running;

    /**
     * 获取通道类型标识。
     * @return 固定返回 "TCP"
     */
    @Override
    public String getChannelType() {
        return "TCP";
    }

    /**
     * 打开 TCP 通道并开始监听。
     * <p>
     * 在配置的端口上启动 ServerSocket，在新线程中循环接受仪器连接。
     * 每次 accept() 成功后，持续从输入流读取数据并回调消息监听器。
     * 连接断开会触发错误回调，但不会退出 accept 循环（自动重连等待）。
     * </p>
     *
     * @param config 通道配置，需提供 port（监听端口）
     * @throws RuntimeException 如果 ServerSocket 创建失败
     */
    @Override
    public void open(DriverConfig.ChannelConfig config) {
        try {
            serverSocket = new ServerSocket(config.getPort());
            running = true;
            log.info("TCP channel listening on port {}", config.getPort());

            // 使用单线程池，避免每次 open() 都 new Thread
            this.executor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "tcp-ch-" + config.getPort());
                t.setDaemon(true);
                return t;
            });

            executor.submit(() -> {
                while (running) {
                    try {
                        socket = serverSocket.accept();
                        in = socket.getInputStream();
                        out = socket.getOutputStream();

                        byte[] buf = new byte[65536];
                        int n;
                        while (running && (n = in.read(buf)) > 0) {
                            byte[] data = new byte[n];
                            System.arraycopy(buf, 0, data, 0, n);
                            if (messageListener != null) {
                                messageListener.accept(data);
                            }
                        }
                    } catch (IOException e) {
                        if (running && errorListener != null) {
                            ConnectionError err = new ConnectionError();
                            err.setChannelType("TCP");
                            err.setMessage(e.getMessage());
                            errorListener.accept(err);
                        }
                    }
                }
            });
        } catch (IOException e) {
            throw new RuntimeException("TCP channel open failed on port " + config.getPort(), e);
        }
    }

    /**
     * 关闭 TCP 通道。
     * <p>设置关闭标志 → 关闭 IO 资源 → 关闭 ServerSocket → 优雅关闭线程池（等3秒）。</p>
     */
    @Override
    public void close() {
        running = false;
        try { if (in != null) in.close(); } catch (IOException ignored) {}
        try { if (out != null) out.close(); } catch (IOException ignored) {}
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
        if (executor != null) {
            executor.shutdown();
            try { executor.awaitTermination(3, TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }

    /**
     * 检查通道是否处于运行状态。
     * @return true 如果通道正在运行
     */
    @Override
    public boolean isOpen() {
        return running;
    }

    /**
     * 通过 TCP 连接向仪器发送数据。
     * @param data 待发送的字节数组
     * @throws RuntimeException 如果发送失败（连接不存在或 IO 异常）
     */
    @Override
    public void send(byte[] data) {
        try {
            out.write(data);
            out.flush();
        } catch (IOException e) {
            throw new RuntimeException("Send failed", e);
        }
    }

    /**
     * 设置消息监听器。
     * @param l 消息消费者，接收从仪器读取到的字节数组
     */
    @Override
    public void setMessageListener(Consumer<byte[]> l) {
        this.messageListener = l;
    }

    /**
     * 设置错误监听器。
     * @param l 错误消费者，接收 IO 异常封装后的 {@link ConnectionError}
     */
    @Override
    public void setErrorListener(Consumer<ConnectionError> l) {
        this.errorListener = l;
    }
}
