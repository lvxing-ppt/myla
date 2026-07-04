package com.myla.gateway.core.spi;

import com.myla.gateway.core.model.TelemetryData;

public interface TelemetryListener {
    void onTelemetry(String instrumentId, TelemetryData data);
}
