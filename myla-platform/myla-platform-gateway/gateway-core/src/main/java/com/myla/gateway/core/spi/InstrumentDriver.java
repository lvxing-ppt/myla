package com.myla.gateway.core.spi;

import com.myla.common.api.enums.CommunicationMode;
import com.myla.gateway.core.context.DriverConfig;
import com.myla.gateway.core.context.DriverContext;
import com.myla.gateway.core.model.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 仪器驱动接口（SPI 核心）。
 * <p>
 * 定义仪器驱动的标准契约，是所有仪器适配器的顶层抽象。
 * 每种仪器型号需提供对应的驱动实现，负责管理通信通道、数据分桢、数据解析、
 * 结果上报、命令执行和状态监控等全部逻辑。
 * </p>
 *
 * <h3>实现者必须实现的方法：</h3>
 * <ul>
 *   <li>{@link #getDriverId()} — 驱动唯一标识</li>
 *   <li>{@link #getDisplayName()} — 驱动显示名称</li>
 *   <li>{@link #getVersion()} — 驱动版本号</li>
 *   <li>{@link #getMode()} — 通信模式（被动监听/主动轮询/主动连接）</li>
 *   <li>{@link #initialize(DriverConfig)} — 初始化驱动配置</li>
 *   <li>{@link #start(DriverContext)} — 启动数据采集</li>
 *   <li>{@link #stop()} — 停止数据采集</li>
 *   <li>{@link #testConnection()} — 测试连接状态</li>
 *   <li>{@link #registerListener(DataEventListener)} — 注册数据事件监听器</li>
 *   <li>{@link #getDiscoveryInfo()} — 返回仪器发现信息</li>
 * </ul>
 *
 * <h3>可选覆盖的默认方法：</h3>
 * <ul>
 *   <li>{@link #executeCommand(InstrumentCommand)} — 命令执行（默认返回不支持）</li>
 *   <li>{@link #registerTelemetryListener(TelemetryListener)} — 遥测监听（默认为空操作）</li>
 *   <li>{@link #getMaintenanceCapabilities()} — 维护能力（默认返回空列表）</li>
 *   <li>{@link #executeMaintenance(MaintenanceCommand)} — 维护操作执行（默认返回不支持）</li>
 * </ul>
 *
 * <h3>生命周期：</h3>
 * <pre>
 * new() -> initialize() -> start() -> [运行中：数据接收/命令执行] -> stop()
 * </pre>
 *
 * @author MyLA Team
 */
public interface InstrumentDriver {

    /**
     * 获取驱动唯一标识。
     * @return 驱动 ID，如 "vitek2-v1.0"、"proprietary-v1.0"
     */
    String getDriverId();

    /**
     * 获取驱动显示名称，用于 UI 展示和日志。
     * @return 显示名称，如 "VITEK 2 Driver"
     */
    String getDisplayName();

    /**
     * 获取驱动版本号。
     * @return 语义化版本号字符串，如 "1.0"
     */
    String getVersion();

    /**
     * 获取驱动使用的通信模式。
     * @return 通信模式枚举值
     */
    CommunicationMode getMode();

    /**
     * 初始化驱动配置。
     * <p>在 start() 之前调用。实现者应在此方法中完成配置校验和资源准备。</p>
     *
     * @param config 驱动配置
     */
    void initialize(DriverConfig config);

    /**
     * 启动驱动，开始数据采集。
     * <p>
     * 实现者应在此方法中：
     * <ol>
     *   <li>打开通信通道（根据通信模式建立连接或启动监听）</li>
     *   <li>设置通道的消息监听器和错误监听器</li>
     *   <li>通过 {@link DriverContext} 上报健康状态</li>
     *   <li>将解析后的结果通过 {@link DataEventListener} 回调上层</li>
     * </ol>
     * </p>
     *
     * @param ctx 驱动上下文，提供基础设施能力
     */
    void start(DriverContext ctx);

    /**
     * 停止驱动，停止数据采集并释放通信资源。
     * <p>实现应为幂等操作：对已停止的驱动重复调用 stop() 不应抛异常。</p>
     */
    void stop();

    /**
     * 测试与仪器的连接状态。
     * @return true 如果连接正常
     */
    boolean testConnection();

    /**
     * 注册数据事件监听器。
     * <p>驱动在解析出结果或遇到错误时，通过此监听器回调上层处理逻辑。</p>
     *
     * @param listener 数据事件监听器实现
     */
    void registerListener(DataEventListener listener);

    /**
     * 执行仪器命令（可选，默认不支持）。
     * <p>支持双向命令交互的仪器驱动应覆盖此方法。</p>
     *
     * @param command 待执行的命令
     * @return 异步执行结果的 Future
     */
    default CompletableFuture<CommandResult> executeCommand(InstrumentCommand command) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("Commands not supported"));
    }

    /**
     * 获取仪器发现信息（制造商、型号、固件版本等）。
     * @return 仪器发现信息对象
     */
    DiscoveryInfo getDiscoveryInfo();

    /**
     * 注册遥测数据监听器（可选，默认为空操作）。
     * <p>支持上报遥测数据（CPU 温度、试剂余量等）的仪器驱动应覆盖此方法。</p>
     *
     * @param listener 遥测数据监听器实现
     */
    default void registerTelemetryListener(TelemetryListener listener) {}

    /**
     * 获取仪器支持的维护能力列表（可选，默认返回空列表）。
     * @return 维护能力列表
     */
    default List<MaintenanceCapability> getMaintenanceCapabilities() {
        return List.of();
    }

    /**
     * 执行维护操作（可选，默认不支持）。
     * <p>支持维护操作（固件升级、校准、自检等）的仪器驱动应覆盖此方法。</p>
     *
     * @param cmd 维护命令
     * @return 异步执行结果的 Future
     */
    default CompletableFuture<CommandResult> executeMaintenance(MaintenanceCommand cmd) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("Maintenance not supported"));
    }
}
