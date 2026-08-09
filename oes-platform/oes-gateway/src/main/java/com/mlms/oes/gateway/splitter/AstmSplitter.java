package com.mlms.oes.gateway.splitter;

import com.mlms.oes.gateway.core.spi.FrameSplitter;
import java.util.*;

/**
 * ASTM 协议帧分割器。
 * <p>
 * 实现 {@link FrameSplitter} 接口，用于从 TCP 流式数据中切分 ASTM E1394/E1381 标准的数据帧。
 * </p>
 *
 * <h3>分桢规则：</h3>
 * <p>
 * ASTM 帧以 STX(0x02) 为起始标志，以 ETX(0x03) 或 ETB(0x17) 为结束标志：
 * <ul>
 *   <li>ETX(0x03) — 最后一帧的结束标志</li>
 *   <li>ETB(0x17) — 中间帧的结束标志（后续还有帧）</li>
 * </ul>
 * 一个完整的帧从 STX 到紧随其后的 ETX/ETB 之间的所有字节。
 * </p>
 *
 * <h3>拼帧机制：</h3>
 * <p>
 * 由于 TCP 是流式协议，一次 read() 可能只收到部分帧数据。
 * 当遇到 STX 后但未遇到 ETX/ETB 时数据已读完，则将该不完整帧保存到 incompleteFrames 列表，
 * 下次读取时与新数据拼接后继续处理。
 * </p>
 *
 * <h3>算法说明：</h3>
 * <ol>
 *   <li>先将上次残留的 incompleteFrames 加入结果列表并清空</li>
 *   <li>遍历每个字节，使用布尔标志 {@code in} 标记是否处于帧内部
 *     <ul>
 *       <li>遇到 STX：如果已在帧内，说明之前是不完整帧，保存到 incompleteFrames；开始新帧</li>
 *       <li>在帧内时：将字节追加到当前帧缓冲区</li>
 *       <li>遇到 ETX 或 ETB：当前帧结束，保存到结果列表</li>
 *     </ul>
 *   </li>
 *   <li>遍历结束后，如果仍在帧内（有 STX 无 ETX/ETB），将剩余数据保存到 incompleteFrames</li>
 * </ol>
 *
 * @author MLMS Team
 */
public class AstmSplitter implements FrameSplitter {

    /** STX — 帧起始标志 (0x02) */
    private static final byte STX = 0x02;

    /** ETX — 帧结束标志 (0x03)，表示最后一帧 */
    private static final byte ETX = 0x03;

    /** ETB — 帧块结束标志 (0x17)，表示中间帧（后续还有帧） */
    private static final byte ETB = 0x17;

    /**
     * 获取分桢器类型标识。
     * @return 固定返回 "ASTM"
     */
    @Override
    public String getSplitterType() {
        return "ASTM";
    }

    /**
     * 从原始字节流中切分 ASTM 完整帧。
     *
     * @param raw 新到达的原始字节数组
     * @param incomplete 上次未完成帧片段的列表（输入/输出参数）
     * @return 本次切出的完整 ASTM 帧列表
     */
    @Override
    public List<byte[]> splitFrames(byte[] raw, List<byte[]> incomplete) {
        List<byte[]> frames = new ArrayList<>();

        // 将上次残留帧与本次新数据拼接，统一走状态机处理
        byte[] combined = raw;
        if (incomplete != null && !incomplete.isEmpty()) {
            int totalLen = raw.length;
            for (byte[] frag : incomplete) {
                totalLen += frag.length;
            }
            combined = new byte[totalLen];
            int pos = 0;
            for (byte[] frag : incomplete) {
                System.arraycopy(frag, 0, combined, pos, frag.length);
                pos += frag.length;
            }
            System.arraycopy(raw, 0, combined, pos, raw.length);
            incomplete.clear();
        }

        List<Byte> cur = new ArrayList<>();  // 当前帧缓冲区
        boolean in = false;                   // 是否处于帧内部（遇到 STX 后、ETX/ETB 前）

        for (byte b : combined) {
            // 遇到 STX：开始新帧
            if (b == STX) {
                // 如果之前已在帧内但遇到了新的 STX，说明上一帧不完整，保存为残留
                if (in && incomplete != null) {
                    incomplete.add(toArr(cur));
                }
                cur.clear();
                in = true;
            }

            if (in) {
                cur.add(b);
            }

            // 遇到 ETX 或 ETB：帧结束
            if (in && (b == ETX || b == ETB)) {
                frames.add(toArr(cur));
                cur.clear();
                in = false;
            }
        }

        // 读取结束但仍在帧内：保存不完整帧供下次拼接
        if (in && !cur.isEmpty() && incomplete != null) {
            incomplete.add(toArr(cur));
        }

        return frames;
    }

    /**
     * 将 Byte 列表转换为 byte 数组。
     * @param list Byte 列表
     * @return byte 数组
     */
    private byte[] toArr(List<Byte> list) {
        byte[] a = new byte[list.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = list.get(i);
        }
        return a;
    }
}
