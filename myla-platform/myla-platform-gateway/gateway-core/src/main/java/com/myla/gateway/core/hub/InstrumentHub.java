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

@Slf4j
@Component
public class InstrumentHub {

    private final Map<String, DriverContainer> containers = new ConcurrentHashMap<>();
    private final Map<String, InstrumentDriver> drivers = new ConcurrentHashMap<>();

    public void loadDriver(InstrumentDriver driver, DriverConfig config, DriverContext context) {
        String instrumentId = config.getInstrumentId();
        DriverContainer container = new DriverContainer(driver, config, context);
        containers.put(instrumentId, container);
        drivers.put(instrumentId, driver);
        driver.initialize(config);
        log.info("Driver loaded: driverId={}, instrumentId={}", driver.getDriverId(), instrumentId);
    }

    public void startDriver(String instrumentId) {
        DriverContainer container = containers.get(instrumentId);
        if (container != null) { container.start(); log.info("Driver started: {}", instrumentId); }
    }

    public void stopDriver(String instrumentId) {
        DriverContainer container = containers.get(instrumentId);
        if (container != null) { container.stop(); log.info("Driver stopped: {}", instrumentId); }
    }

    public void unloadDriver(String instrumentId) {
        stopDriver(instrumentId);
        containers.remove(instrumentId);
        drivers.remove(instrumentId);
        log.info("Driver unloaded: {}", instrumentId);
    }

    public CompletableFuture<CommandResult> sendCommand(String instrumentId, InstrumentCommand command) {
        InstrumentDriver driver = drivers.get(instrumentId);
        if (driver == null) throw new IllegalArgumentException("Driver not found: " + instrumentId);
        return driver.executeCommand(command);
    }

    public DiscoveryInfo getDiscoveryInfo(String instrumentId) {
        InstrumentDriver driver = drivers.get(instrumentId);
        if (driver == null) throw new IllegalArgumentException("Driver not found: " + instrumentId);
        return driver.getDiscoveryInfo();
    }

    public List<String> listInstruments() { return List.copyOf(containers.keySet()); }

    public Map<String, InstrumentStatus> getAllStatuses() {
        Map<String, InstrumentStatus> statuses = new ConcurrentHashMap<>();
        containers.forEach((id, c) -> statuses.put(id, c.getCurrentStatus()));
        return statuses;
    }
}
