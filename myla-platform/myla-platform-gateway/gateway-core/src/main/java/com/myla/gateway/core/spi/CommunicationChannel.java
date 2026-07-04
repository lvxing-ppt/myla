package com.myla.gateway.core.spi;

import com.myla.gateway.core.context.DriverConfig;
import com.myla.gateway.core.model.ConnectionError;
import java.util.function.Consumer;

public interface CommunicationChannel {
    String getChannelType();
    void open(DriverConfig.ChannelConfig config);
    void close();
    boolean isOpen();
    void send(byte[] data);
    void setMessageListener(Consumer<byte[]> onMessage);
    void setErrorListener(Consumer<ConnectionError> onError);
}
