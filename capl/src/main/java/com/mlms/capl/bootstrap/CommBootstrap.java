package com.mlms.capl.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mlms.oes.gateway.core.context.DriverConfig;
import com.mlms.oes.gateway.core.hub.InstrumentHub;
import com.mlms.oes.gateway.core.spi.InstrumentDriver;
import com.mlms.oes.gateway.core.spi.DataEventListener;
import com.mlms.capl.config.LisCommProperties;
import com.mlms.capl.config.LisCommProperties.InstrumentProps;
import com.mlms.capl.config.LisCommProperties.ChannelProps;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通讯层启动引导器。
 * <p>
 * 启动时加载 YAML 配置的仪器驱动 + LIS 入站监听。
 * 替代原来 oes-server 中的 GatewayBootstrap + LisInboundServer 的配置读取部分。
 * </p>
 */
@Slf4j
@Component
public class CommBootstrap implements CommandLineRunner {

    private final InstrumentHub hub;
    private final LisCommProperties properties;
    private final RabbitTemplate rabbitTemplate;
    private final LisInboundServerStarter inboundStarter;

    /** SPI 驱动缓存 */
    private static final Map<String, InstrumentDriver> DRIVER_CACHE = new ConcurrentHashMap<>();
    private static final ObjectMapper jsonMapper = new ObjectMapper();

    public CommBootstrap(InstrumentHub hub, LisCommProperties properties,
                          RabbitTemplate rabbitTemplate,
                          LisInboundServerStarter inboundStarter) {
        this.hub = hub;
        this.properties = properties;
        this.rabbitTemplate = rabbitTemplate;
        this.inboundStarter = inboundStarter;
    }

    @Override
    public void run(String... args) {
        // 1. 启动仪器驱动
        if (!properties.getInstruments().isEmpty()) {
            log.info("Loading {} instrument(s) from YAML config", properties.getInstruments().size());
            for (InstrumentProps cfg : properties.getInstruments()) {
                try {
                    bootInstrument(cfg);
                } catch (Exception e) {
                    log.error("Instrument {} failed: {}", cfg.getInstrumentId(), e.getMessage(), e);
                }
            }
        }

        // 2. 启动 LIS 入站监听
        inboundStarter.startAll(properties.getLisInbound());

        log.info("CommBootstrap complete: {} instrument(s), {} LIS inbound(s)",
                properties.getInstruments().size(), properties.getLisInbound().size());
    }

    private void bootInstrument(InstrumentProps cfg) {
        String instrumentId = cfg.getInstrumentId();
        log.info("--- Loading instrument: {} (driver={}) ---", instrumentId, cfg.getDriverId());

        // 1. SPI 发现并创建驱动实例
        InstrumentDriver driver = createDriver(cfg.getDriverId());

        // 2. 构建 DriverConfig
        DriverConfig config = toDriverConfig(cfg);

        // 3. 创建 MQ 发布器 (替代 ResultPersistenceService)
        DataEventListener publisher = new InstrumentResultPublisher(rabbitTemplate);
        driver.registerListener(publisher);

        // 4. 加载到 Hub 并启动（注意：不创建 DefaultDriverContext，因为没有 DB/Redis）
        hub.loadDriver(driver, config, null);
        hub.startDriver(instrumentId);

        log.info("--- Instrument {} started, listening on port {} ---", instrumentId, cfg.getChannel().getPort());
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
        if (driver == null) {
            throw new IllegalArgumentException(
                    "Driver not found: " + driverId + ". Ensure it's registered in META-INF/services.");
        }
        try {
            return driver.getClass().getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Cannot create driver instance: " + driverId, e);
        }
    }

    private DriverConfig toDriverConfig(InstrumentProps cfg) {
        DriverConfig config = new DriverConfig();
        config.setDriverId(cfg.getDriverId());
        config.setInstrumentId(cfg.getInstrumentId());
        config.setSplitterType(cfg.getSplitterType());
        config.setParserType(cfg.getParserType());
        config.setProperties(cfg.getProperties());

        ChannelProps ch = cfg.getChannel();
        DriverConfig.ChannelConfig channelConfig = new DriverConfig.ChannelConfig();
        channelConfig.setType(ch.getType());
        channelConfig.setHost(ch.getHost());
        channelConfig.setPort(ch.getPort());
        channelConfig.setDirectory(ch.getDirectory());
        channelConfig.setFilePattern(ch.getFilePattern());
        channelConfig.setPollIntervalMs(ch.getPollIntervalMs());
        channelConfig.setSerialPort(ch.getSerialPort());
        channelConfig.setBaudRate(ch.getBaudRate());
        channelConfig.setReconnectDelayMs(ch.getReconnectDelayMs());
        config.setChannel(channelConfig);

        return config;
    }
}
