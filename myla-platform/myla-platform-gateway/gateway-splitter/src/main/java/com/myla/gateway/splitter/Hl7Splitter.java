package com.myla.gateway.splitter;

import com.myla.gateway.core.spi.FrameSplitter;
import java.util.*;

/**
 * HL7 MLLP（Minimal Lower Layer Protocol）帧分割器。
 * <p>
 * 实现 {@link FrameSplitter} 接口，用于从 TCP 流式数据中切分 HL7 MLLP 封装的数据帧。
 * MLLP 是 HL7 消息在 TCP 上传输的标准底层协议。
 * </p>
 *
 * <h3>MLLP 帧格式：</h3>
 * <pre>
 * VT [HL7 Message] FS CR
 * 0x0B               0x1C 0x0D
 *
 * VT  — 帧起始标志（Vertical Tab，0x0B）
 * FS  — 字段分隔符（File Separator，0x1C），表示消息结束
 * CR  — 回车符（Carriage Return，0x0D），与 FS 组成帧尾
 * </pre>
 *
 * <h3>拼帧机制：</h3>
 * <p>
 * 当一次读取的数据在 VT 之后、FS+CR 之前结束时，该不完整帧的剩余字节
 * 被保存到 incompleteFrames 列表。下次收到新数据时，与上次残留拼接后继续搜索 FS+CR 边界。
 * 使用 {@link #merge(List, byte[])} 方法将残留片段与新数据拼接为一个连续数组。
 * </p>
 *
 * <h3>算法说明：</h3>
 * <ol>
 *   <li>将 incompleteFrames 中的残留数据与新数据拼接为完整缓冲区</li>
 *   <li>遍历缓冲区，寻找 VT 标记帧起始位置</li>
 *   <li>在 VT 之后搜索 FS+CR 连续两个字节标记帧结束</li>
 *   <li>找到完整帧后，切出子数组加入结果列表</li>
 *   <li>遍历结束后，如果仍有 VT 但未找到 FS+CR，将残留数据保存到 incompleteFrames</li>
 * </ol>
 *
 * @author MyLA Team
 */
public class Hl7Splitter implements FrameSplitter {

    /** VT — 帧起始标志 (0x0B) */
    private static final byte VT = 0x0B;

    /** FS — 消息结束标志 (0x1C) */
    private static final byte FS = 0x1C;

    /** CR — 回车符 (0x0D)，与 FS 组成帧尾 */
    private static final byte CR = 0x0D;

    /**
     * 获取分桢器类型标识。
     * @return 固定返回 "HL7-MLLP"
     */
    @Override
    public String getSplitterType() {
        return "HL7-MLLP";
    }

    /**
     * 从原始字节流中切分 HL7 MLLP 完整帧。
     *
     * @param raw 新到达的原始字节数组
     * @param incomplete 上次未完成帧片段的列表（输入/输出参数）
     * @return 本次切出的完整 HL7 MLLP 帧列表
     */
    @Override
    public List<byte[]> splitFrames(byte[] raw, List<byte[]> incomplete) {
        List<byte[]> frames = new ArrayList<>();

        // 将上次残留片段与新数据拼接为连续缓冲区
        byte[] buf = (incomplete != null && !incomplete.isEmpty()) ? merge(incomplete, raw) : raw;

        int s = -1; // VT（帧起始）位置索引，-1 表示尚未找到

        for (int i = 0; i < buf.length - 1; i++) {
            // 找到 VT，标记帧起始位置
            if (buf[i] == VT) {
                s = i;
            }

            // 在 VT 之后找到 FS+CR，标记帧结束
            if (s >= 0 && buf[i] == FS && buf[i + 1] == CR) {
                // 切出从 VT 到 CR 的完整帧
                byte[] f = new byte[i + 2 - s];
                System.arraycopy(buf, s, f, 0, f.length);
                frames.add(f);
                s = -1; // 重置，继续搜索下一帧
            }
        }

        // 清空 incomplete，准备写入新的残留数据
        if (incomplete != null) {
            incomplete.clear();
        }

        // 如果有未完成的帧（找到 VT 但未找到 FS+CR），保存残留数据
        if (s >= 0 && s < buf.length && incomplete != null) {
            byte[] rem = new byte[buf.length - s];
            System.arraycopy(buf, s, rem, 0, rem.length);
            incomplete.add(rem);
        }

        return frames;
    }

    /**
     * 将上次残留的多个片段与新数据拼接为一个连续的字节数组。
     *
     * @param frags 上次残留的帧片段列表
     * @param n 新到达的字节数组
     * @return 拼接后的完整字节数组
     */
    private byte[] merge(List<byte[]> frags, byte[] n) {
        // 计算总长度
        int t = n.length;
        for (byte[] f : frags) {
            t += f.length;
        }

        // 拼接
        byte[] m = new byte[t];
        int p = 0;
        for (byte[] f : frags) {
            System.arraycopy(f, 0, m, p, f.length);
            p += f.length;
        }
        System.arraycopy(n, 0, m, p, n.length);
        frags.clear();
        return m;
    }
}
