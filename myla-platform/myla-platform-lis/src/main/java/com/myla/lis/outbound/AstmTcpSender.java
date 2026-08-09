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
 * ASTM TCP 发送器。
 * <p>
 * 通过 TCP 连接以 ASTM E1394 帧格式（STX...ETX）发送结果到 LIS。
 * 发送后等待 ACK(0x06) 确认。
 * </p>
 */
@Slf4j
public class AstmTcpSender implements LisOutboundSender {

    private static final byte STX = 0x02;
    private static final byte ETX = 0x03;
    private static final byte ACK = 0x06;
    private static final byte NAK = 0x15;
    private static final ObjectMapper jsonMapper = new ObjectMapper();

    @Override
    public String getChannelType() {
        return "ASTM";
    }

    @Override
    public SendResult send(OutboundMessage msg, LisConfig config) {
        try {
            String channelCfgJson = config.getOutboundConfig();
            if (channelCfgJson == null || channelCfgJson.isBlank()) {
                return SendResult.fail("outbound_config is empty");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> cfg = jsonMapper.readValue(channelCfgJson, Map.class);
            String host = (String) cfg.getOrDefault("host", "localhost");
            int port = cfg.get("port") instanceof Number n ? n.intValue() : 2000;
            int timeout = config.getAckTimeoutSec() != null ? config.getAckTimeoutSec() * 1000 : 30_000;

            try (Socket sock = new Socket(host, port)) {
                sock.setSoTimeout(timeout);
                OutputStream out = sock.getOutputStream();
                InputStream in = sock.getInputStream();

                // ASTM 帧封装
                byte[] content = msg.getMessageContent().getBytes(StandardCharsets.UTF_8);
                byte[] frame = new byte[content.length + 2];
                frame[0] = STX;
                System.arraycopy(content, 0, frame, 1, content.length);
                frame[frame.length - 1] = ETX;
                out.write(frame);
                out.flush();

                // 等待 ACK
                int response = in.read();
                if (response == ACK) {
                    log.info("ASTM sent OK: messageId={}, hospital={}",
                            msg.getMessageId(), msg.getHospitalCode());
                    return SendResult.ok();
                } else if (response == NAK) {
                    return SendResult.fail("LIS returned NAK");
                } else {
                    return SendResult.fail("Unexpected response: 0x" + Integer.toHexString(response));
                }
            }
        } catch (Exception e) {
            log.error("ASTM send failed: messageId={}, hospital={}, error={}",
                    msg.getMessageId(), msg.getHospitalCode(), e.getMessage());
            return SendResult.fail(e.getMessage());
        }
    }

    @Override
    public boolean testConnection(LisConfig config) {
        try {
            String channelCfgJson = config.getOutboundConfig();
            if (channelCfgJson == null) return false;
            @SuppressWarnings("unchecked")
            Map<String, Object> cfg = jsonMapper.readValue(channelCfgJson, Map.class);
            String host = (String) cfg.getOrDefault("host", "localhost");
            int port = cfg.get("port") instanceof Number n ? n.intValue() : 2000;
            try (Socket s = new Socket(host, port)) {
                s.setSoTimeout(3000);
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }
}
