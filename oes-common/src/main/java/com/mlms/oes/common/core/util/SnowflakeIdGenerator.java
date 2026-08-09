package com.mlms.oes.common.core.util;

import org.springframework.stereotype.Component;

/**
 * 雪花算法 ID 生成器 (Snowflake).
 * <p>
 * 64 位 Long 型 ID，结构：
 * <pre>
 * 1 bit (保留) | 41 bits 时间戳(ms) | 10 bits 机器ID | 12 bits 序列号
 * </pre>
 * 全局唯一，趋势递增，每秒可生成约 40 万个。
 * </p>
 *
 * <h3>用法：</h3>
 * <ul>
 *   <li>MyBatis-Plus 实体主键：@TableId(type = IdType.ASSIGN_ID)</li>
 *   <li>业务 ID（代码中手动生成）：@Autowired SnowflakeIdGenerator → generator.nextId()</li>
 * </ul>
 */
@Component
public class SnowflakeIdGenerator {

    /** 起始时间戳 (2026-01-01 00:00:00) */
    private static final long EPOCH = 1735689600000L;

    /** 机器 ID 所占位数 */
    private static final long WORKER_ID_BITS = 5L;
    private static final long DATA_CENTER_ID_BITS = 5L;
    /** 序列号所占位数 */
    private static final long SEQUENCE_BITS = 12L;

    /** 最大机器 ID (31) */
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    /** 最大序列号 (4095) */
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    /** 机器 ID 左移位数 = 12 */
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    /** 时间戳左移位数 = 22 */
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATA_CENTER_ID_BITS;

    private final long workerId;
    private final long dataCenterId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;

    public SnowflakeIdGenerator() {
        this(1, 1); // 默认 workerId=1, dataCenterId=1
    }

    public SnowflakeIdGenerator(long workerId, long dataCenterId) {
        if (workerId > MAX_WORKER_ID || workerId < 0) {
            throw new IllegalArgumentException("workerId must be 0.." + MAX_WORKER_ID);
        }
        this.workerId = workerId;
        this.dataCenterId = dataCenterId;
    }

    /**
     * 生成下一个唯一 ID。
     * @return Snowflake 64-bit Long ID
     */
    public synchronized long nextId() {
        long timestamp = System.currentTimeMillis();

        // 时钟回拨检测
        if (timestamp < lastTimestamp) {
            throw new RuntimeException("Clock moved backwards! Refusing to generate ID for "
                + (lastTimestamp - timestamp) + " ms");
        }

        if (timestamp == lastTimestamp) {
            // 同一毫秒内，序列号递增
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                // 序列号用尽，等待下一毫秒
                timestamp = nextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = timestamp;

        return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
            | (dataCenterId << (SEQUENCE_BITS + WORKER_ID_BITS))
            | (workerId << WORKER_ID_SHIFT)
            | sequence;
    }

    /** 生成 String 形式的 ID，用于 VARCHAR 业务 ID 列 */
    public String nextIdStr() {
        return String.valueOf(nextId());
    }

    private long nextMillis(long lastTs) {
        long ts = System.currentTimeMillis();
        while (ts <= lastTs) {
            ts = System.currentTimeMillis();
        }
        return ts;
    }
}
