package com.myla.lis.inbound;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myla.lis.mapper.LisConfigMapper;
import com.myla.lis.entity.LisConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * LIS 入站 TCP MLLP 服务器。
 * <p>
 * 为每个启用 HL7 入站的医院启动一个独立的 TCP 监听端口。
 * 收到 MLLP 帧（VT...FS CR）后解析 HL7 消息，调用 LisInboundService，
 * 然后返回 HL7 ACK（MSA^AA 或 MSA^AR）。
 * </p>
 *
 * <h3>生命周期：</h3>
 * <ul>
 *   <li>ApplicationReadyEvent → 查询 lis_config，为每个 enabled HL7 医院启动监听线程</li>
 *   <li>destroy() → 关闭所有监听线程和 ServerSocket</li>
 * </ul>
 *
 * <h3>MLLP 帧格式：</h3>
 * <pre>VT(0x0B) [HL7 Message] FS(0x1C) CR(0x0D)</pre>
 */
@Slf4j
@RequiredArgsConstructor
public class LisInboundServer implements DisposableBean {

    private final LisConfigMapper configMapper;
    private final LisInboundService inboundService;

    private final Map<String, ServerSocket> servers = new ConcurrentHashMap<>();
    private final Map<String, Thread> threads = new ConcurrentHashMap<>();
    private static final ObjectMapper jsonMapper = new ObjectMapper();

    // MLLP 帧分隔符
    private static final byte VT = 0x0B;
    private static final byte FS = 0x1C;
    private static final byte CR = 0x0D;

    /**
     * 应用启动后自动开启所有入站监听。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void startAll() {
        List<LisConfig> configs = configMapper.selectEnabledInbound();
        log.info("Starting LIS inbound servers for {} hospital(s)", configs.size());

        for (LisConfig cfg : configs) {
            startForHospital(cfg);
        }
    }

    /**
     * 为指定医院启动 MLLP 监听。
     */
    private void startForHospital(LisConfig cfg) {
        try {
            int port = extractPort(cfg.getInboundConfig());
            ServerSocket ss = new ServerSocket(port);
            servers.put(cfg.getHospitalCode(), ss);

            AtomicBoolean running = new AtomicBoolean(true);
            Thread t = new Thread(() -> {
                log.info("LIS inbound listener started: hospital={}, port={}", cfg.getHospitalCode(), port);
                while (running.get()) {
                    try {
                        Socket client = ss.accept();
                        client.setSoTimeout(30_000); // read timeout 30s
                        new Thread(() -> handleConnection(client, cfg)).start();
                    } catch (IOException e) {
                        if (running.get()) {
                            log.error("Accept error for hospital={}: {}", cfg.getHospitalCode(), e.getMessage());
                        }
                    }
                }
            }, "lis-inbound-" + cfg.getHospitalCode());
            t.setDaemon(true);
            t.start();
            threads.put(cfg.getHospitalCode(), t);

        } catch (Exception e) {
            log.error("Failed to start LIS inbound for hospital={}: {}", cfg.getHospitalCode(), e.getMessage());
        }
    }

    /**
     * 处理单个 TCP 连接 — 读取 MLLP 帧，处理，回 ACK。
     */
    private void handleConnection(Socket client, LisConfig cfg) {
        try (client; InputStream in = client.getInputStream();
             OutputStream out = client.getOutputStream()) {

            // 读取直到流结束
            byte[] buf = in.readAllBytes();
            if (buf.length == 0) return;

            // MLLP 帧切分：找 VT 开头、FS+CR 结尾
            int start = -1;
            for (int i = 0; i < buf.length - 1; i++) {
                if (buf[i] == VT) start = i;
                if (start >= 0 && buf[i] == FS && buf[i + 1] == CR) {
                    // 提取 HL7 消息（VT 之后、FS 之前）
                    int msgStart = start + 1;
                    int msgEnd = i;
                    byte[] hl7Frame = new byte[msgEnd - msgStart + 1];
                    System.arraycopy(buf, msgStart, hl7Frame, 0, hl7Frame.length);
                    String hl7 = new String(hl7Frame, StandardCharsets.UTF_8).trim();

                    // 识别消息类型
                    String msgType = identifyMsgType(hl7);

                    try {
                        // 处理
                        if (msgType.contains("ORM") || msgType.contains("O01")) {
                            inboundService.receiveOrder(cfg.getHospitalCode(), hl7.getBytes(StandardCharsets.UTF_8), "HL7");
                        } else if (msgType.contains("ADT")) {
                            inboundService.receivePatientUpdate(cfg.getHospitalCode(), hl7.getBytes(StandardCharsets.UTF_8));
                        }

                        // 回 ACK
                        sendAck(out, hl7, "AA", "OK");
                    } catch (Exception e) {
                        log.error("Failed to process HL7 from {}: {}", cfg.getHospitalCode(), e.getMessage());
                        sendAck(out, hl7, "AR", e.getMessage());
                    }

                    start = -1; // 重置，继续下一帧
                }
            }
        } catch (IOException e) {
            log.debug("Connection closed for hospital={}: {}", cfg.getHospitalCode(), e.getMessage());
        }
    }

    /**
     * 从 MSH-9 识别消息类型。
     */
    private String identifyMsgType(String hl7) {
        try {
            String[] segs = hl7.split("\r|\n");
            if (segs.length > 0 && segs[0].startsWith("MSH")) {
                String[] fields = segs[0].split("\\|");
                if (fields.length > 9) return fields[9]; // MSH-9
            }
        } catch (Exception ignored) {}
        return "UNKNOWN";
    }

    /**
     * 构造并发送 HL7 ACK 消息。
     */
    private void sendAck(OutputStream out, String request, String ackCode, String text) throws IOException {
        // 从请求中提取 MSH 字段用于构造 ACK
        String msh3 = ""; // sending application
        String msh4 = ""; // sending facility
        String msh5 = ""; // receiving application
        String msh6 = ""; // receiving facility
        String msh10 = ""; // message control ID

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
                java.util.UUID.randomUUID().toString().replace("-", ""),
                ackCode, msh10, text);

        byte[] ackBytes = ack.getBytes(StandardCharsets.UTF_8);
        // MLLP 封装
        byte[] mllpFrame = new byte[ackBytes.length + 3];
        mllpFrame[0] = VT;
        System.arraycopy(ackBytes, 0, mllpFrame, 1, ackBytes.length);
        mllpFrame[ackBytes.length + 1] = FS;
        mllpFrame[ackBytes.length + 2] = CR;
        out.write(mllpFrame);
        out.flush();
    }

    /**
     * 从 inbound_config JSON 提取端口号。
     */
    private int extractPort(String channelConfig) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = jsonMapper.readValue(channelConfig, Map.class);
            Object portObj = map.get("port");
            if (portObj instanceof Number n) return n.intValue();
        } catch (Exception ignored) {}
        return 2575; // 默认 HL7 MLLP 端口
    }

    /**
     * 停止所有监听。
     */
    @Override
    public void destroy() {
        log.info("Shutting down LIS inbound servers...");
        servers.forEach((code, ss) -> {
            try { ss.close(); } catch (IOException ignored) {}
        });
        threads.forEach((code, t) -> t.interrupt());
    }
}
