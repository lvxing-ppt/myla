package com.mlms.oes.gateway.core.context;

/**
 * 驱动上下文接口。
 * <p>
 * 为 {@link com.mlms.oes.gateway.core.spi.InstrumentDriver} 提供与网关基础设施交互的能力。
 * 驱动通过此接口可以：
 * <ul>
 *   <li>持久化原始报文</li>
 *   <li>发布解析后的结果到消息队列</li>
 *   <li>上报仪器健康状态</li>
 *   <li>注册/取消重试调度任务</li>
 *   <li>发送告警通知</li>
 * </ul>
 * 驱动不直接依赖具体的存储或消息中间件实现，所有基础设施操作均通过此接口完成。
 * </p>
 *
 * @author MLMS Team
 */
public interface DriverContext {

    /**
     * 获取驱动 ID。
     * @return 驱动唯一标识
     */
    String getDriverId();

    /**
     * 获取仪器 ID。
     * @return 仪器唯一标识
     */
    String getInstrumentId();

    /**
     * 保存原始报文到持久化存储。
     * <p>用于原始数据归档和回溯分析。</p>
     *
     * @param instrumentId 仪器 ID
     * @param messageType 报文类型标签，如 "ASTM"、"HL7"、"PROPRIETARY"
     * @param rawData 原始报文字节数组
     */
    void saveRawMessage(String instrumentId, String messageType, byte[] rawData);

    /**
     * 发布解析后的结果到消息队列。
     * <p>下游业务模块（如审核、报告）通过订阅消息队列消费结果数据。</p>
     *
     * @param rawData 原始报文字节数组（与结果一起发布，供下游追溯）
     */
    void publishResult(byte[] rawData);

    /**
     * 上报仪器健康状态。
     *
     * @param instrumentId 仪器 ID
     * @param status 状态字符串，如 "ONLINE"、"OFFLINE"、"ERROR"
     * @param message 详细状态描述
     */
    void reportHealth(String instrumentId, String status, String message);

    /**
     * 注册重试调度任务。
     * <p>用于实现指数退避重连等场景。调度器使用递增延迟，从 initialDelayMs 开始，
     * 每次失败后延迟翻倍，直到达到 maxDelayMs。</p>
     *
     * @param key 任务唯一键，用于后续取消
     * @param task 待调度的任务
     * @param initialDelayMs 初始延迟（毫秒）
     * @param maxDelayMs 最大延迟上限（毫秒）
     */
    void registerRetryScheduler(String key, Runnable task, long initialDelayMs, long maxDelayMs);

    /**
     * 取消重试调度任务。
     * @param key 任务唯一键
     */
    void cancelRetryScheduler(String key);

    /**
     * 发送告警通知。
     */
    void sendAlert(String instrumentId, String alertType, String message);

    /**
     * 检查原始数据是否已处理过（幂等去重）。
     * <p>基于 SHA-256 hash + Redis SetNX，24小时 TTL。</p>
     *
     * @param rawData 原始报文字节
     * @return true 如果已处理过
     */
    boolean isDuplicate(byte[] rawData);

    /**
     * 标记原始数据为已处理（幂等去重）。
     * <p>必须在成功处理后才调用，用于防重。</p>
     *
     * @param rawData 原始报文字节
     */
    void markProcessed(byte[] rawData);
}
