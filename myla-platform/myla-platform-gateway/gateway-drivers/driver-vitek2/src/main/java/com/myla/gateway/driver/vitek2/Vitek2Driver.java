package com.myla.gateway.driver.vitek2;

import com.myla.common.api.enums.CommunicationMode;
import com.myla.gateway.channel.TcpChannel;
import com.myla.gateway.core.context.DriverConfig;
import com.myla.gateway.core.context.DriverContext;
import com.myla.gateway.core.model.*;
import com.myla.gateway.core.spi.*;
import com.myla.gateway.splitter.AstmSplitter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class Vitek2Driver implements InstrumentDriver {

    private final TcpChannel channel = new TcpChannel();
    private final AstmSplitter splitter = new AstmSplitter();
    private final Vitek2Parser parser = new Vitek2Parser();
    private DriverConfig config;
    private DriverContext ctx;
    private DataEventListener listener;

    @Override
    public String getDriverId() {
        return "vitek2-v1.0";
    }

    @Override
    public String getDisplayName() {
        return "VITEK 2 Driver";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public CommunicationMode getMode() {
        return CommunicationMode.PASSIVE_LISTEN;
    }

    @Override
    public void initialize(DriverConfig config) {
        this.config = config;
        log.info("Vitek2Driver initialized for instrument {}", config.getInstrumentId());
    }

    @Override
    public void start(DriverContext ctx) {
        this.ctx = ctx;
        List<byte[]> incompleteFrames = new ArrayList<>();

        channel.setMessageListener(rawBytes -> {
            try {
                // Save raw message
                ctx.saveRawMessage(config.getInstrumentId(), "ASTM", rawBytes);

                // Split into frames and parse
                List<byte[]> frames = splitter.splitFrames(rawBytes, incompleteFrames);
                for (byte[] frame : frames) {
                    try {
                        var results = parser.parse(frame);
                        for (var result : results) {
                            result.setInstrumentId(config.getInstrumentId());
                            ctx.publishResult(rawBytes);
                            if (listener != null) {
                                listener.onResultReceived(result);
                            }
                        }
                    } catch (Exception e) {
                        log.error("Parse failed for instrument {}: {}", config.getInstrumentId(), e.getMessage());
                        if (listener != null) {
                            listener.onParseFailed(new String(frame), e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Message processing error for instrument {}: {}", config.getInstrumentId(), e.getMessage());
                if (listener != null) {
                    listener.onConnectionError(config.getInstrumentId(), e.getMessage(), 1);
                }
            }
        });

        channel.setErrorListener(error -> {
            log.error("Channel error for instrument {}: {}", config.getInstrumentId(), error.getMessage());
            if (listener != null) {
                listener.onConnectionError(config.getInstrumentId(), error.getMessage(), 1);
            }
            ctx.reportHealth(config.getInstrumentId(), "ERROR", error.getMessage());
        });

        channel.open(config.getChannel());
        ctx.reportHealth(config.getInstrumentId(), "ONLINE", "Vitek2Driver started");
        log.info("Vitek2Driver started for instrument {}", config.getInstrumentId());
    }

    @Override
    public void stop() {
        channel.close();
        if (ctx != null) {
            ctx.reportHealth(config.getInstrumentId(), "OFFLINE", "Vitek2Driver stopped");
        }
        log.info("Vitek2Driver stopped for instrument {}", config.getInstrumentId());
    }

    @Override
    public boolean testConnection() {
        return channel.isOpen();
    }

    @Override
    public void registerListener(DataEventListener listener) {
        this.listener = listener;
    }

    @Override
    public DiscoveryInfo getDiscoveryInfo() {
        DiscoveryInfo info = new DiscoveryInfo();
        info.setManufacturer("bioMerieux");
        info.setModel("VITEK 2");
        info.setSerialNumber("N/A");
        info.setFirmwareVersion("N/A");
        info.setHardwareRevision("N/A");
        info.setSupportedCommands(List.of());
        return info;
    }
}
