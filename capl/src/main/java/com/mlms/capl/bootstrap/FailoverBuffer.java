package com.mlms.capl.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mlms.oes.common.core.constant.MqBinding;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;

/**
 * MQ 故障时本地持久化降级缓冲区。
 *
 * <h3>设计</h3>
 * <p>每次 MQ 发送失败时，将消息以 JSON 文件写入本地目录（原子 rename 避免半写），
 * 后台线程定时扫描并重放到 MQ，成功后删除文件。</p>
 *
 * <h3>一致性</h3>
 * <p>Redis 去重键在 {@link LisInboundHandler} 中已设置，LIS 重传会被去重。
 * 本地文件 + 去重键 = 消息不会丢也不会重复。</p>
 */
@Slf4j
public class FailoverBuffer implements AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path bufferDir;
    private final RabbitTemplate rabbitTemplate;
    private final ScheduledExecutorService replayExecutor;
    private volatile boolean running = true;

    /**
     * @param baseDir        缓冲区根目录，每个医院在此目录下有独立子目录
     * @param hospitalCode   医院编码（用于目录隔离）
     * @param rabbitTemplate MQ 模板
     * @param replayIntervalSec 重放间隔（秒）
     */
    public FailoverBuffer(Path baseDir, String hospitalCode,
                          RabbitTemplate rabbitTemplate, int replayIntervalSec) {
        this.bufferDir = baseDir.resolve(hospitalCode);
        this.rabbitTemplate = rabbitTemplate;
        try {
            Files.createDirectories(bufferDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create failover buffer dir: " + bufferDir, e);
        }

        this.replayExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "mq-failover-replay-" + hospitalCode);
            t.setDaemon(true);
            return t;
        });
        replayExecutor.scheduleWithFixedDelay(
                this::replay, replayIntervalSec, replayIntervalSec, TimeUnit.SECONDS);

        log.info("FailoverBuffer ready: dir={}, replayInterval={}s", bufferDir, replayIntervalSec);
    }

    /**
     * 将消息持久化到本地文件（原子写入：先写 .tmp，再 rename）。
     *
     * @return true 表示写入成功，false 表示写入失败（调用方根据返回值决定 AA/AR）
     */
    public boolean buffer(String hospitalCode, String hl7, String msgControlId, String msgType) {
        try {
            Map<String, String> payload = new LinkedHashMap<>();
            payload.put("hospitalCode", hospitalCode);
            payload.put("messageType", msgType);
            payload.put("messageControlId", msgControlId);
            payload.put("rawMessage", hl7);

            String filename = String.format("%d_%s.json",
                    System.currentTimeMillis(), msgControlId.replaceAll("[\\\\/:*?\"<>|]", "_"));
            Path tmp = bufferDir.resolve(filename + ".tmp");
            Path target = bufferDir.resolve(filename);

            Files.write(tmp, MAPPER.writeValueAsBytes(payload));
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);

            log.info("[FAILOVER] buffered: file={}, msgId={}", filename, msgControlId);
            return true;
        } catch (IOException e) {
            log.error("[FAILOVER] failed to write buffer file: msgId={}, error={}",
                    msgControlId, e.getMessage());
            return false;
        }
    }

    /** 扫描缓冲区并重放到 MQ，成功则删除文件。由后台线程定时调用。 */
    void replay() {
        if (!running) return;

        try (Stream<Path> files = Files.list(bufferDir)) {
            files.filter(p -> p.toString().endsWith(".json"))
                 .sorted()
                 .forEach(this::replayOne);
        } catch (IOException e) {
            log.error("[FAILOVER] replay scan failed: dir={}, error={}", bufferDir, e.getMessage());
        }
    }

    private void replayOne(Path file) {
        try {
            byte[] data = Files.readAllBytes(file);
            @SuppressWarnings("unchecked")
            Map<String, String> msg = MAPPER.readValue(data, Map.class);

            String msgControlId = msg.getOrDefault("messageControlId", "");
            // 同步确认：重放在后台线程执行，阻塞等待 broker ACK 不影响业务线程。
            // NACK 或超时（5s）会抛异常，文件保留到下次重试。
            rabbitTemplate.invoke(operations -> {
                operations.convertAndSend(
                        MqBinding.LIS_INBOUND.getExchange(),
                        MqBinding.LIS_INBOUND.getRoutingKey(),
                        msg, new CorrelationData(msgControlId));
                operations.waitForConfirmsOrDie(5_000);
                return null;
            });

            Files.delete(file);
            log.info("[FAILOVER] replayed and deleted: file={}, msgId={}",
                    file.getFileName(), msgControlId);
        } catch (Exception e) {
            log.debug("[FAILOVER] replay retry pending: file={}, error={}",
                    file.getFileName(), e.getMessage());
            // 保留文件，下次重试
        }
    }

    @Override
    public void close() {
        running = false;
        replayExecutor.shutdown();
        try {
            if (!replayExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                replayExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            replayExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
