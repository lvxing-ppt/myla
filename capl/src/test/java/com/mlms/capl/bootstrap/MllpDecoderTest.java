package com.mlms.capl.bootstrap;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Real HL7 MLLP decoder test — 10 messages covering normal flow, edge cases,
 * framing, and error scenarios. Uses EmbeddedChannel to test without Spring.
 */
public class MllpDecoderTest {

    private static final byte VT = 0x0B;
    private static final byte FS = 0x1C;
    private static final byte CR = 0x0D;

    private static final DateTimeFormatter DTM_FMT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    static int passed = 0;
    static int failed = 0;

    public static void main(String[] args) {
        System.out.println("=".repeat(70));
        System.out.println("MLLP Frame Decoder Test — 10 real HL7 messages");
        System.out.println("Time: " + LocalDateTime.now().format(DTM_FMT));
        System.out.println("=".repeat(70));

        runAllTests();

        System.out.println("\n" + "=".repeat(70));
        System.out.println("Results: " + passed + " passed, " + failed + " failed out of " + (passed + failed));
        if (failed > 0) {
            System.out.println("*** SOME TESTS FAILED ***");
            System.exit(1);
        } else {
            System.out.println("*** ALL TESTS PASSED ***");
        }
    }

    // ==================== Test Runner ====================

    static void runAllTests() {
        String baseId = "TEST" + System.currentTimeMillis() / 1000;

        // 1. Single complete frame
        test("Single complete ORM^O01 frame", () -> {
            String hl7 = buildHl7("ORM^O01", baseId + "01",
                    "PID|1||12345678||ZHANGSAN||19850101|M\r" +
                    "OBR|1|ORD001||CULTURE^BLOOD CULTURE\r");
            List<byte[]> frames = decode(fullFrame(hl7));
            assert frames.size() == 1 : "Expected 1 frame, got " + frames.size();
            assert frames.get(0).length == hl7.getBytes(StandardCharsets.UTF_8).length
                    : "Frame body length mismatch";
            String decoded = new String(frames.get(0), StandardCharsets.UTF_8);
            assert decoded.contains("ORM^O01") : "Missing message type";
            assert decoded.contains(baseId + "01") : "Missing message control ID";
            System.out.println("    Decoded: " + decoded.substring(0, Math.min(80, decoded.length())) + "...");
        });

        // 2. Two frames in one buffer (sticky packet / 粘包)
        test("Two frames in one buffer (sticky packet)", () -> {
            String hl7a = buildHl7("ADT^A01", baseId + "02a",
                    "PID|1||87654321||LISI||19900315|F\r");
            String hl7b = buildHl7("ORM^O01", baseId + "02b",
                    "PID|1||99999001||WANGWU||19880707|M\r" +
                    "OBR|1|ORD002||CULTURE^URINE\r");
            byte[] combined = concat(fullFrame(hl7a), fullFrame(hl7b));
            List<byte[]> frames = decode(combined);
            assert frames.size() == 2 : "Expected 2 frames, got " + frames.size();
            assert new String(frames.get(0), StandardCharsets.UTF_8).contains("ADT^A01");
            assert new String(frames.get(1), StandardCharsets.UTF_8).contains("ORM^O01");
            System.out.println("    Frame 1: " + new String(frames.get(0), StandardCharsets.UTF_8).substring(0, Math.min(60, frames.get(0).length)) + "...");
            System.out.println("    Frame 2: " + new String(frames.get(1), StandardCharsets.UTF_8).substring(0, Math.min(60, frames.get(1).length)) + "...");
        });

        // 3. Split frame (拆包) — VT + partial body, then body end + FS+CR
        test("Split frame across two reads (unpacking)", () -> {
            String hl7 = buildHl7("ORU^R01", baseId + "03",
                    "PID|1||RESULT001||QIANQI||19781203|M\r" +
                    "OBR|1|RES001||CULTURE^BLOOD\r" +
                    "OBX|1|CE|ORG||E.coli||||||F\r");
            byte[] full = fullFrame(hl7);
            int splitPoint = 20;  // Split after VT + first 19 bytes
            byte[] part1 = new byte[splitPoint];
            System.arraycopy(full, 0, part1, 0, splitPoint);
            byte[] part2 = new byte[full.length - splitPoint];
            System.arraycopy(full, splitPoint, part2, 0, part2.length);

            EmbeddedChannel ch = newEmbeddedChannel();
            ch.writeInbound(Unpooled.wrappedBuffer(part1));
            // After partial write, decoder should produce nothing
            Object partial = ch.readInbound();
            assert partial == null : "Should not produce frame from partial data, got " + partial;

            ch.writeInbound(Unpooled.wrappedBuffer(part2));
            Object msg = ch.readInbound();
            assert msg != null : "Should produce frame after receiving complete data";
            byte[] decoded;
            if (msg instanceof ByteBuf buf) {
                decoded = new byte[buf.readableBytes()];
                buf.readBytes(decoded);
                buf.release();
            } else {
                decoded = (byte[]) msg;
            }
            assert new String(decoded, StandardCharsets.UTF_8).contains("ORU^R01");
            assert new String(decoded, StandardCharsets.UTF_8).contains("E.coli");
            ch.close();
            System.out.println("    Split at byte " + splitPoint + ", reassembled correctly");
        });

        // 4. Garbage before VT header
        test("Garbage bytes before VT header", () -> {
            String hl7 = buildHl7("MDM^T02", baseId + "04",
                    "PID|1||DOC00001||SUNBA||19650505|F\r" +
                    "TXA|1|REPORT||20250809100000\r");
            byte[] garbage = {0x00, 0x01, 0x02, 0x03, 0x04};
            byte[] frame = fullFrame(hl7);
            byte[] combined = concat(garbage, frame);
            List<byte[]> frames = decode(combined);
            assert frames.size() == 1 : "Expected 1 frame after garbage skip";
            assert new String(frames.get(0), StandardCharsets.UTF_8).contains("MDM^T02");
            System.out.println("    Skipped " + garbage.length + " garbage bytes before VT");
        });

        // 5. Corrupted terminator (FS+X instead of FS+CR) — should skip bad frame, get next
        test("Corrupted terminator FS+X (not CR) — skip and recover next frame", () -> {
            String badHl7 = "MSH|^~\\&|BAD|BAD|MLMS|LAB|20250101||ACK|BAD001|P|2.5\rMSA|AR|BAD001\r";
            String goodHl7 = buildHl7("ORM^O01", baseId + "05",
                    "PID|1||GOOD001||RECOVERED||20010101|M\r" +
                    "OBR|1|ORD005||CULTURE^SPUTUM\r");
            byte[] badFrame = VT_frame(badHl7, (byte) 0x1C, (byte) 0x58); // FS+'X' not FS+CR
            byte[] goodFrame = fullFrame(goodHl7);
            byte[] combined = concat(badFrame, goodFrame);
            List<byte[]> frames = decode(combined);
            // The bad frame should be skipped, good frame should be extracted
            assert frames.size() >= 1 : "Should recover at least 1 good frame";
            boolean hasGood = frames.stream().anyMatch(
                    f -> new String(f, StandardCharsets.UTF_8).contains("RECOVERED"));
            assert hasGood : "Should have recovered the good frame after corrupted one";
            System.out.println("    Corrupted frame skipped, recovered next frame: "
                    + new String(frames.get(frames.size() - 1), StandardCharsets.UTF_8).substring(0, Math.min(60, frames.get(frames.size() - 1).length)));
        });

        // 6. Frame body exceeds MAX_FRAME_LENGTH (128KB)
        test("Frame body exceeds 128KB limit", () -> {
            StringBuilder sb = new StringBuilder();
            sb.append("MSH|^~\\&|S|S|R|R|20250101||ORM^O01|BIG001|P|2.5\r");
            sb.append("PID|1||BIG||||||\r");
            sb.append("OBX|1|TX|HUGE||");
            while (sb.length() < 128 * 1024 + 100) {
                sb.append("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
            }
            sb.append("\r");
            byte[] frame = fullFrame(sb.toString());
            // Decoder will call ctx.close() for oversized frame;
            // EmbeddedChannel may throw due to released buffer — that's expected.
            List<byte[]> frames;
            try {
                frames = decode(frame);
            } catch (Exception e) {
                // Expected: decoder closes connection, buffer released
                frames = List.of();
            }
            assert frames.isEmpty() : "Oversized frame should not be delivered, got " + frames.size();
            System.out.println("    Oversized frame (" + (sb.length() / 1024) + "KB) correctly rejected");
        });

        // 7. Empty frame (VT+FS+CR with nothing in between)
        test("Empty MLLP frame (VT+FS+CR)", () -> {
            byte[] emptyFrame = {VT, FS, CR};
            List<byte[]> frames = decode(emptyFrame);
            assert frames.size() == 1 : "Empty frame should still be extracted";
            assert frames.get(0).length == 0 : "Empty frame body should be 0 bytes";
            System.out.println("    Empty frame extracted with 0-length body");
        });

        // 8. Multiple messages with Chinese characters
        test("HL7 with Chinese characters (UTF-8)", () -> {
            String hl7 = "MSH|^~\\&|LIS|HOSPITAL|MLMS|LAB|20250101120000||ORM^O01|" + baseId + "08|P|2.5\r"
                    + "PID|1||CHN001||赵六^ZHAOLIU||19950620|F|||北京市海淀区中关村南大街5号院\r"
                    + "OBR|1|ORD008||CULTURE^痰培养\r";
            List<byte[]> frames = decode(fullFrame(hl7));
            assert frames.size() == 1;
            String decoded = new String(frames.get(0), StandardCharsets.UTF_8);
            assert decoded.contains("赵六") : "Chinese name not preserved";
            assert decoded.contains("痰培养") : "Chinese test name not preserved";
            assert decoded.contains("中关村") : "Chinese address not preserved";
            System.out.println("    Decoded Chinese: " + decoded.substring(0, Math.min(80, decoded.length())) + "...");
        });

        // 9. HL7 escape sequences
        test("HL7 escape sequences (\\F\\ \\S\\ \\T\\ \\R\\)", () -> {
            String hl7 = "MSH|^~\\&|S|S|R|R|20250101||ORM^O01|" + baseId + "09|P|2.5\r"
                    + "PID|1||ESC001||\\F\\古\\S\\^\\T\\王\\R\\\\S\\||19880707|M\r"
                    + "OBR|1|ORD009||MICRO^ANAEROBIC\\T\\CULTURE\r";
            List<byte[]> frames = decode(fullFrame(hl7));
            assert frames.size() == 1;
            String decoded = new String(frames.get(0), StandardCharsets.UTF_8);
            assert decoded.contains("\\F\\") : "Field separator escape lost";
            assert decoded.contains("\\T\\") : "Sub-component separator escape lost";
            System.out.println("    Decoded: " + decoded.substring(0, Math.min(80, decoded.length())) + "...");
        });

        // 10. Large message with many segments (~10KB)
        test("Large message with 200 OBX segments (~10KB)", () -> {
            StringBuilder sb = new StringBuilder();
            sb.append("MSH|^~\\&|S|S|R|R|20250101||ORU^R01|").append(baseId).append("10|P|2.5\r");
            sb.append("PID|1||LARGE001||MANY_RESULTS||20010101|M\r");
            sb.append("OBR|1|LARGE001||CULTURE^BLOOD\r");
            for (int i = 1; i <= 200; i++) {
                sb.append("OBX|").append(i).append("|ST|TEST").append(String.format("%04d", i))
                        .append("||Result for test ").append(i).append(": NEGATIVE||||||F\r");
            }
            List<byte[]> frames = decode(fullFrame(sb.toString()));
            assert frames.size() == 1 : "Expected 1 frame";
            String decoded = new String(frames.get(0), StandardCharsets.UTF_8);
            assert decoded.contains("TEST0200") : "Last OBX segment missing";
            int obxCount = decoded.split("OBX\\|").length - 1;
            assert obxCount == 200 : "Expected 200 OBX, got " + obxCount;
            System.out.println("    Decoded " + obxCount + " OBX segments, " + decoded.length() + " chars");
        });
    }

    // ==================== Helpers ====================

    static EmbeddedChannel newEmbeddedChannel() {
        return new EmbeddedChannel(
                new LisInboundServerStarter.MllpFrameDecoder()
        );
    }

    static List<byte[]> decode(byte[] rawBytes) {
        EmbeddedChannel ch = newEmbeddedChannel();
        ch.writeInbound(Unpooled.wrappedBuffer(rawBytes));

        List<byte[]> frames = new ArrayList<>();
        while (true) {
            Object msg = ch.readInbound();
            if (msg == null) break;
            if (msg instanceof ByteBuf buf) {
                byte[] bytes = new byte[buf.readableBytes()];
                buf.readBytes(bytes);
                buf.release();
                frames.add(bytes);
            } else if (msg instanceof byte[] bytes) {
                frames.add(bytes);
            }
        }
        ch.close();
        return frames;
    }

    static byte[] fullFrame(String hl7) {
        return VT_frame(hl7, FS, CR);
    }

    static byte[] VT_frame(String hl7, byte term1, byte term2) {
        byte[] body = hl7.getBytes(StandardCharsets.UTF_8);
        byte[] frame = new byte[body.length + 3];
        frame[0] = VT;
        System.arraycopy(body, 0, frame, 1, body.length);
        frame[body.length + 1] = term1;
        frame[body.length + 2] = term2;
        return frame;
    }

    static byte[] concat(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    static String buildHl7(String msgType, String msgControlId, String segments) {
        String now = LocalDateTime.now().format(DTM_FMT);
        return "MSH|^~\\&|SENDER|HOSPITAL|MLMS|LAB|" + now + "||"
                + msgType + "|" + msgControlId + "|P|2.5\r" + segments;
    }

    static void test(String name, Runnable test) {
        try {
            test.run();
            passed++;
            System.out.println("\n[" + (passed + failed) + "] PASS: " + name);
        } catch (Throwable e) {
            failed++;
            System.out.println("\n[" + (passed + failed) + "] FAIL: " + name);
            System.out.println("    Error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            e.printStackTrace(System.out);
        }
    }
}
