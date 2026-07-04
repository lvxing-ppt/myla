package com.myla.gateway.splitter;

import com.myla.gateway.core.spi.FrameSplitter;
import java.util.*;

public class Hl7Splitter implements FrameSplitter {
    private static final byte VT = 0x0B, FS = 0x1C, CR = 0x0D;

    @Override public String getSplitterType() { return "HL7-MLLP"; }

    @Override
    public List<byte[]> splitFrames(byte[] raw, List<byte[]> incomplete) {
        List<byte[]> frames = new ArrayList<>();
        byte[] buf = (incomplete != null && !incomplete.isEmpty()) ? merge(incomplete, raw) : raw;
        int s = -1;
        for (int i = 0; i < buf.length - 1; i++) {
            if (buf[i] == VT) s = i;
            if (s >= 0 && buf[i] == FS && buf[i + 1] == CR) {
                byte[] f = new byte[i + 2 - s];
                System.arraycopy(buf, s, f, 0, f.length);
                frames.add(f); s = -1;
            }
        }
        if (incomplete != null) incomplete.clear();
        if (s >= 0 && s < buf.length && incomplete != null) {
            byte[] rem = new byte[buf.length - s];
            System.arraycopy(buf, s, rem, 0, rem.length);
            incomplete.add(rem);
        }
        return frames;
    }

    private byte[] merge(List<byte[]> frags, byte[] n) {
        int t = n.length;
        for (byte[] f : frags) t += f.length;
        byte[] m = new byte[t]; int p = 0;
        for (byte[] f : frags) { System.arraycopy(f, 0, m, p, f.length); p += f.length; }
        System.arraycopy(n, 0, m, p, n.length);
        frags.clear(); return m;
    }
}
