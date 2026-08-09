package com.mlms.oes.server.gateway;

import com.mlms.oes.common.api.dto.AstResultDTO;
import com.mlms.oes.common.api.dto.UnifiedResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * VITEK 2 仪器接入完整流程集成测试。
 * <p>
 * 启动 Spring Boot 应用（dev 模式），等待网关启动 Vitek2Driver 监听 TCP 端口，
 * 然后模拟 VITEK 2 仪器连接并发送 ASTM 格式报文，
 * 验证完整的"TCP接收 → 分桢 → 解析 → 结果回调"管道。
 * </p>
 *
 * <h3>测试覆盖的流程：</h3>
 * <pre>
 * TCP Socket(模拟仪器) → TcpChannel → Vitek2Driver.messageListener
 *   → AstmSplitter.splitFrames()
 *     → Vitek2Parser.parse()
 *       → LoggingDataEventListener.onResultReceived()
 * </pre>
 *
 * @author MLMS Team
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("dev")
class Vitek2DemoTest {

    @Autowired
    private GatewayBootstrap bootstrap;

    /** VITEK 2 监听的 TCP 端口（与 application-dev.yml 保持一致） */
    private static final int VITEK2_PORT = 19001;

    private static final byte STX = 0x02;
    private static final byte ETX = 0x03;
    private static final byte ETB = 0x17;

    @BeforeEach
    void setUp() {
        // 清空上一轮测试的缓存结果，并等待驱动就绪
        waitForDriverReady();
        LoggingDataEventListener listener = bootstrap.getListener("VITEK2-LAB1-001");
        if (listener != null) {
            listener.reset();
        }
    }

    /**
     * 核心测试：模拟 VITEK 2 发送完整的 ASTM 报文（菌种鉴定 + 2 个药敏结果）。
     */
    @Test
    void shouldParseVitek2AstmMessage_OrganismAndTwoAntibiotics() throws Exception {
        // ---- 构造 ASTM 报文 ----
        StringBuilder astm = new StringBuilder();
        astm.append("O|1|20240001|||R|");                          // Order: 样本条码=20240001
        astm.append("\r");
        astm.append("R|1||ORGANISM|Escherichia coli|99.5|||R");   // Result: 菌种鉴定
        astm.append("\r");
        astm.append("R|2||AST|Ampicillin|2.0|S|||R");             // Result: 药敏1
        astm.append("\r");
        astm.append("R|3||AST|Ceftazidime|8.0|R|||R");            // Result: 药敏2
        astm.append("\r");

        byte[] frame = buildAstmFrame(astm.toString());
        System.out.println(">>> 发送 ASTM 报文 (" + frame.length + " bytes)");

        // ---- 模拟仪器通过 TCP 发送数据 ----
        sendTcpData(frame);

        // ---- 等待并验证解析结果 ----
        pollForResult(1);

        LoggingDataEventListener listener = bootstrap.getListener("VITEK2-LAB1-001");
        assertThat(listener).as("DataEventListener 应已注册").isNotNull();

        List<UnifiedResult> results = listener.getReceivedResults();
        assertThat(results).hasSize(1);

        UnifiedResult result = results.get(0);

        // 验证基础字段
        assertThat(result.getInstrumentId()).isEqualTo("VITEK2-LAB1-001");
        assertThat(result.getSampleBarcode()).isEqualTo("20240001");
        System.out.println("  ✅ instrumentId=" + result.getInstrumentId()
                + ", sampleBarcode=" + result.getSampleBarcode());

        // 验证菌种鉴定结果
        assertThat(result.getOrganismName()).isEqualTo("Escherichia coli");
        assertThat(result.getIdentificationPercent()).isEqualTo(99.5);
        System.out.println("  ✅ organism=" + result.getOrganismName()
                + " (" + result.getIdentificationPercent() + "%)");

        // 验证药敏结果
        assertThat(result.getAstResults()).hasSize(2);

        AstResultDTO ast0 = result.getAstResults().get(0);
        assertThat(ast0.getAntibioticName()).isEqualTo("Ampicillin");
        assertThat(ast0.getMicValue()).isEqualTo(2.0);
        assertThat(ast0.getMachineSIR()).isEqualTo("S");
        System.out.println("  ✅ " + ast0.getAntibioticName()
                + " MIC=" + ast0.getMicValue() + " SIR=" + ast0.getFinalSIR());

        AstResultDTO ast1 = result.getAstResults().get(1);
        assertThat(ast1.getAntibioticName()).isEqualTo("Ceftazidime");
        assertThat(ast1.getMicValue()).isEqualTo(8.0);
        assertThat(ast1.getMachineSIR()).isEqualTo("R");
        System.out.println("  ✅ " + ast1.getAntibioticName()
                + " MIC=" + ast1.getMicValue() + " SIR=" + ast1.getFinalSIR());

        // 验证原始报文和解析状态
        assertThat(result.getRawMessage()).isNotNull();
        assertThat(listener.getParseFailureCount()).isZero();
        System.out.println("  ✅ rawMessage 已保存, 无解析失败\n");
    }

    /**
     * 测试：接收分批发来的数据（跨 TCP read 拼帧）。
     * <p>第一批发送 STX + 部分数据，第二批发送剩余数据 + ETX。</p>
     */
    @Test
    void shouldReassembleSplitFrameAcrossMultipleReads() throws Exception {
        // 构造完整帧，然后拆成两半发送
        String full = "O|1|SPLIT-001|||R|\rR|1||ORGANISM|Klebsiella pneumoniae|98.0|||R|\r";
        byte[] fullFrame = buildAstmFrame(full);

        int splitPoint = fullFrame.length / 2;
        byte[] part1 = new byte[splitPoint];
        byte[] part2 = new byte[fullFrame.length - splitPoint];
        System.arraycopy(fullFrame, 0, part1, 0, splitPoint);
        System.arraycopy(fullFrame, splitPoint, part2, 0, fullFrame.length - splitPoint);

        try (Socket socket = new Socket("127.0.0.1", VITEK2_PORT);
             OutputStream out = socket.getOutputStream()) {

            // 发送前半部分（不完整帧）
            out.write(part1);
            out.flush();
            System.out.println(">>> 已发送前半部分 (" + part1.length + " bytes)");
            Thread.sleep(300);  // 等待服务端读取

            // 发送后半部分（补全帧）
            out.write(part2);
            out.flush();
            System.out.println(">>> 已发送后半部分 (" + part2.length + " bytes)");
        }

        // 验证：即使分开发送，也应正确拼帧
        pollForResult(1);

        LoggingDataEventListener listener = bootstrap.getListener("VITEK2-LAB1-001");
        UnifiedResult result = listener.getReceivedResults().get(0);
        assertThat(result.getSampleBarcode()).isEqualTo("SPLIT-001");
        assertThat(result.getOrganismName()).isEqualTo("Klebsiella pneumoniae");
        System.out.println("  ✅ 跨 TCP read 拼帧成功, organism=" + result.getOrganismName());
    }

    /**
     * 测试：单次 TCP 读取中包含多个完整帧（ETB 中间帧 + ETX 末帧）。
     */
    @Test
    void shouldHandleMultipleFramesInSingleRead() throws Exception {
        // 构造两帧：第一帧用 ETB(0x17) 结尾（中间帧），第二帧用 ETX(0x03) 结尾（末帧）
        byte[] frame1 = buildFrame("O|1|MULTI-01|||R|\rR|1||ORGANISM|Pseudomonas aeruginosa|97.0|||R|\r", ETB);
        byte[] frame2 = buildFrame("O|1|MULTI-02|||R|\rR|1||ORGANISM|Staphylococcus aureus|99.0|||R|\r", ETX);

        byte[] combined = new byte[frame1.length + frame2.length];
        System.arraycopy(frame1, 0, combined, 0, frame1.length);
        System.arraycopy(frame2, 0, combined, frame1.length, frame2.length);

        sendTcpData(combined);

        // 验证应该收到 2 条结果
        pollForResult(2);

        LoggingDataEventListener listener = bootstrap.getListener("VITEK2-LAB1-001");
        assertThat(listener.getReceivedResults()).hasSize(2);
        assertThat(listener.getReceivedResults().get(0).getSampleBarcode()).isEqualTo("MULTI-01");
        assertThat(listener.getReceivedResults().get(1).getSampleBarcode()).isEqualTo("MULTI-02");
        System.out.println("  ✅ 单次接收多帧, 共解析出 " + listener.getResultCount() + " 条结果");
    }

    // ==================== 辅助方法 ====================

    /** 通过 TCP 发送数据到 VITEK 2 端口 */
    private void sendTcpData(byte[] data) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", VITEK2_PORT);
             OutputStream out = socket.getOutputStream()) {
            out.write(data);
            out.flush();
            System.out.println(">>> 报文已发送到端口 " + VITEK2_PORT + " (" + data.length + " bytes)");
        }
        // 等待服务端处理完成
        Thread.sleep(300);
    }

    /** 轮询等待直到收到指定数量的结果（最长 15 秒） */
    private void pollForResult(int expectedCount) throws InterruptedException {
        LoggingDataEventListener listener = bootstrap.getListener("VITEK2-LAB1-001");
        for (int i = 0; i < 150; i++) {
            if (listener != null && listener.getResultCount() >= expectedCount) {
                return;
            }
            Thread.sleep(100);
        }
    }

    /** 等待驱动启动（最长 15 秒） */
    private void waitForDriverReady() {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            if (bootstrap.getContext("VITEK2-LAB1-001") != null) {
                return;
            }
            try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
        }
        throw new IllegalStateException("驱动未能在 15 秒内启动");
    }

    /** 构造以 ETX 结尾的完整 ASTM 帧 */
    private static byte[] buildAstmFrame(String content) {
        return buildFrame(content, ETX);
    }

    /** 构造 ASTM 帧：STX + 内容 + 结束符 */
    private static byte[] buildFrame(String content, byte endByte) {
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
        byte[] frame = new byte[1 + contentBytes.length + 1];
        frame[0] = STX;
        System.arraycopy(contentBytes, 0, frame, 1, contentBytes.length);
        frame[frame.length - 1] = endByte;
        return frame;
    }
}
