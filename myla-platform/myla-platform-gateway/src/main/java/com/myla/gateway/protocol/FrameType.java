package com.myla.gateway.protocol;

import lombok.Getter;

/**
 * 私有协议帧类型枚举。
 * <p>
 * 定义私有二进制通信协议中每种帧的类型码（type code）。
 * 帧类型码占 1 个字节，是帧头部的一部分，用于标识帧的用途。
 * 与 {@link ProprietaryFrameCodec} 配合使用。
 * </p>
 *
 * @author MyLA Team
 * @see ProprietaryFrameCodec
 */
@Getter
public enum FrameType {

    /** 心跳帧（0x01）：用于维持长连接，定期发送，无业务数据 */
    HEARTBEAT((byte) 0x01),

    /** 结果推送帧（0x02）：仪器推送检验结果，载荷为 JSON 格式 */
    RESULT_PUSH((byte) 0x02),

    /** 命令帧（0x03）：网关向仪器下发指令 */
    COMMAND((byte) 0x03),

    /** 命令确认帧（0x04）：仪器对命令的确认应答 */
    COMMAND_ACK((byte) 0x04),

    /** 遥测帧（0x05）：仪器定期上报运行环境数据 */
    TELEMETRY((byte) 0x05),

    /** 固件升级帧（0x06）：用于向仪器推送固件数据 */
    FW_UPGRADE((byte) 0x06),

    /** 发现帧（0x07）：仪器上线时发送，声明自身身份信息 */
    DISCOVERY((byte) 0x07),

    /** 错误帧（0xFF）：仪器上报错误信息 */
    ERROR((byte) 0xFF);

    /** 帧类型字节码 */
    private final byte code;

    FrameType(byte code) {
        this.code = code;
    }

    /**
     * 根据字节码查找对应的帧类型。
     * <p>遍历所有枚举值，匹配对应的 code 字段。</p>
     *
     * @param code 帧类型字节码
     * @return 匹配的 FrameType 枚举值
     * @throws IllegalArgumentException 如果传入的 code 不匹配任何已知帧类型
     */
    public static FrameType fromCode(byte code) {
        for (FrameType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown frame type code: 0x" + Integer.toHexString(code & 0xFF));
    }
}
