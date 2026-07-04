package com.myla.gateway.core.spi;

import java.util.List;

/**
 * 数据帧分割器接口（SPI）。
 * <p>
 * 定义将仪器 TCP 流式数据（或文件内容）切分为完整数据帧的契约。
 * 仪器通信通常是流式的，数据可能分多次到达，帧边界需要由分桢器识别。
 * </p>
 *
 * <h3>实现者契约：</h3>
 * <ul>
 *   <li>{@link #getSplitterType()} — 必须返回唯一的分桢器类型标识</li>
 *   <li>{@link #splitFrames(byte[], List)} — 核心分桢逻辑
 *     <ul>
 *       <li>输入参数 {@code rawBytes}：新到达的原始字节数组</li>
 *       <li>输入/输出参数 {@code incompleteFrames}：上次未完成的帧片段
 *           <ul>
 *             <li>调用前包含上次遗留的不完整数据</li>
 *             <li>调用后应更新为本次处理后仍不完整的数据（供下次调用拼帧使用）</li>
 *           </ul>
 *       </li>
 *       <li>返回值：本次切出的完整帧列表</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <h3>分桢策略示例：</h3>
 * <ul>
 *   <li><b>ASTM</b> — 以 STX(0x02) 开头、ETX(0x03)/ETB(0x17) 结尾为一个完整帧</li>
 *   <li><b>HL7-MLLP</b> — 以 VT(0x0B) 开头、FS(0x1C)+CR(0x0D) 结尾为一个完整帧</li>
 * </ul>
 *
 * @author MyLA Team
 */
public interface FrameSplitter {

    /**
     * 获取分桢器类型标识。
     * @return 分桢器类型字符串，如 "ASTM"、"HL7-MLLP"
     */
    String getSplitterType();

    /**
     * 从原始字节流中切分完整的数据帧。
     * <p>
     * 此方法维护跨调用的分桢状态：调用方将上次返回的不完整片段重新传入
     * {@code incompleteFrames}，与本次新数据拼接后继续尝试切分。
     * </p>
     *
     * @param rawBytes 新到达的原始字节数组
     * @param incompleteFrames 上次未完成帧片段的列表（输入/输出参数）
     *                         调用前包含上次的残留数据；调用后更新为本次的残留数据
     * @return 本次切出的完整帧列表（每个元素是一个完整的帧）
     */
    List<byte[]> splitFrames(byte[] rawBytes, List<byte[]> incompleteFrames);
}
