package com.myla.gateway.protocol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.CRC32;

/**
 * 私有协议帧编解码器（工具类）。
 * <p>
 * 提供私有二进制通信协议的帧编码（encode）和解码（decode）功能。
 * 这是一个不可实例化的工具类，所有方法均为静态方法。
 * </p>
 *
 * <h3>帧格式定义（大端序）：</h3>
 * <pre>
 * +------+--------+------+---------+--------+------+
 * | STX  | Length | Type | Payload | CRC32  | ETX  |
 * | 1B   | 2B     | 1B   | N bytes | 4B     | 1B   |
 * +------+--------+------+---------+--------+------+
 *
 * STX    : 帧起始标志，固定 0xAA
 * Length : 帧总长度（包含 STX 到 ETX 的全部字节），2 字节无符号短整型
 * Type   : 帧类型码，见 {@link FrameType}
 * Payload: 载荷数据，变长（可为空）
 * CRC32  : 对 Length + Type + Payload 区域的 CRC32 校验值，4 字节
 * ETX    : 帧结束标志，固定 0xBB
 * </pre>
 *
 * <h3>CRC32 计算范围：</h3>
 * <p>CRC32 校验覆盖 STX 之后、CRC32 字段之前的所有字节（即 Length + Type + Payload）。
 * 解码时会重新计算并比对，不匹配则抛出 IllegalArgumentException。</p>
 *
 * @author MyLA Team
 */
public final class ProprietaryFrameCodec {

    /** 帧起始标志：0xAA */
    public static final byte STX = (byte) 0xAA;

    /** 帧结束标志：0xBB */
    public static final byte ETX = (byte) 0xBB;

    /** 帧头部大小（字节）：STX(1) + Length(2) + Type(1) = 4 */
    public static final int HEADER_SIZE = 4;

    /** 帧尾部大小（字节）：CRC32(4) + ETX(1) = 5 */
    public static final int FOOTER_SIZE = 5;

    /** 最小帧大小（字节）：仅有头部和尾部，无载荷 = 9 */
    public static final int MIN_FRAME_SIZE = HEADER_SIZE + FOOTER_SIZE;

    /** 私有构造函数，防止实例化 */
    private ProprietaryFrameCodec() {
    }

    /**
     * 编码帧：将帧类型和载荷打包为二进制字节数组。
     * <p>
     * 编码步骤：
     * <ol>
     *   <li>计算总长度：头部(4) + 载荷(N) + 尾部(5)</li>
     *   <li>按大端序写入 STX -> Length -> Type -> Payload</li>
     *   <li>计算 STX 之后、CRC32 之前区域的 CRC32 值</li>
     *   <li>追加 CRC32 和 ETX</li>
     * </ol>
     * </p>
     *
     * @param type 帧类型枚举
     * @param payload 载荷字节数组，可为 null（表示无载荷）
     * @return 编码后的完整帧字节数组，长度至少为 MIN_FRAME_SIZE(9)
     */
    public static byte[] encode(FrameType type, byte[] payload) {
        int payloadLen = (payload != null) ? payload.length : 0;
        int totalLen = MIN_FRAME_SIZE + payloadLen;

        ByteBuffer buf = ByteBuffer.allocate(totalLen);
        buf.order(ByteOrder.BIG_ENDIAN);

        // 写入帧头
        buf.put(STX);                           // 帧起始标志
        buf.putShort((short) totalLen);         // 帧总长度
        buf.put(type.getCode());                // 帧类型码

        // 写入载荷
        if (payload != null && payload.length > 0) {
            buf.put(payload);
        }

        // 计算 CRC32：覆盖 STX 之后、CRC32 之前的所有字节
        byte[] frame = buf.array();
        CRC32 crc = new CRC32();
        crc.update(frame, 1, totalLen - FOOTER_SIZE); // 从索引 1(STX后)开始，长度为 totalLen - 5(尾部大小)
        long crcValue = crc.getValue();

        // 写入帧尾
        buf.putInt((int) crcValue);             // CRC32 校验值
        buf.put(ETX);                           // 帧结束标志

        return buf.array();
    }

    /**
     * 解码帧：从二进制字节数组解析出帧类型和载荷。
     * <p>
     * 解码步骤：
     * <ol>
     *   <li>校验帧最小长度（至少 9 字节）</li>
     *   <li>校验 STX 起始标志（必须为 0xAA）</li>
     *   <li>校验 ETX 结束标志（必须为 0xBB）</li>
     *   <li>读取 Length、Type 字段</li>
     *   <li>读取 Payload 字段（如果有）</li>
     *   <li>读取并校验 CRC32 值</li>
     *   <li>返回 DecodedFrame 记录</li>
     * </ol>
     * </p>
     *
     * @param frame 待解码的完整帧字节数组
     * @return 解码后的帧信息记录，包含帧类型和载荷（载荷可能为 null）
     * @throws IllegalArgumentException 在以下情况抛出：
     *         <ul>
     *           <li>帧过短（小于 9 字节）</li>
     *           <li>STX 字节不正确</li>
     *           <li>ETX 字节不正确</li>
     *           <li>CRC32 校验不匹配（数据损坏）</li>
     *         </ul>
     */
    public static DecodedFrame decode(byte[] frame) {
        // 1. 长度校验
        if (frame == null || frame.length < MIN_FRAME_SIZE) {
            throw new IllegalArgumentException("Frame too short: " + (frame != null ? frame.length : 0));
        }

        // 2. STX 校验
        if (frame[0] != STX) {
            throw new IllegalArgumentException("Invalid STX byte: 0x" + Integer.toHexString(frame[0] & 0xFF));
        }

        // 3. ETX 校验（最后一个字节）
        if (frame[frame.length - 1] != ETX) {
            throw new IllegalArgumentException("Invalid ETX byte: 0x" + Integer.toHexString(frame[frame.length - 1] & 0xFF));
        }

        ByteBuffer buf = ByteBuffer.wrap(frame);
        buf.order(ByteOrder.BIG_ENDIAN);

        buf.get();                      // 跳过 STX
        short length = buf.getShort();  // 帧总长度
        byte typeCode = buf.get();      // 帧类型码

        // 4. 解析帧类型
        FrameType type = FrameType.fromCode(typeCode);

        // 5. 读取载荷
        int payloadLen = length - MIN_FRAME_SIZE;
        byte[] payload = null;
        if (payloadLen > 0) {
            payload = new byte[payloadLen];
            buf.get(payload);
        }

        // 6. 校验 CRC32
        int receivedCrc = buf.getInt();
        CRC32 crc = new CRC32();
        crc.update(frame, 1, length - FOOTER_SIZE);
        long calculatedCrc = crc.getValue();
        if (calculatedCrc != (receivedCrc & 0xFFFFFFFFL)) {
            throw new IllegalArgumentException(
                String.format("CRC mismatch: received 0x%08X, calculated 0x%08X", receivedCrc, calculatedCrc));
        }

        // 7. 返回解码结果
        return new DecodedFrame(type, payload);
    }

    /**
     * 解码后的帧信息记录。
     *
     * @param type 帧类型
     * @param payload 载荷字节数组，无载荷时为 null
     */
    public record DecodedFrame(FrameType type, byte[] payload) {
    }
}
