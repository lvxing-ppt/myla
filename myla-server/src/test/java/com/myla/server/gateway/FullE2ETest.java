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
 * MyLA 全流程端到端测试。
 * 运行前先启动服务: mvn spring-boot:run -pl myla-server
 */
public class FullE2ETest {

    private static final String BASE = "http://localhost:8080";
    private static final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public static void main(String[] args) throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        String barcode = "E2E-" + System.currentTimeMillis() % 100000;
        println("=== MyLA Full E2E Test ===");
        println("Barcode: " + barcode);

        // === Step 1: Login ===
        println("\n[1/7] Login...");
        String token = postJson("/api/v1/auth/login", "{\"username\":\"admin\",\"password\":\"admin123\"}");
        println("  Token: " + token.substring(0, Math.min(60, token.length())) + "...");

        // === Step 2: Dashboard Stats ===
        println("\n[2/7] Dashboard Stats...");
        get("/api/v1/dashboard/stats");

        // === Step 3: Register Sample ===
        println("\n[3/7] Register Sample...");
        postJson("/api/v1/samples",
            "{\"barcode\":\"" + barcode + "\",\"patientName\":\"E2E-Test\",\"specimenType\":\"BLOOD\",\"sourceSystem\":\"MANUAL\"}");

        // === Step 4: Send ASTM via TCP ===
        println("\n[4/7] Send ASTM data via TCP...");
        String astm = "O|1|" + barcode + "|||R|\r" +
            "R|1||ORGANISM|Klebsiella pneumoniae|98.0|||R|\r" +
            "R|2||AST|Ampicillin|0.5|S|||R|\r" +
            "R|3||AST|Ceftazidime|4.0|S|||R|\r";
        byte[] frame = buildFrame(astm);
        try (Socket s = new Socket("127.0.0.1", 19001); OutputStream o = s.getOutputStream()) {
            o.write(frame); o.flush();
        }
        println("  Sent " + frame.length + " bytes, waiting for processing...");
        Thread.sleep(4000);

        // === Step 5: Check Results in DB ===
        println("\n[5/7] Check DB Results...");
        try (Connection c = DriverManager.getConnection(
                "jdbc:mysql://127.0.0.1:3306/myla?characterEncoding=UTF-8", "root", "root");
             Statement s = c.createStatement()) {

            // Raw message
            ResultSet rs = s.executeQuery("SELECT id,message_type,parse_status FROM raw_message WHERE raw_content LIKE '%" + barcode + "%' ORDER BY id DESC LIMIT 1");
            if (rs.next()) println("  raw_message: id=" + rs.getLong(1) + " type=" + rs.getString(2) + " status=" + rs.getString(3));
            else println("  raw_message: MISSING");

            // Organism result
            rs = s.executeQuery("SELECT id,organism_name,identification_percent,review_status,sample_id FROM organism_result WHERE raw_message LIKE '%" + barcode + "%' ORDER BY id DESC LIMIT 1");
            if (rs.next()) {
                long orgId = rs.getLong(1);
                long sampleId = rs.getLong(5);
                println("  organism_result: id=" + orgId + " organism=" + rs.getString(2) + " conf=" + rs.getDouble(3) + "% status=" + rs.getString(4) + " sample_id=" + sampleId);
                println("  sample association: " + (sampleId > 0 ? "LINKED" : "NOT LINKED"));

                // AST results
                rs = s.executeQuery("SELECT antibiotic_name,mic_value,machine_sir,final_sir,is_corrected,expert_rule_comment FROM ast_result WHERE organism_result_id=" + orgId + " ORDER BY id");
                println("  AST Results:");
                while (rs.next()) {
                    String fixed = rs.getInt(5) == 1 ? " [CLSI CORRECTED]" : "";
                    println("    " + rs.getString(1) + " MIC=" + rs.getDouble(2) + " machine=" + rs.getString(3) + " final=" + rs.getString(4) + fixed);
                }

                // Critical alerts
                rs = s.executeQuery("SELECT id,alert_level,alert_reason,notify_status FROM critical_value_alert WHERE organism_result_id=" + orgId);
                if (rs.next()) println("  Critical Alert: id=" + rs.getLong(1) + " level=" + rs.getString(2) + " status=" + rs.getString(4));

                // === Step 6: Review ===
                println("\n[6/7] Three-level Review...");
                // Level 1: Tech review
                review(orgId, "APPROVE", "TechZhang", "ROLE_TECHNICIAN");
                rs = s.executeQuery("SELECT review_status,tech_reviewed_by FROM organism_result WHERE id=" + orgId);
                rs.next();
                println("  L1 Tech Review: " + rs.getString(1) + " by " + rs.getString(2));

                // Level 2: Clinical review
                review(orgId, "APPROVE", "DrLi", "ROLE_REVIEWER");
                rs = s.executeQuery("SELECT review_status,clinical_reviewed_by FROM organism_result WHERE id=" + orgId);
                rs.next();
                println("  L2 Clinical Review: " + rs.getString(1) + " by " + rs.getString(2));

                // Level 3: Director review
                review(orgId, "APPROVE", "DirectorWang", "ROLE_DIRECTOR");
                rs = s.executeQuery("SELECT review_status,reviewed_by FROM organism_result WHERE id=" + orgId);
                rs.next();
                println("  L3 Director Review: " + rs.getString(1) + " by " + rs.getString(2));
            } else {
                println("  organism_result: MISSING");
            }
        }

        // === Step 7: Report ===
        println("\n[7/7] Generate Report...");
        postJson("/api/v1/reports/sample/" + barcode + "/generate", "");
        get("/api/v1/reports/sample/" + barcode + "/excel");

        println("\n========================================");
        println("  E2E TEST COMPLETE");
        println("========================================");
    }

    private static void review(long id, String action, String reviewer, String role) throws Exception {
        String body = "{\"action\":\"" + action + "\",\"reviewer\":\"" + reviewer + "\"}";
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(BASE + "/api/v1/results/" + id + "/review"))
            .header("Content-Type", "application/json")
            .header("X-Reviewer", reviewer)
            .header("X-Role", role)
            .PUT(HttpRequest.BodyPublishers.ofString(body)).build();
        http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private static String postJson(String path, String json) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(BASE + path))
            .header("Content-Type", "application/json")
            .POST(json.isEmpty() ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json))
            .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        String preview = resp.body().length() > 150 ? resp.body().substring(0, 150) + "..." : resp.body();
        println("  " + resp.statusCode() + " " + preview);
        return resp.body();
    }

    private static String get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(BASE + path)).GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        String preview = resp.body().length() > 200 ? resp.body().substring(0, 200) + "..." : resp.body();
        println("  " + resp.statusCode() + " " + preview);
        return resp.body();
    }

    private static byte[] buildFrame(String content) {
        byte[] body = content.getBytes(StandardCharsets.UTF_8);
        byte[] frame = new byte[1 + body.length + 1];
        frame[0] = 0x02;
        System.arraycopy(body, 0, frame, 1, body.length);
        frame[frame.length - 1] = 0x03;
        return frame;
    }

    private static void println(String s) { System.out.println(s); }
}
