package com.myla.gateway.protocol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.CRC32;

public final class ProprietaryFrameCodec {

    public static final byte STX = (byte) 0xAA;
    public static final byte ETX = (byte) 0xBB;
    public static final int HEADER_SIZE = 4; // STX(1) + Length(2) + Type(1)
    public static final int FOOTER_SIZE = 5; // CRC32(4) + ETX(1)
    public static final int MIN_FRAME_SIZE = HEADER_SIZE + FOOTER_SIZE;

    private ProprietaryFrameCodec() {
    }

    public static byte[] encode(FrameType type, byte[] payload) {
        int payloadLen = (payload != null) ? payload.length : 0;
        int totalLen = MIN_FRAME_SIZE + payloadLen;

        ByteBuffer buf = ByteBuffer.allocate(totalLen);
        buf.order(ByteOrder.BIG_ENDIAN);
        buf.put(STX);
        buf.putShort((short) totalLen);
        buf.put(type.getCode());
        if (payload != null && payload.length > 0) {
            buf.put(payload);
        }

        // Calculate CRC32 over Length + Type + Payload (bytes after STX, before CRC)
        byte[] frame = buf.array();
        CRC32 crc = new CRC32();
        crc.update(frame, 1, totalLen - FOOTER_SIZE);
        long crcValue = crc.getValue();

        buf.putInt((int) crcValue);
        buf.put(ETX);

        return buf.array();
    }

    public static DecodedFrame decode(byte[] frame) {
        if (frame == null || frame.length < MIN_FRAME_SIZE) {
            throw new IllegalArgumentException("Frame too short: " + (frame != null ? frame.length : 0));
        }
        if (frame[0] != STX) {
            throw new IllegalArgumentException("Invalid STX byte: 0x" + Integer.toHexString(frame[0] & 0xFF));
        }
        if (frame[frame.length - 1] != ETX) {
            throw new IllegalArgumentException("Invalid ETX byte: 0x" + Integer.toHexString(frame[frame.length - 1] & 0xFF));
        }

        ByteBuffer buf = ByteBuffer.wrap(frame);
        buf.order(ByteOrder.BIG_ENDIAN);
        buf.get(); // STX
        short length = buf.getShort();
        byte typeCode = buf.get();

        FrameType type = FrameType.fromCode(typeCode);

        int payloadLen = length - MIN_FRAME_SIZE;
        byte[] payload = null;
        if (payloadLen > 0) {
            payload = new byte[payloadLen];
            buf.get(payload);
        }

        int receivedCrc = buf.getInt();
        // Verify CRC32
        CRC32 crc = new CRC32();
        crc.update(frame, 1, length - FOOTER_SIZE);
        long calculatedCrc = crc.getValue();
        if (calculatedCrc != (receivedCrc & 0xFFFFFFFFL)) {
            throw new IllegalArgumentException(
                String.format("CRC mismatch: received 0x%08X, calculated 0x%08X", receivedCrc, calculatedCrc));
        }

        return new DecodedFrame(type, payload);
    }

    public record DecodedFrame(FrameType type, byte[] payload) {
    }
}
