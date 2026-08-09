package com.mlms.oes.gateway.core.spi;

import com.mlms.oes.gateway.core.context.DriverConfig;
import com.mlms.oes.gateway.core.model.ConnectionError;
import java.util.function.Consumer;

/**
 * 仪器通信通道抽象接口（SPI）。
 * <p>
 * 定义与实验室仪器建立连接、收发数据、关闭连接的标准契约。
 * 所有具体通道实现（TCP、文件监听、串口）必须实现此接口。
 * </p>
 *
 * <h3>实现者契约：</h3>
 * <ul>
 *   <li>{@link #getChannelType()} — 必须返回唯一的通道类型标识字符串</li>
 *   <li>{@link #open(DriverConfig.ChannelConfig)} — 建立连接，完成后应能接收数据</li>
 *   <li>{@link #close()} — 释放所有资源，实现应为幂等操作（重复关闭不抛异常）</li>
 *   <li>{@link #isOpen()} — 返回当前连接状态</li>
 *   <li>{@link #send(byte[])} — 发送数据，只读通道可抛出 UnsupportedOperationException</li>
 *   <li>{@link #setMessageListener(Consumer)} — 接收到完整报文时回调此监听器</li>
 *   <li>{@link #setErrorListener(Consumer)} — 发生连接错误时回调此监听器</li>
 * </ul>
 *
 * <h3>生命周期：</h3>
 * <pre>
 * new() -> setMessageListener() / setErrorListener() -> open() -> [send()/receive...] -> close()
 * </pre>
 *
 * @author MLMS Team
 */
public interface CommunicationChannel {

    /**
     * 获取通道类型标识。
     * <p>实现者必须返回唯一的类型字符串，如 "TCP"、"FILE"、"SERIAL"。</p>
     *
     * @return 通道类型字符串
     */
    String getChannelType();

    /**
     * 打开通信通道，与仪器建立连接。
     * <p>
     * 实现者应根据 {@link DriverConfig.ChannelConfig} 中的参数完成连接建立。
     * 对于被动监听模式，此方法启动监听线程；对于主动连接/轮询模式，此方法发起连接或启动轮询任务。
     * </p>
     *
     * @param config 通道配置参数
     * @throws RuntimeException 如果连接建立失败
     */
    void open(DriverConfig.ChannelConfig config);

    /**
     * 关闭通信通道，释放所有资源（Socket、线程池、文件句柄等）。
     * <p><b>实现必须为幂等操作：</b>重复调用 close() 不应抛异常。</p>
     */
    void close();

    /**
     * 检查通道是否处于打开/运行状态。
     * @return true 如果通道当前可用
     */
    boolean isOpen();

    /**
     * 通过通道发送数据到仪器。
     * <p>只读通道（如 FileChannel）应抛出 {@link UnsupportedOperationException}。</p>
     *
     * @param data 待发送的字节数组
     * @throws RuntimeException 如果发送失败
     * @throws UnsupportedOperationException 如果通道不支持发送操作
     */
    void send(byte[] data);

    /**
     * 设置消息监听器。
     * <p>通道接收到完整数据报文后，应通过此 Consumer 回调上层处理逻辑。</p>
     *
     * @param onMessage 消息消费者，接收完整的报文字节数组
     */
    void setMessageListener(Consumer<byte[]> onMessage);

    /**
     * 设置错误监听器。
     * <p>通道层发生 IO 异常或其他通信错误时，应通过此 Consumer 回调上层处理逻辑。</p>
     *
     * @param onError 错误消费者，接收封装后的 {@link ConnectionError}
     */
    void setErrorListener(Consumer<ConnectionError> onError);
}
