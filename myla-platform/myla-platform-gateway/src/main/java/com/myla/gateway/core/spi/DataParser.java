package com.myla.gateway.core.spi;

import com.myla.common.api.dto.UnifiedResult;
import com.myla.common.core.exception.ParseException;
import java.util.List;

/**
 * 数据解析器接口（SPI）。
 * <p>
 * 定义将仪器原始报文（分桢后的完整数据帧）解析为统一结果对象的契约。
 * 每种仪器协议（ASTM、HL7、私有协议）需提供对应的解析器实现。
 * </p>
 *
 * <h3>实现者契约：</h3>
 * <ul>
 *   <li>{@link #getParserId()} — 必须返回唯一的解析器标识</li>
 *   <li>{@link #parse(byte[])} — 将单个完整数据帧解析为一个或多个 {@link UnifiedResult}
 *       <ul>
 *         <li>输入：单帧完整的字节数组（已由分桢器处理）</li>
 *         <li>输出：解析出的统一结果列表（一帧可能对应多条结果）</li>
 *         <li>异常：解析失败时抛出 {@link ParseException}，应携带原始报文和错误详情</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * @author MyLA Team
 */
public interface DataParser {

    /**
     * 获取解析器唯一标识。
     * @return 解析器 ID，如 "vitek2-parser"、"hl7-parser"
     */
    String getParserId();

    /**
     * 解析单个完整数据帧为统一结果列表。
     * <p>
     * 实现者应根据自身协议特点解析帧内容：
     * <ul>
     *   <li>ASTM 协议：按 O|、R| 等记录类型解析</li>
     *   <li>HL7 协议：按 OBX 段解析</li>
     *   <li>私有协议：按自定格式解析</li>
     * </ul>
     * 一条数据帧可能包含多个独立的结果（如一张药敏卡多个抗生素），
     * 因此返回 List 而非单个对象。
     * </p>
     *
     * @param frame 完整的数据帧字节数组
     * @return 解析出的统一结果列表，不应为 null（至少返回空列表）
     * @throws ParseException 如果帧格式不符合预期，携带原始数据和错误详情
     */
    List<UnifiedResult> parse(byte[] frame) throws ParseException;
}
