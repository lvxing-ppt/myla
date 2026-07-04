package com.myla.gateway.channel;

import com.myla.gateway.core.spi.CommunicationChannel;
import com.myla.gateway.core.context.DriverConfig;
import com.myla.gateway.core.model.ConnectionError;
import lombok.extern.slf4j.Slf4j;
import java.io.IOException;
import java.nio.file.*;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * 文件监听通道实现。
 * <p>
 * 实现 {@link CommunicationChannel} 接口，通过定期扫描指定目录来读取仪器导出的数据文件。
 * 适用于以文件方式输出结果的仪器（如通过共享目录导出 ASTM/HL7 文本文件的场景）。
 * </p>
 *
 * <h3>工作流程：</h3>
 * <ol>
 *   <li>按配置的轮询间隔（pollIntervalMs）定时扫描目录</li>
 *   <li>使用文件模式（filePattern）过滤匹配的文件</li>
 *   <li>通过 {@code processed} 集合去重，避免重复处理同一文件</li>
 *   <li>读取文件内容后回调消息监听器</li>
 *   <li>处理完成后将文件移动到 archive 子目录归档</li>
 * </ol>
 *
 * <p><b>注意：此通道为只读模式，不支持 send() 方法。</b></p>
 *
 * @author MyLA Team
 */
@Slf4j
public class FileChannel implements CommunicationChannel {

    /** 消息监听器，接收到文件数据时回调 */
    private Consumer<byte[]> messageListener;

    /** 错误监听器，发生 IO 异常时回调 */
    private Consumer<ConnectionError> errorListener;

    /** 通道运行状态标志 */
    private volatile boolean running;

    /** 定时扫描调度器 */
    private ScheduledExecutorService scheduler;

    /**
     * 已处理文件名的去重集合。
     * 使用 ConcurrentHashMap 的 keySet 实现线程安全的去重记录。
     */
    private final Set<String> processed = ConcurrentHashMap.newKeySet();

    /**
     * 获取通道类型标识。
     * @return 固定返回 "FILE"
     */
    @Override
    public String getChannelType() {
        return "FILE";
    }

    /**
     * 打开文件监听通道。
     * <p>
     * 确保监听的目录存在，然后启动定时任务按配置的间隔轮询文件。
     * 匹配的文件被读取后移动到 archive 子目录。
     * </p>
     *
     * @param c 通道配置，需提供 directory（监听目录）、filePattern（文件匹配模式）和 pollIntervalMs（轮询间隔）
     * @throws RuntimeException 如果目录创建失败
     */
    @Override
    public void open(DriverConfig.ChannelConfig c) {
        running = true;
        scheduler = Executors.newSingleThreadScheduledExecutor();
        Path dir = Paths.get(c.getDirectory());
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        // 定时轮询任务
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                // 编译文件名匹配正则，默认匹配 .txt 文件
                Pattern p = Pattern.compile(c.getFilePattern() != null ? c.getFilePattern() : ".*\\.txt");
                try (DirectoryStream<Path> s = Files.newDirectoryStream(dir)) {
                    for (Path f : s) {
                        String fn = f.getFileName().toString();
                        if (p.matcher(fn).matches() && processed.add(fn)) {
                            // 读取并回调消息
                            if (messageListener != null) {
                                messageListener.accept(Files.readAllBytes(f));
                            }
                            // 移动到 archive 子目录
                            Path arch = Paths.get(c.getDirectory(), "archive", fn);
                            Files.createDirectories(arch.getParent());
                            Files.move(f, arch, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                }
            } catch (IOException e) {
                if (running && errorListener != null) {
                    ConnectionError err = new ConnectionError();
                    err.setChannelType("FILE");
                    err.setMessage(e.getMessage());
                    errorListener.accept(err);
                }
            }
        }, 0, c.getPollIntervalMs(), TimeUnit.MILLISECONDS);
    }

    /**
     * 关闭文件监听通道，停止调度任务。
     */
    @Override
    public void close() {
        running = false;
        if (scheduler != null) {
            scheduler.shutdown();
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
     * 发送数据操作在此通道中不支持。
     * @param data 待发送的数据
     * @throws UnsupportedOperationException 始终抛出，因为 FileChannel 为只读模式
     */
    @Override
    public void send(byte[] data) {
        throw new UnsupportedOperationException("FileChannel read-only");
    }

    /**
     * 设置消息监听器。
     * @param l 消息消费者，接收文件读取到的字节数组
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
