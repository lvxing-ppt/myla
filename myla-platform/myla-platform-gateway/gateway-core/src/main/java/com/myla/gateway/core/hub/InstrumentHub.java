package com.myla.gateway.core.hub;

import com.myla.gateway.core.spi.InstrumentDriver;
import com.myla.gateway.core.context.DriverConfig;
import com.myla.gateway.core.context.DriverContext;
import com.myla.gateway.core.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 仪器管理中心（Hub）。
 * <p>
 * 作为网关的核心组件，负责管理所有已接入仪器的驱动实例生命周期。
 * 提供驱动的加载、启动、停止、卸载等管理操作，以及命令下发、发现信息查询等运行时操作。
 * </p>
 *
 * <h3>核心职责：</h3>
 * <ul>
 *   <li>维护 instrumentId -> {@link DriverContainer} 的映射关系</li>
 *   <li>维护 instrumentId -> {@link InstrumentDriver} 的直接引用</li>
 *   <li>协调驱动的初始化、启动、停止、卸载完整生命周期</li>
 *   <li>提供异步命令执行接口</li>
 *   <li>提供发现信息查询接口</li>
 *   <li>提供所有仪器状态快照查询</li>
 * </ul>
 *
 * <p><b>线程安全：</b>所有 Map 操作均使用 {@link ConcurrentHashMap}。</p>
 *
 * @author MyLA Team
 */
@Slf4j
@Component
public class InstrumentHub {

    /** instrumentId -> DriverContainer 映射 */
    private final Map<String, DriverContainer> containers = new ConcurrentHashMap<>();

    /** instrumentId -> InstrumentDriver 映射，用于快速查找驱动实例 */
    private final Map<String, InstrumentDriver> drivers = new ConcurrentHashMap<>();

    /**
     * 加载驱动。
     * <p>
     * 创建 {@link DriverContainer}，初始化驱动，并注册到内部映射表。
     * 加载后驱动处于未启动状态，需调用 {@link #startDriver(String)} 启动。
     * </p>
     *
     * @param driver 驱动实例
     * @param config 驱动配置
     * @param context 驱动上下文
     */
    public void loadDriver(InstrumentDriver driver, DriverConfig config, DriverContext context) {
        String instrumentId = config.getInstrumentId();
        DriverContainer container = new DriverContainer(driver, config, context);
        containers.put(instrumentId, container);
        drivers.put(instrumentId, driver);
        driver.initialize(config);
        log.info("Driver loaded: driverId={}, instrumentId={}", driver.getDriverId(), instrumentId);
    }

    /**
     * 启动指定仪器的驱动。
     * @param instrumentId 仪器 ID
     */
    public void startDriver(String instrumentId) {
        DriverContainer container = containers.get(instrumentId);
        if (container != null) {
            container.start();
            log.info("Driver started: {}", instrumentId);
        }
    }

    /**
     * 停止指定仪器的驱动。
     * <p>停止后通信通道关闭，但驱动仍保留在映射表中，可再次启动。</p>
     *
     * @param instrumentId 仪器 ID
     */
    public void stopDriver(String instrumentId) {
        DriverContainer container = containers.get(instrumentId);
        if (container != null) {
            container.stop();
            log.info("Driver stopped: {}", instrumentId);
        }
    }

    /**
     * 卸载指定仪器的驱动。
     * <p>先停止驱动，然后从映射表中移除。卸载后不可再启动，需重新加载。</p>
     *
     * @param instrumentId 仪器 ID
     */
    public void unloadDriver(String instrumentId) {
        stopDriver(instrumentId);
        containers.remove(instrumentId);
        drivers.remove(instrumentId);
        log.info("Driver unloaded: {}", instrumentId);
    }

    /**
     * 向指定仪器发送命令。
     * <p>通过驱动实例异步执行命令，返回 CompletableFuture 供调用方等待结果或注册回调。</p>
     *
     * @param instrumentId 仪器 ID
     * @param command 待发送的仪器命令
     * @return 异步命令执行结果的 Future
     * @throws IllegalArgumentException 如果指定 instrumentId 的驱动不存在
     */
    public CompletableFuture<CommandResult> sendCommand(String instrumentId, InstrumentCommand command) {
        InstrumentDriver driver = drivers.get(instrumentId);
        if (driver == null) {
            throw new IllegalArgumentException("Driver not found: " + instrumentId);
        }
        return driver.executeCommand(command);
    }

    /**
     * 获取指定仪器的发现信息（制造商、型号、固件版本等）。
     *
     * @param instrumentId 仪器 ID
     * @return 仪器发现信息
     * @throws IllegalArgumentException 如果指定 instrumentId 的驱动不存在
     */
    public DiscoveryInfo getDiscoveryInfo(String instrumentId) {
        InstrumentDriver driver = drivers.get(instrumentId);
        if (driver == null) {
            throw new IllegalArgumentException("Driver not found: " + instrumentId);
        }
        return driver.getDiscoveryInfo();
    }

    /**
     * 列出所有已接入的仪器 ID。
     * @return 仪器 ID 列表（不可变副本）
     */
    public List<String> listInstruments() {
        return List.copyOf(containers.keySet());
    }

    /**
     * 获取所有仪器的当前状态快照。
     * @return instrumentId -> InstrumentStatus 的映射
     */
    public Map<String, InstrumentStatus> getAllStatuses() {
        Map<String, InstrumentStatus> statuses = new ConcurrentHashMap<>();
        containers.forEach((id, c) -> statuses.put(id, c.getCurrentStatus()));
        return statuses;
    }
}
