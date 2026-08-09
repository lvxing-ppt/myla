package com.mlms.oes.gateway.core.spi;

import com.mlms.oes.gateway.core.model.TelemetryData;

/**
 * 遥测数据监听器接口（SPI）。
 * <p>
 * 定义接收仪器遥测数据的回调契约。上层监控模块实现此接口并注册到驱动中，
 * 以实时接收仪器的运行环境数据（温度、湿度、电源状态、试剂余量、故障信息等）。
 * </p>
 *
 * @author MLMS Team
 */
public interface TelemetryListener {

    /**
     * 接收到仪器遥测数据时的回调。
     *
     * @param instrumentId 上报遥测数据的仪器 ID
     * @param data 遥测数据对象，包含温度、湿度、电源状态等信息
     */
    void onTelemetry(String instrumentId, TelemetryData data);
}
