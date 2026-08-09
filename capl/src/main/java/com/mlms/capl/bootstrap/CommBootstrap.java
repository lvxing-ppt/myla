package com.mlms.capl.bootstrap;

import com.mlms.capl.config.LisCommProperties;
import com.mlms.capl.config.LisCommProperties.InstrumentProps;
import com.mlms.capl.config.LisCommProperties.ChannelProps;
import com.mlms.oes.gateway.core.context.DriverConfig;
import com.mlms.oes.gateway.core.hub.InstrumentHub;
import com.mlms.oes.gateway.core.spi.InstrumentDriver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.cloud.context.scope.refresh.RefreshScopeRefreshedEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 通讯层启动引导器。
 * <p>
 * 启动 LIS 入站监听 + 仪器驱动（可选）。
 * 支持 Nacos 配置变更时动态重载仪器列表，无需重启整个应用。
 * </p>
 */
@Slf4j
@Component
public class CommBootstrap implements CommandLineRunner {

    @Autowired(required = false)
    private InstrumentHub hub;
    private final LisCommProperties properties;
    private final RabbitTemplate rabbitTemplate;
    private final LisInboundServerStarter inboundStarter;
    private final ApplicationContext appCtx;

    private static final Map<String, InstrumentDriver> DRIVER_CACHE = new ConcurrentHashMap<>();

    /** 当前已启动的仪器 ID 集合，用于 refresh 时停止旧仪器 */
    private final Set<String> activeInstruments = new CopyOnWriteArraySet<>();

    public CommBootstrap(LisCommProperties properties,
                         RabbitTemplate rabbitTemplate,
                         LisInboundServerStarter inboundStarter,
                         ApplicationContext appCtx) {
        this.properties = properties;
        this.rabbitTemplate = rabbitTemplate;
        this.inboundStarter = inboundStarter;
        this.appCtx = appCtx;
    }

    // ==================== 启动入口 ====================

    @Override
    public void run(String... args) {
        // 1. 启动 LIS 入站监听（核心功能）
        inboundStarter.startAll(properties.getLisInbound());

        // 2. 启动仪器驱动
        bootInstruments(properties.getInstruments());

        log.info("CommBootstrap complete: {} LIS inbound(s), {} instrument(s)",
                properties.getLisInbound().size(), properties.getInstruments().size());
    }

    // ==================== Nacos 动态刷新 ====================

    /**
     * 监听 Nacos 配置刷新事件，动态重载仪器列表。
     * <p>
     * 当运维人员在 Nacos 控制台修改仪器配置并发布后，
     * Spring Cloud Nacos 触发 {@link RefreshScopeRefreshedEvent}，
     * 此方法自动停止旧仪器、启动新配置的仪器。
     * </p>
     */
    @EventListener(RefreshScopeRefreshedEvent.class)
    public void onConfigRefresh() {
        if (hub == null) {
            log.info("InstrumentHub not available, skip instrument refresh");
            return;
        }

        // 获取刷新后的新配置（@RefreshScope 已重建 LisCommProperties bean）
        LisCommProperties freshProps = appCtx.getBean(LisCommProperties.class);

        log.info("Config refreshed — reloading instruments: old={}, new={}",
                activeInstruments.size(), freshProps.getInstruments().size());

        // 停止所有旧仪器
        for (String instrumentId : activeInstruments) {
            try {
                hub.stopDriver(instrumentId);
                hub.unloadDriver(instrumentId);
                log.info("Instrument stopped (refresh): {}", instrumentId);
            } catch (Exception e) {
                log.error("Failed to stop instrument {}: {}", instrumentId, e.getMessage());
            }
        }
        activeInstruments.clear();

        // 用新配置启动仪器（插件 JAR 缓存不清空，支持新增插件）
        bootInstruments(freshProps.getInstruments());
    }

    // ==================== 仪器生命周期 ====================

    private void bootInstruments(java.util.List<InstrumentProps> instrumentList) {
        if (hub == null || instrumentList.isEmpty()) {
            if (!instrumentList.isEmpty()) {
                log.warn("{} instrument(s) configured but InstrumentHub not available (no datasource?)",
                        instrumentList.size());
            }
            return;
        }

        log.info("Loading {} instrument(s) from config", instrumentList.size());
        for (InstrumentProps cfg : instrumentList) {
            try {
                bootSingle(cfg);
                activeInstruments.add(cfg.getInstrumentId());
            } catch (Exception e) {
                log.error("Instrument {} failed: {}", cfg.getInstrumentId(), e.getMessage(), e);
            }
        }
    }

    private void bootSingle(InstrumentProps cfg) {
        String instrumentId = cfg.getInstrumentId();
        log.info("--- Loading instrument: {} (driver={}) ---", instrumentId, cfg.getDriverId());

        InstrumentDriver driver = createDriver(cfg.getDriverId());
        DriverConfig config = toDriverConfig(cfg);
        InstrumentResultPublisher publisher = new InstrumentResultPublisher(rabbitTemplate);
        driver.registerListener(publisher);
        hub.loadDriver(driver, config, null);
        hub.startDriver(instrumentId);

        log.info("--- Instrument {} started on port {} ---", instrumentId, cfg.getChannel().getPort());
    }

    // ==================== Driver 发现 ====================

    @SuppressWarnings("unchecked")
    private InstrumentDriver createDriver(String driverId) {
        if (DRIVER_CACHE.isEmpty()) {
            synchronized (DRIVER_CACHE) {
                if (DRIVER_CACHE.isEmpty()) {
                    // 1. 从 driver-dir 加载插件 JAR（优先，允许覆盖内置驱动）
                    File pluginDir = new File(properties.getDriverDir());
                    DRIVER_CACHE.putAll(DriverPluginLoader.loadFrom(pluginDir));

                    // 2. 从 classpath SPI 加载内置驱动（插件已有的不覆盖）
                    for (var d : java.util.ServiceLoader.load(InstrumentDriver.class)) {
                        DRIVER_CACHE.putIfAbsent(d.getDriverId(), d);
                        log.info("SPI discovered driver: {} ({})", d.getDriverId(), d.getDisplayName());
                    }
                }
            }
        }
        InstrumentDriver driver = DRIVER_CACHE.get(driverId);
        if (driver == null) throw new IllegalArgumentException("Driver not found: " + driverId);
        try { return driver.getClass().getDeclaredConstructor().newInstance(); }
        catch (Exception e) { throw new RuntimeException("Cannot create driver: " + driverId, e); }
    }

    private DriverConfig toDriverConfig(InstrumentProps cfg) {
        DriverConfig config = new DriverConfig();
        config.setDriverId(cfg.getDriverId());
        config.setInstrumentId(cfg.getInstrumentId());
        config.setSplitterType(cfg.getSplitterType());
        config.setParserType(cfg.getParserType());
        config.setProperties(cfg.getProperties());
        ChannelProps ch = cfg.getChannel();
        DriverConfig.ChannelConfig cc = new DriverConfig.ChannelConfig();
        cc.setType(ch.getType()); cc.setHost(ch.getHost()); cc.setPort(ch.getPort());
        cc.setDirectory(ch.getDirectory()); cc.setFilePattern(ch.getFilePattern());
        cc.setPollIntervalMs(ch.getPollIntervalMs()); cc.setSerialPort(ch.getSerialPort());
        cc.setBaudRate(ch.getBaudRate()); cc.setReconnectDelayMs(ch.getReconnectDelayMs());
        config.setChannel(cc);
        return config;
    }
}
