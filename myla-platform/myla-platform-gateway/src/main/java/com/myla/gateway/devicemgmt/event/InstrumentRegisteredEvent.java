package com.myla.gateway.devicemgmt.event;

import org.springframework.context.ApplicationEvent;

/**
 * 仪器注册事件 — 由 Controller 发布，GatewayBootstrap 监听并热加载驱动。
 */
public class InstrumentRegisteredEvent extends ApplicationEvent {
    private final String instrumentId;

    public InstrumentRegisteredEvent(Object source, String instrumentId) {
        super(source);
        this.instrumentId = instrumentId;
    }

    public String getInstrumentId() {
        return instrumentId;
    }
}
