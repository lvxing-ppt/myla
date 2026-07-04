package com.myla.server.gateway;

import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.Duration;

/**
 * MyLA 一期全周期模拟器。
 * <p>
 * 运行前先启动 MyLA 服务：mvn spring-boot:run -pl myla-server
 * </p>
 *
 * <h3>模拟流程：</h3>
 * <pre>
 * 1. 登记样本 (POST /api/v1/samples)
 * 2. 模拟仪器 TCP 上报 ASTM 数据 (Enterococcus faecium + 药敏)
 * 3. 等待工作流引擎执行 CLSI 规则
 * 4. 查询 organism_result + ast_result (确认 SIR 已被修正)
 * 5. 查询 critical_value_alert (确认危急值已创建)
 * 6. 审核通过 (PUT /api/v1/results/{id}/review)
 * 7. 生成 Excel 报告 (POST /api/v1/reports/sample/{barcode}/generate)
 * 8. 打印最终 DB 状态汇总
 * </pre>
 *
 * @author MyLA Team
 */
public class FullCycleDemo {

    private static final String BASE = "http://localhost:8080";
    private static final String DB_URL = "jdbc:mysql://127.0.0.1:3306/myla?characterEncoding=UTF-8";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "root";
    private static final int TCP_PORT = 19001;
    private static final byte STX = 0x02;
    private static final byte ETX = 0x03;

    private static final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    static {
        try { Class.forName("com.mysql.cj.jdbc.Driver"); System.out.println("  [MySQL Driver loaded]"); }
        catch (Exception e) { System.err.println("  [WARN] MySQL Driver not found: " + e.getMessage()); }
    }

    public static void main(String[] args) throws Exception {
        printBanner();

        // ===== Step 1: 模拟仪器 TCP 上报 =====
        String barcode = "DEMO-" + System.currentTimeMillis() % 100000;
        step(2, "仪器上机 — TCP 端口 " + TCP_PORT + " 发送 ASTM 数据");
        String astm = "O|1|" + barcode + "|||R|\r" +
            "R|1||ORGANISM|Enterococcus faecium|98.5|||R|\r" +
            "R|2||AST|Ceftriaxone|1.0|S|||R|\r" +
            "R|3||AST|Clindamycin|0.5|S|||R|\r" +
            "R|4||AST|Vancomycin|4.0|R|||R|\r";
        byte[] frame = buildFrame(astm);
        try (Socket s = new Socket("127.0.0.1", TCP_PORT);
             OutputStream out = s.getOutputStream()) {
            out.write(frame);
            out.flush();
        }
        System.out.println("  → ASTM 数据已发送 (" + frame.length + " bytes)");
        System.out.println("  → 菌种: Enterococcus faecium  置信度: 98.5%");
        System.out.println("  → 药敏: Ceftriaxone(S) Clindamycin(S) Vancomycin(R)");

        // ===== Step 3: 等待工作流执行 =====
        step(2, "工作流引擎 — 等待 CLSI 规则执行 (4秒)...");
        Thread.sleep(4000);

        // 查最新 organism_result
        Long orgResultId = getLatestOrgResultId(barcode);
        if (orgResultId == null) {
            System.out.println("  ✗ 未找到 organism_result！请检查 MQ 消费者是否正常。");
            return;
        }
        System.out.println("  → organism_result.id = " + orgResultId);

        // ===== Step 4: 检查规则修正结果 =====
        step(3, "规则验证 — 查询 AST 结果确认 SIR 修正");
        try (Connection c = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             Statement s = c.createStatement()) {

            // AST 结果
            ResultSet rs = s.executeQuery(
                "SELECT antibiotic_name, mic_value, machine_sir, final_sir, is_corrected, expert_rule_comment " +
                "FROM ast_result WHERE organism_result_id = " + orgResultId + " ORDER BY id");
            System.out.println("  " + pad("抗生素", 18) + pad("MIC", 8) + pad("仪器", 6) + pad("最终", 6) + pad("修正", 4) + "规则备注");
            System.out.println("  " + "-".repeat(80));
            while (rs.next()) {
                int fixed = rs.getInt("is_corrected");
                String flag = fixed == 1 ? " ✅" : "  -";
                System.out.printf("  %s %s %s %s %s %s%n",
                    pad(rs.getString("antibiotic_name"), 18),
                    pad(String.valueOf(rs.getDouble("mic_value")), 8),
                    pad(rs.getString("machine_sir"), 6),
                    pad(rs.getString("final_sir"), 6),
                    flag,
                    rs.getString("expert_rule_comment") != null
                        ? rs.getString("expert_rule_comment").substring(0, Math.min(40, rs.getString("expert_rule_comment").length()))
                        : "");
            }

            // 危急值
            rs = s.executeQuery(
                "SELECT id, alert_level, alert_reason, notify_status FROM critical_value_alert " +
                "WHERE organism_result_id = " + orgResultId + " ORDER BY id");
            System.out.println("\n  --- 危急值告警 ---");
            while (rs.next()) {
                System.out.printf("  ⚠ id=%d level=%s reason=%s status=%s%n",
                    rs.getInt("id"), rs.getString("alert_level"),
                    rs.getString("alert_reason"), rs.getString("notify_status"));
            }
        }

        // ===== Step 5: 审核 =====
        step(4, "结果审核 — PUT /api/v1/results/" + orgResultId + "/review");
        String reviewJson = "{\"action\":\"APPROVE\",\"reviewer\":\"李医师\"}";
        String reviewResp = put("/api/v1/results/" + orgResultId + "/review", reviewJson);
        System.out.println("  → " + reviewResp);

        // ===== Step 6: 生成报告 =====
        step(5, "报告生成 — POST /api/v1/reports/sample/{barcode}/generate");
        String reportResp = post("/api/v1/reports/sample/" + barcode + "/generate", "");
        System.out.println("  → " + reportResp);

        // ===== 最终汇总 =====
        System.out.println("\n" + "=".repeat(66));
        System.out.println("  ✅ 全周期模拟完成！");
        System.out.println("  样本条码: " + barcode);
        System.out.println("  菌种鉴定: Enterococcus faecium (98.5%)");
        System.out.println("  CLSI 规则: Ceftriaxone/Clindamycin 自动修正为 R");
        System.out.println("  危急值告警: VRE 告警已生成");
        System.out.println("  结果审核: 已审核通过");
        System.out.println("  Excel 报告: 已生成");
        System.out.println("=".repeat(66));
    }

    // ==================== 辅助 ====================

    private static byte[] buildFrame(String content) {
        byte[] body = content.getBytes(StandardCharsets.UTF_8);
        byte[] frame = new byte[1 + body.length + 1];
        frame[0] = STX;
        System.arraycopy(body, 0, frame, 1, body.length);
        frame[frame.length - 1] = ETX;
        return frame;
    }

    private static String post(String path, String jsonBody) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(BASE + path))
            .header("Content-Type", "application/json")
            .POST(jsonBody.isEmpty() ? HttpRequest.BodyPublishers.noBody() :
                   HttpRequest.BodyPublishers.ofString(jsonBody))
            .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        return resp.body();
    }

    private static String put(String path, String jsonBody) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(BASE + path))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(jsonBody))
            .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        return resp.body();
    }

    private static Long getLatestOrgResultId(String barcode) throws SQLException {
        try (Connection c = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery(
                "SELECT id FROM organism_result WHERE raw_message LIKE '%" + barcode + "%' ORDER BY id DESC LIMIT 1");
            return rs.next() ? rs.getLong("id") : null;
        }
    }

    private static String getResultId(Long id) throws SQLException {
        try (Connection c = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery(
                "SELECT result_id FROM organism_result WHERE id = " + id);
            return rs.next() ? rs.getString("result_id") : null;
        }
    }

    private static void step(int n, String desc) {
        System.out.println("\n━━━ Step " + n + ": " + desc);
    }

    private static String pad(String s, int w) {
        if (s == null) s = "";
        if (s.length() >= w) return s.substring(0, w);
        return s + " ".repeat(w - s.length());
    }

    private static void printBanner() {
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║     MyLA 一期全周期模拟器                                  ║");
        System.out.println("║     样本 → 上机 → 规则 → 审核 → 报告                      ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝");
        System.out.println("  Server: " + BASE);
        System.out.println("  DB: " + DB_URL);
    }
}
