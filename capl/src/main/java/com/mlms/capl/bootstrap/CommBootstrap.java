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
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通讯层启动引导器。
 * 启动 LIS 入站监听 + 仪器驱动（可选）。
 */
@Slf4j
@Component
public class CommBootstrap implements CommandLineRunner {

    @Autowired(required = false)
    private InstrumentHub hub;
    private final LisCommProperties properties;
    private final RabbitTemplate rabbitTemplate;
    private final LisInboundServerStarter inboundStarter;

    private static final Map<String, InstrumentDriver> DRIVER_CACHE = new ConcurrentHashMap<>();

    public CommBootstrap(LisCommProperties properties,
                          RabbitTemplate rabbitTemplate,
                          LisInboundServerStarter inboundStarter) {
        this.properties = properties;
        this.rabbitTemplate = rabbitTemplate;
        this.inboundStarter = inboundStarter;
    }

    @Override
    public void run(String... args) {
        // 1. 启动 LIS 入站监听（核心功能）
        inboundStarter.startAll(properties.getLisInbound());

        // 2. 启动仪器驱动（仅当 InstrumentHub 可用且有配置时）
        if (hub != null && !properties.getInstruments().isEmpty()) {
            log.info("Loading {} instrument(s) from YAML config", properties.getInstruments().size());
            for (InstrumentProps cfg : properties.getInstruments()) {
                try {
                    bootInstrument(cfg);
                } catch (Exception e) {
                    log.error("Instrument {} failed: {}", cfg.getInstrumentId(), e.getMessage(), e);
                }
            }
        } else if (!properties.getInstruments().isEmpty()) {
            log.warn("{} instrument(s) configured but InstrumentHub not available (no datasource?)",
                    properties.getInstruments().size());
        }

        log.info("CommBootstrap complete: {} LIS inbound(s), {} instrument(s)",
                properties.getLisInbound().size(), properties.getInstruments().size());
    }

    private void bootInstrument(InstrumentProps cfg) {
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

    @SuppressWarnings("unchecked")
    private InstrumentDriver createDriver(String driverId) {
        if (DRIVER_CACHE.isEmpty()) {
            synchronized (DRIVER_CACHE) {
                if (DRIVER_CACHE.isEmpty()) {
                    for (var d : java.util.ServiceLoader.load(InstrumentDriver.class)) {
                        DRIVER_CACHE.put(d.getDriverId(), d);
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
