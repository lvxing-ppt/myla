package com.myla.gateway.core.context;

public interface DriverContext {
    String getDriverId();
    String getInstrumentId();
    void saveRawMessage(String instrumentId, String messageType, byte[] rawData);
    void publishResult(byte[] rawData);
    void reportHealth(String instrumentId, String status, String message);
    void registerRetryScheduler(String key, Runnable task, long initialDelayMs, long maxDelayMs);
    void cancelRetryScheduler(String key);
    void sendAlert(String instrumentId, String alertType, String message);
}
