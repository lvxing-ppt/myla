package com.mlms.capl.outbound;

import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * HL7 MLLP 发送器（通讯层侧）。
 * 短连接模式：每次发送建立一次 TCP 连接，等待 ACK 后关闭。
 */
@Slf4j
public class Hl7MllpSender implements LisOutboundSender {

    private static final byte VT = 0x0B;
    private static final byte FS = 0x1C;
    private static final byte CR = 0x0D;

    @Override
    public String getChannelType() { return "HL7"; }

    @Override
    public SendResult send(Map<String, Object> channelConfig, String messageContent, String hospitalCode) {
        try {
            String host = (String) channelConfig.getOrDefault("host", "localhost");
            int port = channelConfig.get("port") instanceof Number n ? n.intValue() : 2575;
            int ackTimeoutSec = channelConfig.get("ackTimeoutSec") instanceof Number n ? n.intValue() : 30;

            try (Socket sock = new Socket(host, port)) {
                sock.setSoTimeout(ackTimeoutSec * 1000);
                OutputStream out = sock.getOutputStream();
                InputStream in = sock.getInputStream();

                byte[] hl7 = messageContent.getBytes(StandardCharsets.UTF_8);
                byte[] frame = new byte[hl7.length + 3];
                frame[0] = VT;
                System.arraycopy(hl7, 0, frame, 1, hl7.length);
                frame[hl7.length + 1] = FS;
                frame[hl7.length + 2] = CR;
                out.write(frame);
                out.flush();

                byte[] ackBuf = in.readAllBytes();
                String ack = new String(ackBuf, StandardCharsets.UTF_8).trim();
                if (ack.contains("MSA|AA")) {
                    log.info("HL7 MLLP sent OK: hospital={}", hospitalCode);
                    return SendResult.ok();
                } else if (ack.contains("MSA|AR") || ack.contains("MSA|AE")) {
                    return SendResult.fail("LIS rejected: " + ack.substring(0, Math.min(200, ack.length())));
                } else {
                    return SendResult.fail("Unexpected ACK: " + ack.substring(0, Math.min(200, ack.length())));
                }
            }
        } catch (Exception e) {
            log.error("HL7 MLLP send failed: hospital={}, error={}", hospitalCode, e.getMessage());
            return SendResult.fail(e.getMessage());
        }
    }
}
