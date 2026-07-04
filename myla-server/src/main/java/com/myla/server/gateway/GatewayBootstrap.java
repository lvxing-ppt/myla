package com.myla.server.gateway;

import com.myla.gateway.core.context.DriverConfig;
import com.myla.gateway.core.hub.InstrumentHub;
import com.myla.gateway.core.spi.InstrumentDriver;
import com.myla.gateway.devicemgmt.service.InstrumentMgmtService;
import com.myla.server.config.MylaProperties;
import com.myla.server.config.MylaProperties.ChannelProperties;
import com.myla.server.config.MylaProperties.InstrumentProperties;
import com.myla.server.mapper.RawMessageMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myla.gateway.devicemgmt.entity.InstrumentRegistry;
import com.myla.gateway.devicemgmt.event.InstrumentRegisteredEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 网关启动引导器。
 * <p>
 * 在 Spring Boot 启动后自动执行，负责：
 * <ol>
 *   <li>读取 {@link MylaProperties} 中的仪器配置列表</li>
 *   <li>根据 driverId 实例化对应的 InstrumentDriver</li>
 *   <li>创建 DefaultDriverContext（对接 RabbitMQ、文件存储等基础设施）</li>
 *   <li>创建 LoggingDataEventListener（接收解析结果）</li>
 *   <li>注册到 InstrumentHub 并启动驱动</li>
 * </ol>
 * </p>
 *
 * <h3>驱动注册表：</h3>
 * <p>当前通过 factory Map 硬编码驱动类型。后续可改为 Java SPI 自动发现。</p>
 *
 * @author MyLA Team
 */
@Slf4j
@Configuration
public class GatewayBootstrap implements CommandLineRunner {

    @Autowired
    private InstrumentHub hub;

    @Autowired
    private MylaProperties properties;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RawMessageMapper rawMessageMapper;

    @Autowired
    private ResultPersistenceService resultPersistenceService;

    @Autowired
    private InstrumentMgmtService instrumentMgmtService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    /** 所有已创建的 DataEventListener，key = instrumentId，供测试查询 */
    private final Map<String, LoggingDataEventListener> listeners = new ConcurrentHashMap<>();

    /** 所有已创建的 DriverContext，key = instrumentId，供关闭资源 */
    private final Map<String, DefaultDriverContext> contexts = new ConcurrentHashMap<>();

    /**
     * Spring Boot 启动后自动执行。
     * <p>遍历仪器配置列表，逐个加载并启动驱动。</p>
     */
    @Override
    public void run(String... args) {
        int yamlCount = properties.getGateway().getInstruments().size();

        // ====== 1. 加载 YAML 配置的仪器 ======
        if (!properties.getGateway().getInstruments().isEmpty()) {
            log.info("Loading {} instrument(s) from YAML config", yamlCount);
            for (InstrumentProperties cfg : properties.getGateway().getInstruments()) {
                try {
                    bootInstrument(cfg);
                } catch (Exception e) {
                    log.error("YAML instrument {} failed: {}", cfg.getInstrumentId(), e.getMessage());
                }
            }
        }

        // ====== 2. 加载 DB 中动态注册的仪器 ======
        var dbInstruments = instrumentMgmtService.listAll();
        int dbLoaded = 0;
        for (var reg : dbInstruments) {
            if (reg.getChannelConfig() == null || reg.getChannelConfig().isBlank()) continue;
            // 跳过 YAML 已加载的
            if (contexts.containsKey(reg.getInstrumentId())) continue;

            try {
                bootInstrumentFromDb(reg);
                dbLoaded++;
            } catch (Exception e) {
                log.error("DB instrument {} failed: {}", reg.getInstrumentId(), e.getMessage());
            }
        }

        log.info("Gateway boot complete: {} YAML + {} DB = {} instrument(s)",
                contexts.size() - dbLoaded, dbLoaded, contexts.size());
    }

    /**
     * 启动单台仪器的完整链路。
     */
    private void bootInstrument(InstrumentProperties cfg) {
        String instrumentId = cfg.getInstrumentId();
        log.info("--- 正在加载仪器: {} (driver={}) ---", instrumentId, cfg.getDriverId());

        // 1. 根据 driverId 创建驱动实例（SPI 自动发现）
        InstrumentDriver driver = createDriver(cfg.getDriverId());

        // 2. 注册仪器到 instrument_registry 表
        String manufacturer = "Unknown";
        String model = "Unknown";
        try {
            var info = driver.getDiscoveryInfo();
            manufacturer = info.getManufacturer() != null ? info.getManufacturer() : "Unknown";
            model = info.getModel() != null ? info.getModel() : "Unknown";
        } catch (Exception ignored) {}
        instrumentMgmtService.register(instrumentId, cfg.getDriverId(), manufacturer, model);

        // 3. 将 YAML 配置转换为核心层的 DriverConfig
        DriverConfig config = toDriverConfig(cfg);

        // 4. 创建驱动上下文
        DefaultDriverContext context = new DefaultDriverContext(
                cfg.getDriverId(), instrumentId, rabbitTemplate, rawMessageMapper,
                instrumentMgmtService, redisTemplate);
        contexts.put(instrumentId, context);

        // 4. 创建数据事件监听器
        LoggingDataEventListener listener = new LoggingDataEventListener(
                resultPersistenceService);
        listeners.put(instrumentId, listener);
        driver.registerListener(listener);

        // 5. 加载到 Hub 并启动
        hub.loadDriver(driver, config, context);
        hub.startDriver(instrumentId);

        log.info("--- 仪器 {} 已启动，监听端口 {} ---", instrumentId, cfg.getChannel().getPort());
    }

    /** 从 DB 记录启动仪器（动态注册的仪器，重启后自动恢复） */
    @SuppressWarnings("unchecked")
    private void bootInstrumentFromDb(InstrumentRegistry reg) throws Exception {
        String instrumentId = reg.getInstrumentId();
        log.info("--- Loading DB instrument: {} (driver={}) ---", instrumentId, reg.getDriverId());

        // 解析 channel_config JSON
        var cfgMap = new ObjectMapper().readValue(reg.getChannelConfig(), java.util.Map.class);
        int port = ((Number) cfgMap.getOrDefault("port", 0)).intValue();

        // 构建 ChannelProperties
        ChannelProperties ch = new ChannelProperties();
        ch.setType((String) cfgMap.getOrDefault("type", "TCP"));
        ch.setPort(port);

        // 构建 InstrumentProperties
        InstrumentProperties props = new InstrumentProperties();
        props.setDriverId(reg.getDriverId());
        props.setInstrumentId(instrumentId);
        props.setChannel(ch);
        props.setSplitterType((String) cfgMap.getOrDefault("splitterType", ""));
        props.setParserType((String) cfgMap.getOrDefault("parserType", ""));

        bootInstrument(props);
    }

    /** 监听仪器注册事件 — API 注册后热加载，无需重启 */
    @EventListener
    public void onInstrumentRegistered(InstrumentRegisteredEvent event) {
        String instrumentId = event.getInstrumentId();
        InstrumentRegistry reg = instrumentMgmtService.getByInstrumentId(instrumentId);
        if (reg == null || reg.getChannelConfig() == null) {
            log.warn("Cannot hot-load {}: not found or no channel_config", instrumentId);
            return;
        }
        try {
            bootInstrumentFromDb(reg);
            log.info("Instrument {} hot-loaded successfully", instrumentId);
        } catch (Exception e) {
            log.error("Failed to hot-load instrument {}: {}", instrumentId, e.getMessage());
        }
    }

    /**
     * 驱动工厂：通过 Java SPI 自动发现所有 InstrumentDriver 实现。
     * <p>新增仪器驱动只需：1. 实现 InstrumentDriver 2. 在 META-INF/services 注册 3. YAML 配置。
     * 无需修改此处代码。</p>
     */
    private InstrumentDriver createDriver(String driverId) {
        var drivers = java.util.ServiceLoader.load(
            com.myla.gateway.core.spi.InstrumentDriver.class);
        for (var d : drivers) {
            if (d.getDriverId().equals(driverId)) {
                return d;
            }
        }
        throw new IllegalArgumentException(
                "未找到驱动: " + driverId + "。请确认 Driver 实现类在 classpath 中并已在 META-INF/services 注册。");
    }

    /**
     * 将 YAML 配置属性转换为核心层的 DriverConfig。
     */
    private DriverConfig toDriverConfig(InstrumentProperties cfg) {
        DriverConfig config = new DriverConfig();
        config.setDriverId(cfg.getDriverId());
        config.setInstrumentId(cfg.getInstrumentId());
        config.setSplitterType(cfg.getSplitterType());
        config.setParserType(cfg.getParserType());
        config.setProperties(cfg.getProperties());

        // 通道配置映射
        ChannelProperties ch = cfg.getChannel();
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

    // ==================== 供外部查询的 getter ====================

    /**
     * 获取指定仪器的 DataEventListener（供测试断言）。
     */
    public LoggingDataEventListener getListener(String instrumentId) {
        return listeners.get(instrumentId);
    }

    /**
     * 获取指定仪器的 DriverContext（供测试查询）。
     */
    public DefaultDriverContext getContext(String instrumentId) {
        return contexts.get(instrumentId);
    }
}
