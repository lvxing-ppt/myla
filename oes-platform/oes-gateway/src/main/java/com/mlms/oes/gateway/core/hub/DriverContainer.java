package com.mlms.oes.gateway.core.hub;

import com.mlms.oes.gateway.core.spi.InstrumentDriver;
import com.mlms.oes.gateway.core.context.DriverConfig;
import com.mlms.oes.gateway.core.context.DriverContext;
import com.mlms.oes.gateway.core.model.InstrumentStatus;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 驱动容器。
 * <p>
 * 包装一个 {@link InstrumentDriver} 实例及其配置和上下文，提供统一的启停控制和状态管理。
 * 使用原子布尔值 {@code running} 防止重复启动或重复停止。
 * </p>
 *
 * <p><b>线程安全：</b>start/stop 使用 CAS 操作保证幂等性，currentStatus 使用 volatile 保证可见性。</p>
 *
 * @author MLMS Team
 */
@Slf4j
public class DriverContainer {

    /** 被包装的仪器驱动实例 */
    @Getter
    private final InstrumentDriver driver;

    /** 驱动配置 */
    private final DriverConfig config;

    /** 驱动上下文，提供基础设施能力 */
    private final DriverContext context;

    /** 运行状态标志，通过 CAS 保证启停的原子性 */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /** 当前仪器状态，使用 volatile 保证多线程可见性 */
    @Getter
    private volatile InstrumentStatus currentStatus;

    /**
     * 构造驱动容器。
     *
     * @param driver 仪器驱动实例
     * @param config 驱动配置
     * @param context 驱动上下文
     */
    public DriverContainer(InstrumentDriver driver, DriverConfig config, DriverContext context) {
        this.driver = driver;
        this.config = config;
        this.context = context;
        this.currentStatus = new InstrumentStatus();
        this.currentStatus.setInstrumentId(config.getInstrumentId());
        this.currentStatus.setStatus(InstrumentStatus.Status.OFFLINE);
    }

    /**
     * 启动驱动。
     * <p>使用 CAS 操作确保不会重复启动。启动成功后将状态更新为 ONLINE。</p>
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            driver.start(context);
            currentStatus.setStatus(InstrumentStatus.Status.ONLINE);
            log.info("Driver {} started for instrument {}", driver.getDriverId(), config.getInstrumentId());
        }
    }

    /**
     * 停止驱动。
     * <p>使用 CAS 操作确保不会重复停止。停止成功后将状态更新为 OFFLINE。</p>
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            driver.stop();
            currentStatus.setStatus(InstrumentStatus.Status.OFFLINE);
            log.info("Driver {} stopped for instrument {}", driver.getDriverId(), config.getInstrumentId());
        }
    }
}
