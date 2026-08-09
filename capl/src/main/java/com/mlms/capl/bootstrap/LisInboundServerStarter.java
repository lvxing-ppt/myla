package com.mlms.capl.bootstrap;

import com.mlms.capl.config.LisCommProperties.LisInboundProps;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.DisposableBean;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * LIS 入站 TCP MLLP 服务器启动器。
 * <p>
 * 从 YAML 配置读取医院→端口映射，为每个医院启动独立监听线程。
 * 收到 HL7 消息后轻量解析 MSH-9/MSH-10，包装 JSON 发到 lis.inbound 队列。
 * </p>
 */
@Slf4j
@RequiredArgsConstructor
public class LisInboundServerStarter implements DisposableBean {

    private final RabbitTemplate rabbitTemplate;

    private final Map<String, ServerSocket> servers = new ConcurrentHashMap<>();
    private final Map<String, Thread> threads = new ConcurrentHashMap<>();

    private static final byte VT = 0x0B;
    private static final byte FS = 0x1C;
    private static final byte CR = 0x0D;

    public void startAll(List<LisInboundProps> inbounds) {
        if (inbounds == null || inbounds.isEmpty()) {
            log.info("No LIS inbound ports configured");
            return;
        }
        log.info("Starting {} LIS inbound listener(s)", inbounds.size());
        for (LisInboundProps cfg : inbounds) {
            startForHospital(cfg.getHospitalCode(), cfg.getPort());
        }
    }

    private void startForHospital(String hospitalCode, int port) {
        try {
            ServerSocket ss = new ServerSocket(port);
            servers.put(hospitalCode, ss);

            AtomicBoolean running = new AtomicBoolean(true);
            Thread t = new Thread(() -> {
                log.info("LIS inbound listener started: hospital={}, port={}", hospitalCode, port);
                while (running.get()) {
                    try {
                        Socket client = ss.accept();
                        client.setSoTimeout(30_000);
                        new Thread(() -> handleConnection(client, hospitalCode)).start();
                    } catch (IOException e) {
                        if (running.get()) {
                            log.error("Accept error for hospital={}: {}", hospitalCode, e.getMessage());
                        }
                    }
                }
            }, "lis-inbound-" + hospitalCode);
            t.setDaemon(true);
            t.start();
            threads.put(hospitalCode, t);

        } catch (Exception e) {
            log.error("Failed to start LIS inbound for hospital={}, port={}: {}", hospitalCode, port, e.getMessage());
        }
    }

    private void handleConnection(Socket client, String hospitalCode) {
        try (client; InputStream in = client.getInputStream();
             OutputStream out = client.getOutputStream()) {

            byte[] buf = in.readAllBytes();
            if (buf.length == 0) return;

            int start = -1;
            for (int i = 0; i < buf.length - 1; i++) {
                if (buf[i] == VT) start = i;
                if (start >= 0 && buf[i] == FS && buf[i + 1] == CR) {
                    int msgStart = start + 1;
                    int msgEnd = i;
                    byte[] hl7Frame = new byte[msgEnd - msgStart + 1];
                    System.arraycopy(buf, msgStart, hl7Frame, 0, hl7Frame.length);
                    String hl7 = new String(hl7Frame, StandardCharsets.UTF_8).trim();

                    String msgType = identifyMsgType(hl7);
                    String msgControlId = extractMsgControlId(hl7);

                    try {
                        // 包装 JSON → 发 MQ
                        Map<String, String> msg = new HashMap<>();
                        msg.put("hospitalCode", hospitalCode);
                        msg.put("messageType", msgType);
                        msg.put("messageControlId", msgControlId);
                        msg.put("rawMessage", hl7);

                        rabbitTemplate.convertAndSend("myla.lis", "lis.inbound", msg);
                        log.info("[LIS-IN] published to lis.inbound: hospital={}, type={}, msgId={}",
                                hospitalCode, msgType, msgControlId);

                        sendAck(out, hl7, "AA", "OK");
                    } catch (Exception e) {
                        log.error("Failed to process HL7 from {}: {}", hospitalCode, e.getMessage());
                        sendAck(out, hl7, "AR", e.getMessage());
                    }

                    start = -1;
                }
            }
        } catch (IOException e) {
            log.debug("Connection closed for hospital={}: {}", hospitalCode, e.getMessage());
        }
    }

    private String identifyMsgType(String hl7) {
        try {
            String[] segs = hl7.split("\r|\n");
            if (segs.length > 0 && segs[0].startsWith("MSH")) {
                String[] fields = segs[0].split("\\|");
                if (fields.length > 9) return fields[9];
            }
        } catch (Exception ignored) {}
        return "UNKNOWN";
    }

    private String extractMsgControlId(String hl7) {
        try {
            String[] segs = hl7.split("\r|\n");
            if (segs.length > 0 && segs[0].startsWith("MSH")) {
                String[] fields = segs[0].split("\\|");
                if (fields.length > 10) return fields[10];
            }
        } catch (Exception ignored) {}
        return "";
    }

    private void sendAck(OutputStream out, String request, String ackCode, String text) throws IOException {
        String msh3 = "", msh4 = "", msh5 = "", msh6 = "", msh10 = "";
        try {
            String[] segs = request.split("\r|\n");
            if (segs.length > 0 && segs[0].startsWith("MSH")) {
                String[] f = segs[0].split("\\|");
                if (f.length > 3) msh3 = f[3];
                if (f.length > 4) msh4 = f[4];
                if (f.length > 5) msh5 = f[5];
                if (f.length > 6) msh6 = f[6];
                if (f.length > 10) msh10 = f[10];
            }
        } catch (Exception ignored) {}

        String ack = String.format(
                "MSH|^~\\&|%s|%s|%s|%s|%s||ACK|%s|P|2.5\r" +
                "MSA|%s|%s|%s\r",
                msh5, msh6, msh3, msh4,
                java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")),
                UUID.randomUUID().toString().replace("-", ""),
                ackCode, msh10, text);

        byte[] ackBytes = ack.getBytes(StandardCharsets.UTF_8);
        byte[] mllpFrame = new byte[ackBytes.length + 3];
        mllpFrame[0] = VT;
        System.arraycopy(ackBytes, 0, mllpFrame, 1, ackBytes.length);
        mllpFrame[ackBytes.length + 1] = FS;
        mllpFrame[ackBytes.length + 2] = CR;
        out.write(mllpFrame);
        out.flush();
    }

    @Override
    public void destroy() {
        log.info("Shutting down LIS inbound servers...");
        servers.forEach((code, ss) -> { try { ss.close(); } catch (IOException ignored) {} });
        threads.forEach((code, t) -> t.interrupt());
    }
}
