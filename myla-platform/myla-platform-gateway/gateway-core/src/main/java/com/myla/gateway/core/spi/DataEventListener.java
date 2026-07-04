package com.myla.gateway.core.spi;

import com.myla.common.api.dto.UnifiedResult;
import com.myla.gateway.core.model.InstrumentStatus;

public interface DataEventListener {
    void onResultReceived(UnifiedResult result);
    void onParseFailed(String rawText, String error);
    void onStatusChanged(InstrumentStatus status);
    void onConnectionError(String instrumentId, String error, int consecutiveFailures);
}
