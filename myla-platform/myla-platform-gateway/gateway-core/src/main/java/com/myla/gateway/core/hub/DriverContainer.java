package com.myla.gateway.core.hub;

import com.myla.gateway.core.spi.InstrumentDriver;
import com.myla.gateway.core.context.DriverConfig;
import com.myla.gateway.core.context.DriverContext;
import com.myla.gateway.core.model.InstrumentStatus;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class DriverContainer {
    @Getter
    private final InstrumentDriver driver;
    private final DriverConfig config;
    private final DriverContext context;
    private final AtomicBoolean running = new AtomicBoolean(false);
    @Getter
    private volatile InstrumentStatus currentStatus;

    public DriverContainer(InstrumentDriver driver, DriverConfig config, DriverContext context) {
        this.driver = driver;
        this.config = config;
        this.context = context;
        this.currentStatus = new InstrumentStatus();
        this.currentStatus.setInstrumentId(config.getInstrumentId());
        this.currentStatus.setStatus(InstrumentStatus.Status.OFFLINE);
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            driver.start(context);
            currentStatus.setStatus(InstrumentStatus.Status.ONLINE);
            log.info("Driver {} started for instrument {}", driver.getDriverId(), config.getInstrumentId());
        }
    }

    public void stop() {
        if (running.compareAndSet(true, false)) {
            driver.stop();
            currentStatus.setStatus(InstrumentStatus.Status.OFFLINE);
            log.info("Driver {} stopped for instrument {}", driver.getDriverId(), config.getInstrumentId());
        }
    }
}
