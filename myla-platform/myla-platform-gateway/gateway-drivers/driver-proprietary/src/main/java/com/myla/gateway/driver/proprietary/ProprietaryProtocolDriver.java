package com.myla.gateway.driver.proprietary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myla.common.api.dto.UnifiedResult;
import com.myla.common.api.enums.CommunicationMode;
import com.myla.gateway.core.context.DriverConfig;
import com.myla.gateway.core.context.DriverContext;
import com.myla.gateway.core.model.*;
import com.myla.gateway.core.spi.*;
import com.myla.gateway.channel.TcpChannel;
import com.myla.gateway.protocol.FrameType;
import com.myla.gateway.protocol.ProprietaryFrameCodec;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class ProprietaryProtocolDriver implements InstrumentDriver {

    private final CommunicationChannel channel;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private DriverConfig config;
    private DriverContext ctx;
    private DataEventListener dataListener;
    private TelemetryListener telemetryListener;

    public ProprietaryProtocolDriver() {
        this(new TcpChannel());
    }

    public ProprietaryProtocolDriver(CommunicationChannel channel) {
        this.channel = channel;
    }

    @Override
    public String getDriverId() {
        return "proprietary-v1.0";
    }

    @Override
    public String getDisplayName() {
        return "Proprietary Protocol Driver";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public CommunicationMode getMode() {
        return CommunicationMode.ACTIVE_CONNECT;
    }

    @Override
    public void initialize(DriverConfig config) {
        this.config = config;
        log.info("ProprietaryProtocolDriver initialized for instrument {}", config.getInstrumentId());
    }

    @Override
    public void start(DriverContext ctx) {
        this.ctx = ctx;

        channel.setMessageListener(rawBytes -> {
            try {
                ctx.saveRawMessage(config.getInstrumentId(), "PROPRIETARY", rawBytes);

                ProprietaryFrameCodec.DecodedFrame decoded = ProprietaryFrameCodec.decode(rawBytes);
                FrameType type = decoded.type();

                switch (type) {
                    case RESULT_PUSH -> {
                        UnifiedResult result = objectMapper.readValue(decoded.payload(), UnifiedResult.class);
                        result.setInstrumentId(config.getInstrumentId());
                        ctx.publishResult(rawBytes);
                        if (dataListener != null) {
                            dataListener.onResultReceived(result);
                        }
                    }
                    case TELEMETRY -> {
                        TelemetryData telemetry = objectMapper.readValue(decoded.payload(), TelemetryData.class);
                        if (telemetryListener != null) {
                            telemetryListener.onTelemetry(config.getInstrumentId(), telemetry);
                        }
                    }
                    case HEARTBEAT -> {
                        log.debug("Heartbeat received from instrument {}", config.getInstrumentId());
                    }
                    case DISCOVERY -> {
                        log.info("Discovery request from instrument {}", config.getInstrumentId());
                    }
                    case ERROR -> {
                        String errorMsg = decoded.payload() != null ? new String(decoded.payload()) : "Unknown error";
                        log.error("Error frame from instrument {}: {}", config.getInstrumentId(), errorMsg);
                        if (dataListener != null) {
                            dataListener.onConnectionError(config.getInstrumentId(), errorMsg, 1);
                        }
                    }
                    default -> log.debug("Unhandled frame type {} from instrument {}", type, config.getInstrumentId());
                }
            } catch (Exception e) {
                log.error("Failed to process frame from instrument {}: {}", config.getInstrumentId(), e.getMessage());
                if (dataListener != null) {
                    dataListener.onParseFailed(new String(rawBytes), e.getMessage());
                }
            }
        });

        channel.setErrorListener(error -> {
            log.error("Channel error for instrument {}: {}", config.getInstrumentId(), error.getMessage());
            ctx.reportHealth(config.getInstrumentId(), "ERROR", error.getMessage());
            if (dataListener != null) {
                dataListener.onConnectionError(config.getInstrumentId(), error.getMessage(), 1);
            }
        });

        channel.open(config.getChannel());
        ctx.reportHealth(config.getInstrumentId(), "ONLINE", "ProprietaryProtocolDriver started");
        log.info("ProprietaryProtocolDriver started for instrument {}", config.getInstrumentId());
    }

    @Override
    public void stop() {
        channel.close();
        if (ctx != null) {
            ctx.reportHealth(config.getInstrumentId(), "OFFLINE", "ProprietaryProtocolDriver stopped");
        }
        log.info("ProprietaryProtocolDriver stopped for instrument {}", config.getInstrumentId());
    }

    @Override
    public boolean testConnection() {
        return channel.isOpen();
    }

    @Override
    public void registerListener(DataEventListener listener) {
        this.dataListener = listener;
    }

    @Override
    public void registerTelemetryListener(TelemetryListener listener) {
        this.telemetryListener = listener;
    }

    @Override
    public DiscoveryInfo getDiscoveryInfo() {
        DiscoveryInfo info = new DiscoveryInfo();
        info.setManufacturer("Generic");
        info.setModel("Proprietary Protocol");
        info.setSerialNumber("N/A");
        info.setFirmwareVersion("N/A");
        info.setHardwareRevision("N/A");
        info.setSupportedCommands(List.of());
        return info;
    }
}
