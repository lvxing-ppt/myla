package com.myla.gateway.channel;

import com.myla.gateway.core.spi.CommunicationChannel;
import com.myla.gateway.core.context.DriverConfig;
import com.myla.gateway.core.model.ConnectionError;
import lombok.extern.slf4j.Slf4j;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.function.Consumer;

/**
 * TCP 通信通道实现。
 * <p>
 * 实现 {@link CommunicationChannel} 接口，基于 TCP ServerSocket 进行被动监听。
 * 仪器作为 TCP 客户端主动连接到网关，建立长连接后进行双向数据通信。
 * </p>
 *
 * <h3>工作流程：</h3>
 * <ol>
 *   <li>{@link #open(DriverConfig.ChannelConfig)} 在指定端口启动 ServerSocket 监听</li>
 *   <li>在新线程中循环 accept() 等待仪器连接</li>
 *   <li>建立连接后持续读取输入流数据，每次读取到数据即回调消息监听器</li>
 *   <li>支持通过 {@link #send(byte[])} 方法向仪器发送指令</li>
 *   <li>{@link #close()} 关闭所有 IO 资源和 Socket</li>
 * </ol>
 *
 * <p><b>线程模型：</b>accept 循环和 IO 读取均在独立守护线程 "tcp-chan" 中运行。</p>
 *
 * @author MyLA Team
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

            // 在独立线程中处理连接和 IO 读取
            new Thread(() -> {
                while (running) {
                    try {
                        // accept() 阻塞等待仪器连接
                        socket = serverSocket.accept();
                        in = socket.getInputStream();
                        out = socket.getOutputStream();

                        // 循环读取数据，直到连接断开或通道关闭
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
                        // 通道仍在运行时发生的异常才上报
                        if (running && errorListener != null) {
                            ConnectionError err = new ConnectionError();
                            err.setChannelType("TCP");
                            err.setMessage(e.getMessage());
                            errorListener.accept(err);
                        }
                    }
                }
            }, "tcp-chan").start();
        } catch (IOException e) {
            throw new RuntimeException("TCP channel open failed on port " + config.getPort(), e);
        }
    }

    /**
     * 关闭 TCP 通道。
     * <p>
     * 依次关闭输入流、输出流、Socket、ServerSocket。
     * 每个资源关闭时的异常均被静默忽略，确保所有资源都有机会被释放。
     * </p>
     */
    @Override
    public void close() {
        running = false;
        try { if (in != null) in.close(); } catch (IOException ignored) {}
        try { if (out != null) out.close(); } catch (IOException ignored) {}
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
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
