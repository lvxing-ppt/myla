package com.myla.gateway.core.spi;
import java.util.List;

public interface FrameSplitter {
    String getSplitterType();
    List<byte[]> splitFrames(byte[] rawBytes, List<byte[]> incompleteFrames);
}
