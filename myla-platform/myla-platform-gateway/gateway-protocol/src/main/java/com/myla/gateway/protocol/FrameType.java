package com.myla.gateway.protocol;

import lombok.Getter;

@Getter
public enum FrameType {
    HEARTBEAT((byte) 0x01),
    RESULT_PUSH((byte) 0x02),
    COMMAND((byte) 0x03),
    COMMAND_ACK((byte) 0x04),
    TELEMETRY((byte) 0x05),
    FW_UPGRADE((byte) 0x06),
    DISCOVERY((byte) 0x07),
    ERROR((byte) 0xFF);

    private final byte code;

    FrameType(byte code) {
        this.code = code;
    }

    public static FrameType fromCode(byte code) {
        for (FrameType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown frame type code: 0x" + Integer.toHexString(code & 0xFF));
    }
}
