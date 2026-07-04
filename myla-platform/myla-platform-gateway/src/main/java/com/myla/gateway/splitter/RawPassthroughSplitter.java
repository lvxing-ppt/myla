package com.myla.gateway.splitter;

import com.myla.gateway.core.spi.FrameSplitter;

import java.util.ArrayList;
import java.util.List;

/**
 * 透传分桢器 — 不做任何帧切分，直接把 raw bytes 当作完整帧返回。
 * <p>
 * 适用于：
 * <ul>
 *   <li>每次 TCP read 就是一个完整 JSON 对象</li>
 *   <li>仪器每发一条消息就断开连接（短连接模式）</li>
 *   <li>私有协议自带帧边界，不需要额外切分</li>
 * </ul>
 * </p>
 */
public class RawPassthroughSplitter implements FrameSplitter {

    @Override
    public String getSplitterType() {
        return "RAW-PASSTHROUGH";
    }

    @Override
    public List<byte[]> splitFrames(byte[] rawBytes, List<byte[]> incompleteFrames) {
        List<byte[]> frames = new ArrayList<>();

        // 如果有上次残留数据，拼接到前面
        byte[] combined = rawBytes;
        if (incompleteFrames != null && !incompleteFrames.isEmpty()) {
            int totalLen = rawBytes.length;
            for (byte[] frag : incompleteFrames) totalLen += frag.length;
            combined = new byte[totalLen];
            int pos = 0;
            for (byte[] frag : incompleteFrames) {
                System.arraycopy(frag, 0, combined, pos, frag.length);
                pos += frag.length;
            }
            System.arraycopy(rawBytes, 0, combined, pos, rawBytes.length);
            incompleteFrames.clear();
        }

        frames.add(combined);
        return frames;
    }
}
