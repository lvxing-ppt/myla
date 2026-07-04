package com.myla.gateway.splitter;

import com.myla.gateway.core.spi.FrameSplitter;
import java.util.*;

public class AstmSplitter implements FrameSplitter {
    private static final byte STX = 0x02, ETX = 0x03, ETB = 0x17;

    @Override public String getSplitterType() { return "ASTM"; }

    @Override
    public List<byte[]> splitFrames(byte[] raw, List<byte[]> incomplete) {
        List<byte[]> frames = new ArrayList<>();
        if (incomplete != null && !incomplete.isEmpty()) {
            for (byte[] frag : incomplete) frames.add(frag);
            incomplete.clear();
        }
        List<Byte> cur = new ArrayList<>();
        boolean in = false;
        for (byte b : raw) {
            if (b == STX) {
                if (in && incomplete != null) incomplete.add(toArr(cur));
                cur.clear(); in = true;
            }
            if (in) cur.add(b);
            if (in && (b == ETX || b == ETB)) { frames.add(toArr(cur)); cur.clear(); in = false; }
        }
        if (in && !cur.isEmpty() && incomplete != null) incomplete.add(toArr(cur));
        return frames;
    }

    private byte[] toArr(List<Byte> list) {
        byte[] a = new byte[list.size()];
        for (int i = 0; i < a.length; i++) a[i] = list.get(i);
        return a;
    }
}
