package com.myla.server.gateway;

import com.myla.gateway.core.context.DriverContext;
import com.myla.gateway.devicemgmt.service.InstrumentMgmtService;
import com.myla.result.entity.RawMessage;
import com.myla.server.mapper.RawMessageMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.*;

/**
 * {@link DriverContext} 的默认实现。
 * <p>
 * 为仪器驱动提供与网关基础设施交互的能力。
 * </p>
 *
 * <h3>基础设施对接：</h3>
 * <ul>
 *   <li>原始报文 — 写入 raw_message 表</li>
 *   <li>结果发布 — 通过 RabbitTemplate 发送到 myla.instrument / result.parsed</li>
 *   <li>健康状态 — 维护在内存 ConcurrentHashMap 中</li>
 *   <li>重试调度 — 基于 ScheduledExecutorService 的指数退避</li>
 *   <li>告警通知 — 记录 WARN 日志</li>
 * </ul>
 *
 * @author MyLA Team
 */
@Slf4j
public class DefaultDriverContext implements DriverContext {

    private final String driverId;
    private final String instrumentId;
    private final RabbitTemplate rabbitTemplate;
    private final RawMessageMapper rawMessageMapper;
    private final InstrumentMgmtService instrumentMgmtService;
    private final StringRedisTemplate redisTemplate;

    /** 仪器健康状态缓存：instrumentId -> status */
    private final Map<String, String> healthStatuses = new ConcurrentHashMap<>();

    /** 重试任务调度器 */
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    /** 已注册的重试任务：key -> future */
    private final Map<String, ScheduledFuture<?>> retryTasks = new ConcurrentHashMap<>();

    public DefaultDriverContext(String driverId, String instrumentId,
                                RabbitTemplate rabbitTemplate, RawMessageMapper rawMessageMapper,
                                InstrumentMgmtService instrumentMgmtService,
                                StringRedisTemplate redisTemplate) {
        this.driverId = driverId;
        this.instrumentId = instrumentId;
        this.rabbitTemplate = rabbitTemplate;
        this.rawMessageMapper = rawMessageMapper;
        this.instrumentMgmtService = instrumentMgmtService;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public String getDriverId() {
        return driverId;
    }

    @Override
    public String getInstrumentId() {
        return instrumentId;
    }

    /**
     * 保存原始报文。
     * <p>当前实现仅记录日志。生产环境应写入数据库或对象存储以支持归档和回溯。</p>
     */
    @Override
    public void saveRawMessage(String instrumentId, String messageType, byte[] rawData) {
        RawMessage msg = new RawMessage();
        msg.setInstrumentId(instrumentId);
        msg.setMessageDirection("IN");
        msg.setMessageType(messageType);
        msg.setRawContent(new String(rawData, StandardCharsets.UTF_8));
        msg.setParseStatus("PENDING");
        msg.setReceivedAt(LocalDateTime.now());
        rawMessageMapper.insert(msg);
        log.info("[RAW-MSG] saved to DB: id={}, instrument={}, type={}, length={} bytes",
                msg.getId(), instrumentId, messageType, rawData.length);
    }

    /**
     * 发布解析结果到消息队列。
     * <p>路由键：result.parsed，交换机：myla.instrument。
     * MQ 不可用时仅记录警告，不影响主处理管道。</p>
     */
    @Override
    public void publishResult(byte[] rawData) {
        try {
            rabbitTemplate.convertAndSend("myla.instrument", "result.parsed", rawData);
            log.debug("[PUBLISH] result sent to myla.instrument/result.parsed, size={} bytes", rawData.length);
        } catch (Exception e) {
            log.warn("[PUBLISH] MQ unavailable, skipping: {}", e.getMessage());
        }
    }

    /**
     * 上报仪器健康状态。
     * <p>状态记录到内存 Map 中，同时输出日志。</p>
     */
    @Override
    public void reportHealth(String instrumentId, String status, String message) {
        healthStatuses.put(instrumentId, status);
        // 写入 instrument_registry 表，更新状态和最后心跳时间
        try {
            instrumentMgmtService.updateStatus(instrumentId, status, message);
        } catch (Exception e) {
            log.warn("[HEALTH] DB write failed for {}: {}", instrumentId, e.getMessage());
        }
        log.info("[HEALTH] instrument={}, status={}, message={}", instrumentId, status, message);
    }

    /**
     * 注册指数退避重试任务。
     * <p>从 initialDelayMs 开始，每次失败延迟翻倍，直到达到 maxDelayMs。</p>
     */
    @Override
    public void registerRetryScheduler(String key, Runnable task, long initialDelayMs, long maxDelayMs) {
        log.info("[RETRY] registered task '{}' with initialDelay={}ms, maxDelay={}ms",
                key, initialDelayMs, maxDelayMs);
        scheduleRetry(key, task, initialDelayMs, maxDelayMs, initialDelayMs);
    }

    private void scheduleRetry(String key, Runnable task, long initialDelayMs, long maxDelayMs, long currentDelay) {
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            try {
                log.debug("[RETRY] executing task '{}'", key);
                task.run();
                retryTasks.remove(key);
                log.info("[RETRY] task '{}' completed successfully", key);
            } catch (Exception e) {
                long nextDelay = Math.min(currentDelay * 2, maxDelayMs);
                log.warn("[RETRY] task '{}' failed, retrying in {}ms: {}", key, nextDelay, e.getMessage());
                scheduleRetry(key, task, initialDelayMs, maxDelayMs, nextDelay);
            }
        }, currentDelay, TimeUnit.MILLISECONDS);
        retryTasks.put(key, future);
    }

    /**
     * 取消重试任务。
     */
    @Override
    public void cancelRetryScheduler(String key) {
        ScheduledFuture<?> future = retryTasks.remove(key);
        if (future != null) {
            future.cancel(false);
            log.info("[RETRY] cancelled task '{}'", key);
        }
    }

    /**
     * 发送告警通知。
     * <p>当前实现仅记录 WARN 日志。生产环境应通过消息队列发送到通知模块。</p>
     */
    @Override
    public void sendAlert(String instrumentId, String alertType, String message) {
        log.warn("[ALERT] instrument={}, type={}, message={}", instrumentId, alertType, message);
    }

    /**
     * 获取当前所有仪器的健康状态快照。
     * @return instrumentId -> status 的不可变副本
     */
    public Map<String, String> getHealthStatuses() {
        return Map.copyOf(healthStatuses);
    }

    // ==================== 幂等去重 ====================

    private static final String DEDUP_PREFIX = "myla:dedup:";
    private static final Duration DEDUP_TTL = Duration.ofHours(24);

    @Override
    public boolean isDuplicate(byte[] rawData) {
        try {
            String hash = sha256(rawData);
            // 只检查不标记——标记在 markProcessed 中，确保处理成功后才写入
            return Boolean.TRUE.equals(redisTemplate.hasKey(DEDUP_PREFIX + hash));
        } catch (Exception e) {
            // Redis 不可用时降级：允许所有数据通过，宁可重复入库也不丢数据
            log.warn("[DEDUP] Redis unavailable, allowing data through: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void markProcessed(byte[] rawData) {
        try {
            String hash = sha256(rawData);
            redisTemplate.opsForValue().set(DEDUP_PREFIX + hash, "1", DEDUP_TTL);
        } catch (Exception e) {
            log.warn("[DEDUP] Failed to mark processed, may cause duplicate on retry: {}", e.getMessage());
        }
    }

    private String sha256(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return Integer.toHexString(java.util.Arrays.hashCode(data));
        }
    }

    /**
     * 关闭调度器，释放线程资源。
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
