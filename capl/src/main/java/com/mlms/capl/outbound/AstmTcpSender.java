package com.mlms.capl.outbound;

import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * ASTM TCP 发送器（通讯层侧）。
 */
@Slf4j
public class AstmTcpSender implements LisOutboundSender {

    private static final byte STX = 0x02;
    private static final byte ETX = 0x03;
    private static final byte ACK = 0x06;
    private static final byte NAK = 0x15;

    @Override
    public String getChannelType() { return "ASTM"; }

    @Override
    public SendResult send(Map<String, Object> channelConfig, String messageContent, String hospitalCode) {
        try {
            String host = (String) channelConfig.getOrDefault("host", "localhost");
            int port = channelConfig.get("port") instanceof Number n ? n.intValue() : 2000;
            int ackTimeoutSec = channelConfig.get("ackTimeoutSec") instanceof Number n ? n.intValue() : 30;

            try (Socket sock = new Socket(host, port)) {
                sock.setSoTimeout(ackTimeoutSec * 1000);
                OutputStream out = sock.getOutputStream();
                InputStream in = sock.getInputStream();

                byte[] content = messageContent.getBytes(StandardCharsets.UTF_8);
                byte[] frame = new byte[content.length + 2];
                frame[0] = STX;
                System.arraycopy(content, 0, frame, 1, content.length);
                frame[frame.length - 1] = ETX;
                out.write(frame);
                out.flush();

                int response = in.read();
                if (response == ACK) {
                    log.info("ASTM sent OK: hospital={}", hospitalCode);
                    return SendResult.ok();
                } else if (response == NAK) {
                    return SendResult.fail("LIS returned NAK");
                } else {
                    return SendResult.fail("Unexpected response: 0x" + Integer.toHexString(response));
                }
            }
        } catch (Exception e) {
            log.error("ASTM send failed: hospital={}, error={}", hospitalCode, e.getMessage());
            return SendResult.fail(e.getMessage());
        }
    }
}
