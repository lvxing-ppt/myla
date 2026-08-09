package com.mlms.oes.gateway.core.spi;

import com.mlms.oes.common.api.dto.UnifiedResult;
import com.mlms.oes.gateway.core.model.InstrumentStatus;

/**
 * 数据事件监听器接口（SPI）。
 * <p>
 * 定义驱动层向上层业务模块通知数据事件的回调契约。
 * 上层业务模块实现此接口并注册到驱动中，以接收结果数据、解析失败、状态变化和连接错误等事件。
 * </p>
 *
 * <h3>回调场景：</h3>
 * <ul>
 *   <li>仪器上报数据被成功解析后 -> {@link #onResultReceived(UnifiedResult)}</li>
 *   <li>仪器上报数据解析失败 -> {@link #onParseFailed(String, String)}</li>
 *   <li>仪器连接状态发生变化 -> {@link #onStatusChanged(InstrumentStatus)}</li>
 *   <li>仪器连接发生错误 -> {@link #onConnectionError(String, String, int)}</li>
 * </ul>
 *
 * @author MLMS Team
 */
public interface DataEventListener {

    /**
     * 成功解析到检验结果时的回调。
     * @param result 统一格式的检验结果对象
     */
    void onResultReceived(UnifiedResult result);

    /**
     * 原始报文解析失败时的回调。
     * @param rawText 解析失败的原始报文字符串
     * @param error 错误描述信息
     */
    void onParseFailed(String rawText, String error);

    /**
     * 仪器状态发生变化时的回调。
     * @param status 更新后的仪器状态对象
     */
    void onStatusChanged(InstrumentStatus status);

    /**
     * 仪器连接发生错误时的回调。
     * <p>可用于触发告警或自动重连逻辑。</p>
     *
     * @param instrumentId 发生错误的仪器 ID
     * @param error 错误描述信息
     * @param consecutiveFailures 连续失败次数
     */
    void onConnectionError(String instrumentId, String error, int consecutiveFailures);
}
