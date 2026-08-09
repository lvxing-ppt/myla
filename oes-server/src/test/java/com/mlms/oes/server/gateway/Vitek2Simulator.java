package com.mlms.oes.server.gateway;

import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * VITEK 2 仪器数据模拟器。
 * <p>
 * 模拟一台 VITEK 2 仪器通过 TCP 向网关发送 ASTM 格式的检验数据。
 * 运行前需先启动 MylaApplication（spring-boot:run）。
 * </p>
 *
 * <h3>用法：</h3>
 * <pre>
 * # 1. 终端1：启动服务
 * mvn spring-boot:run -pl oes-server
 *
 * # 2. 终端2：运行模拟器
 * mvn exec:java -pl oes-server -Dexec.classpathScope=test \
 *     -Dexec.mainClass=com.mlms.oes.server.gateway.Vitek2Simulator
 * </pre>
 */
public class Vitek2Simulator {

    private static final String HOST = "127.0.0.1";
    private static final int PORT = 19001;
    private static final byte STX = 0x02;
    private static final byte ETX = 0x03;
    private static final byte ETB = 0x17;

    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║   VITEK 2 数据模拟器 v1.0                 ║");
        System.out.println("║   目标: " + HOST + ":" + PORT + "                       ║");
        System.out.println("╚══════════════════════════════════════════╝\n");

        // ---- 场景1：单条完整报文 ----
        System.out.println(">>> 场景1：发送菌种鉴定 + 药敏结果");
        sendFrame(scenario1());
        sleep(1000);

        // ---- 场景2：药敏结果 ----
        System.out.println("\n>>> 场景2：发送纯药敏结果");
        sendFrame(scenario2());
        sleep(1000);

        // ---- 场景3：半包拼帧 ----
        System.out.println("\n>>> 场景3：模拟网络粘包（分两次发送，中间间隔300ms）");
        byte[] full = buildFrame(
                "O|1|SPLIT-0001|||R|\rR|1||ORGANISM|Klebsiella pneumoniae|98.0|||R|\r", ETX);
        int mid = full.length / 2;
        sendRaw(full, 0, mid);
        System.out.println("  → 前半部分已发送 (" + mid + " bytes)...");
        sleep(300);
        sendRaw(full, mid, full.length - mid);
        System.out.println("  → 后半部分已发送 (" + (full.length - mid) + " bytes)");
        sleep(1000);

        System.out.println("\n>>> 全部数据发送完毕！查看 oes-server 终端日志确认解析结果。");
    }

    // ==================== 场景数据 ====================

    /** 场景1：菌种鉴定 + 2个药敏 */
    private static byte[] scenario1() {
        StringBuilder sb = new StringBuilder();
        sb.append("O|1|20240001|||R|");
        sb.append("\r");
        sb.append("R|1||ORGANISM|Escherichia coli|99.5|||R");
        sb.append("\r");
        sb.append("R|2||AST|Ampicillin|2.0|S|||R");
        sb.append("\r");
        sb.append("R|3||AST|Ceftazidime|8.0|R|||R");
        sb.append("\r");
        return buildFrame(sb.toString(), ETX);
    }

    /** 场景2：仅药敏 */
    private static byte[] scenario2() {
        StringBuilder sb = new StringBuilder();
        sb.append("O|1|20240002|||R|");
        sb.append("\r");
        sb.append("R|1||AST|Vancomycin|1.0|S|||R");
        sb.append("\r");
        sb.append("R|2||AST|Gentamicin|4.0|R|||R");
        sb.append("\r");
        return buildFrame(sb.toString(), ETX);
    }

    // ==================== 发送工具 ====================

    private static void sendFrame(byte[] data) throws Exception {
        try (Socket s = new Socket(HOST, PORT); OutputStream out = s.getOutputStream()) {
            out.write(data);
            out.flush();
        }
        System.out.println("  ✅ 已发送 " + data.length + " bytes");
        String preview = new String(data, StandardCharsets.UTF_8)
                .replace("\r", "\\r\n").replace("", "<STX>").replace("", "<ETX>");
        System.out.println("  📄 " + preview);
    }

    private static void sendRaw(byte[] data, int offset, int len) throws Exception {
        try (Socket s = new Socket(HOST, PORT); OutputStream out = s.getOutputStream()) {
            out.write(data, offset, len);
            out.flush();
        }
    }

    private static byte[] buildFrame(String content, byte endByte) {
        byte[] body = content.getBytes(StandardCharsets.UTF_8);
        byte[] frame = new byte[1 + body.length + 1];
        frame[0] = STX;
        System.arraycopy(body, 0, frame, 1, body.length);
        frame[frame.length - 1] = endByte;
        return frame;
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
