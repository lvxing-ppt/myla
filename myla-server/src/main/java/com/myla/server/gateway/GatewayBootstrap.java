package com.myla.server.gateway;

import com.myla.gateway.core.context.DriverConfig;
import com.myla.gateway.core.hub.InstrumentHub;
import com.myla.gateway.core.spi.InstrumentDriver;
import com.myla.server.config.MylaProperties;
import com.myla.server.config.MylaProperties.ChannelProperties;
import com.myla.server.config.MylaProperties.InstrumentProperties;
import com.myla.result.mapper.AstResultMapper;
import com.myla.result.mapper.OrganismResultMapper;
import com.myla.server.mapper.RawMessageMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
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
    private OrganismResultMapper organismResultMapper;

    @Autowired
    private AstResultMapper astResultMapper;

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
        if (properties.getGateway().getInstruments().isEmpty()) {
            log.warn(">>> 没有配置任何仪器接入，网关处于空转状态。请在 application.yml 中 myla.gateway.instruments 下添加仪器配置。 <<<");
            return;
        }

        log.info("══════════════════════════════════════════════");
        log.info("  网关启动中，共配置 {} 台仪器", properties.getGateway().getInstruments().size());
        log.info("══════════════════════════════════════════════");

        for (InstrumentProperties cfg : properties.getGateway().getInstruments()) {
            try {
                bootInstrument(cfg);
            } catch (Exception e) {
                log.error("仪器 {} 启动失败: {}", cfg.getInstrumentId(), e.getMessage(), e);
            }
        }

        log.info("══════════════════════════════════════════════");
        log.info("  网关启动完成。已启动 {}/{} 台仪器",
                contexts.size(), properties.getGateway().getInstruments().size());
        log.info("══════════════════════════════════════════════");
    }

    /**
     * 启动单台仪器的完整链路。
     */
    private void bootInstrument(InstrumentProperties cfg) {
        String instrumentId = cfg.getInstrumentId();
        log.info("--- 正在加载仪器: {} (driver={}) ---", instrumentId, cfg.getDriverId());

        // 1. 根据 driverId 创建驱动实例
        InstrumentDriver driver = createDriver(cfg.getDriverId());

        // 2. 将 YAML 配置转换为核心层的 DriverConfig
        DriverConfig config = toDriverConfig(cfg);

        // 3. 创建驱动上下文
        DefaultDriverContext context = new DefaultDriverContext(
                cfg.getDriverId(), instrumentId, rabbitTemplate, rawMessageMapper);
        contexts.put(instrumentId, context);

        // 4. 创建数据事件监听器
        LoggingDataEventListener listener = new LoggingDataEventListener(
                organismResultMapper, astResultMapper, rabbitTemplate);
        listeners.put(instrumentId, listener);
        driver.registerListener(listener);

        // 5. 加载到 Hub 并启动
        hub.loadDriver(driver, config, context);
        hub.startDriver(instrumentId);

        log.info("--- 仪器 {} 已启动，监听端口 {} ---", instrumentId, cfg.getChannel().getPort());
    }

    /**
     * 驱动工厂：根据 driverId 创建对应的 InstrumentDriver 实例。
     * <p>后续可改为 Java SPI {@code ServiceLoader<InstrumentDriver>} 自动发现。</p>
     */
    private InstrumentDriver createDriver(String driverId) {
        return switch (driverId) {
            case "vitek2-v1.0" -> new com.myla.gateway.driver.vitek2.Vitek2Driver();
            case "proprietary-v1.0" -> new com.myla.gateway.driver.proprietary.ProprietaryProtocolDriver();
            default -> throw new IllegalArgumentException(
                    "不支持的驱动类型: " + driverId + "。已支持的驱动: vitek2-v1.0, proprietary-v1.0");
        };
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
