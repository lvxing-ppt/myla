package com.myla.lis.outbound;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myla.lis.entity.LisConfig;
import com.myla.lis.entity.OutboundMessage;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * HL7 MLLP 发送器。
 * <p>
 * 通过 TCP MLLP 协议将 HL7 消息发送到 LIS 系统。
 * 短连接模式：每次发送建立一次 TCP 连接，等待 ACK 后关闭。
 * </p>
 */
@Slf4j
public class Hl7MllpSender implements LisOutboundSender {

    private static final byte VT = 0x0B;
    private static final byte FS = 0x1C;
    private static final byte CR = 0x0D;
    private static final ObjectMapper jsonMapper = new ObjectMapper();

    @Override
    public String getChannelType() {
        return "HL7";
    }

    @Override
    public SendResult send(OutboundMessage msg, LisConfig config) {
        try {
            String channelCfgJson = config.getChannelConfig();
            if (channelCfgJson == null || channelCfgJson.isBlank()) {
                return SendResult.fail("channel_config is empty");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> cfg = jsonMapper.readValue(channelCfgJson, Map.class);
            String host = (String) cfg.getOrDefault("host", "localhost");
            int port = cfg.get("port") instanceof Number n ? n.intValue() : 2575;
            int timeout = config.getAckTimeoutSec() != null ? config.getAckTimeoutSec() * 1000 : 30_000;

            try (Socket sock = new Socket(host, port)) {
                sock.setSoTimeout(timeout);
                OutputStream out = sock.getOutputStream();
                InputStream in = sock.getInputStream();

                // MLLP 封装
                byte[] hl7 = msg.getMessageContent().getBytes(StandardCharsets.UTF_8);
                byte[] frame = new byte[hl7.length + 3];
                frame[0] = VT;
                System.arraycopy(hl7, 0, frame, 1, hl7.length);
                frame[hl7.length + 1] = FS;
                frame[hl7.length + 2] = CR;
                out.write(frame);
                out.flush();

                // 等待 ACK
                byte[] ackBuf = in.readAllBytes();
                String ack = new String(ackBuf, StandardCharsets.UTF_8).trim();
                if (ack.contains("MSA|AA")) {
                    log.info("HL7 MLLP sent OK: messageId={}, hospital={}",
                            msg.getMessageId(), msg.getHospitalCode());
                    return SendResult.ok();
                } else if (ack.contains("MSA|AR") || ack.contains("MSA|AE")) {
                    return SendResult.fail("LIS rejected: " + ack.substring(0, Math.min(200, ack.length())));
                } else {
                    return SendResult.fail("Unexpected ACK: " + ack.substring(0, Math.min(200, ack.length())));
                }
            }
        } catch (Exception e) {
            log.error("HL7 MLLP send failed: messageId={}, hospital={}, error={}",
                    msg.getMessageId(), msg.getHospitalCode(), e.getMessage());
            return SendResult.fail(e.getMessage());
        }
    }

    @Override
    public boolean testConnection(LisConfig config) {
        try {
            String channelCfgJson = config.getChannelConfig();
            if (channelCfgJson == null) return false;
            @SuppressWarnings("unchecked")
            Map<String, Object> cfg = jsonMapper.readValue(channelCfgJson, Map.class);
            String host = (String) cfg.getOrDefault("host", "localhost");
            int port = cfg.get("port") instanceof Number n ? n.intValue() : 2575;
            try (Socket s = new Socket(host, port)) {
                s.setSoTimeout(3000);
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }
}
