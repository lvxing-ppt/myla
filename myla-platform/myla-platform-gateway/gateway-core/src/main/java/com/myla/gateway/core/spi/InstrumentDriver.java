package com.myla.gateway.core.spi;

import com.myla.common.api.enums.CommunicationMode;
import com.myla.gateway.core.context.DriverConfig;
import com.myla.gateway.core.context.DriverContext;
import com.myla.gateway.core.model.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface InstrumentDriver {
    String getDriverId();
    String getDisplayName();
    String getVersion();
    CommunicationMode getMode();
    void initialize(DriverConfig config);
    void start(DriverContext ctx);
    void stop();
    boolean testConnection();
    void registerListener(DataEventListener listener);

    default CompletableFuture<CommandResult> executeCommand(InstrumentCommand command) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("Commands not supported"));
    }

    DiscoveryInfo getDiscoveryInfo();

    default void registerTelemetryListener(TelemetryListener listener) {}

    default List<MaintenanceCapability> getMaintenanceCapabilities() { return List.of(); }
    default CompletableFuture<CommandResult> executeMaintenance(MaintenanceCommand cmd) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("Maintenance not supported"));
    }
}
