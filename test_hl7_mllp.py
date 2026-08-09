"""
Send 10 real HL7 MLLP messages to capl LIS inbound server and verify ACK.
Tests: normal flow, dedup, edge cases, framing, escaping.
"""
import socket
import time
import uuid
import struct

HOST = '127.0.0.1'
PORT = 2575
VT = b'\x0b'
FS = b'\x1c'
CR = b'\x0d'

def send_mllp(hl7_message: str) -> tuple:
    """Send one MLLP frame and receive ACK. Returns (ack_text, elapsed_ms)."""
    frame = VT + hl7_message.encode('utf-8') + FS + CR
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.settimeout(10)
    sock.connect((HOST, PORT))
    start = time.time()
    sock.sendall(frame)

    # Read MLLP response (VT ... FS CR)
    data = b''
    while True:
        chunk = sock.recv(4096)
        if not chunk:
            break
        data += chunk
        # Check for FS+CR terminator
        fs_pos = data.find(FS)
        if fs_pos >= 0 and len(data) > fs_pos and data[fs_pos:fs_pos+2] == FS + CR:
            break

    elapsed = (time.time() - start) * 1000
    sock.close()

    # Extract message body (between VT and FS+CR)
    vt_pos = data.find(VT)
    if vt_pos >= 0 and fs_pos >= 0:
        ack_body = data[vt_pos+1:fs_pos].decode('utf-8', errors='replace')
        return ack_body, elapsed
    return data.decode('utf-8', errors='replace'), elapsed


def msh(case_num: int, msg_type: str, msg_control_id: str, sending_facility: str = "LIS_HOSPITAL") -> str:
    """Build MSH segment with standard HL7 separators."""
    now = time.strftime("%Y%m%d%H%M%S")
    return (
        f"MSH|^~\\&|{sending_facility}|HOSPITAL|MLMS|LAB|{now}||{msg_type}|{msg_control_id}|P|2.5\r"
    )


def test_messages():
    """Generate 10 real HL7 test messages."""
    base_id = f"TEST{int(time.time())}"

    return [
        # 1. Normal ORM^O01 — microbiology order
        {
            "name": "Normal ORM^O01 order",
            "hl7":
                msh(1, "ORM^O01", f"{base_id}001") +
                "PID|1||12345678||张三^ZHANGSAN||19850101|M\r"
                "PV1|1|I|ICU^01^01||||1234^SMITH^JOHN\r"
                "ORC|NW|ORD001|||||^^^20250101^^R\r"
                "OBR|1|ORD001||CULTURE^BLOOD CULTURE|||20250101120000|||||||BLOOD|||1234^SMITH^JOHN\r"
        },
        # 2. Normal ADT^A01 — patient admit
        {
            "name": "Normal ADT^A01 admit",
            "hl7":
                msh(2, "ADT^A01", f"{base_id}002") +
                "EVN|A01|20250101120000\r"
                "PID|1||87654321||李四^LISI||19900315|F|||朝阳区某某路100号\r"
                "PV1|1|O|OPD^01^01||||5678^JONES^LUCY\r"
        },
        # 3. Order with special HL7 escape characters
        {
            "name": "Special characters (\\F\\ \\S\\ \\T\\ \\R\\)",
            "hl7":
                msh(3, "ORM^O01", f"{base_id}003") +
                "PID|1||99999001||王\\F\\五\\S\\^WANG\\T\\WU||19880707|M\r"
                "OBR|1|ORD003||MICRO^ANAEROBIC\\T\\CULTURE\r"
        },
        # 4. Order with Chinese characters (patient name, address)
        {
            "name": "Chinese characters",
            "hl7":
                msh(4, "ORM^O01", f"{base_id}004") +
                "PID|1||CHN00001||赵六^ZHAOLIU||19950620|F|||北京市海淀区中关村南大街5号院||||||||||||中国\r"
                "PV1|1|I|WARD^03^BED05\r"
                "ORC|NW|ORD004\r"
                "OBR|1|ORD004||CULTURE^痰培养\r"
        },
        # 5. Duplicate MSH-10 (same as message 1) — test Redis dedup
        {
            "name": f"DUPLICATE — same MSH-10 as msg1 ({base_id}001)",
            "hl7":
                msh(5, "ORM^O01", f"{base_id}001") +  # Same MSH-10 as #1
                "PID|1||12345678||张三^ZHANGSAN||19850101|M\r"
                "PV1|1|I|ICU^01^01\r"
                "ORC|NW|ORD001_DUP\r"
                "OBR|1|ORD001_DUP||CULTURE^URINE CULTURE\r"
        },
        # 6. Empty MSH-10 — edge case, should log warning
        {
            "name": "Empty MSH-10 (no dedup)",
            "hl7":
                msh(6, "ORM^O01", "") +  # Empty MSH-10
                "PID|1||EMPTY001||测试患者^TEST||20000101|U\r"
                "OBR|1|ORD_EMPTY||GENERAL^GENERAL TEST\r"
        },
        # 7. ORU^R01 — result upload
        {
            "name": "ORU^R01 result message",
            "hl7":
                msh(7, "ORU^R01", f"{base_id}007") +
                "PID|1||RESULT001||钱七^QIANQI||19781203|M\r"
                "OBR|1|RES001||CULTURE^BLOOD CULTURE|||20250801080000\r"
                "OBX|1|CE|ORG^ORGANISM||E.coli^Escherichia coli||||||F\r"
                "OBX|2|ST|SUSC^SUSCEPTIBILITY||AMP:S||||||F\r"
        },
        # 8. MDM^T02 — document notification
        {
            "name": "MDM^T02 document notification",
            "hl7":
                msh(8, "MDM^T02", f"{base_id}008") +
                "PID|1||DOC00001||孙八^SUNBA||19650505|F\r"
                "PV1|1|I|ICU^02\r"
                "TXA|1|REPORT||20250809100000||||||||||||||PDF^PDF^L\r"
        },
        # 9. Minimal valid HL7 (MSH only)
        {
            "name": "Minimal MSH-only message",
            "hl7":
                msh(9, "ACK^ACK", f"{base_id}009", "REMOTE_LIS"),
        },
        # 10. Large message simulating many OBX segments (~5KB)
        {
            "name": "Large message with 50 OBX segments",
            "hl7":
                msh(10, "ORU^R01", f"{base_id}010") +
                "PID|1||LARGE001||大型消息测试||20010101|M\r" +
                "OBR|1|LARGE001||CULTURE^BLOOD CULTURE\r" +
                "".join(f"OBX|{i}|ST|TEST{i:03d}||Result value for test {i:03d}: NEGATIVE||||||F\r" for i in range(1, 51))
        },
    ]


def main():
    import sys
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    print("=" * 70)
    print(f"HL7 MLLP Test - 10 real messages to {HOST}:{PORT}")
    print(f"Time: {time.strftime('%Y-%m-%d %H:%M:%S')}")
    print("=" * 70)

    tests = test_messages()
    passed = 0
    failed = 0

    for i, test in enumerate(tests, 1):
        name = test["name"]
        hl7 = test["hl7"]
        try:
            ack, elapsed = send_mllp(hl7)
            # Parse ACK
            ack_segments = ack.split('\r')
            msa = [s for s in ack_segments if s.startswith('MSA')]
            msa_code = msa[0].split('|')[1] if msa else '?'
            ack_header = [s for s in ack_segments if s.startswith('MSH')]

            status = "PASS" if msa_code == "AA" else ("WARN" if msa_code == "AR" else "FAIL")
            if msa_code == "AA":
                passed += 1
            else:
                failed += 1

            print(f"\n[{i:2d}] {status} {name}")
            print(f"    MSH-10: {hl7.split('|')[9].split('\\r')[0] if '|' in hl7 else '?'}")
            print(f"    HL7 size: {len(hl7)} bytes | ACK: {msa_code} | {elapsed:.1f}ms")
            print(f"    ACK: {ack[:120]}...")

        except Exception as e:
            failed += 1
            print(f"\n[{i:2d}] FAIL {name}")
            print(f"    ERROR: {type(e).__name__}: {e}")

    print(f"\n{'=' * 70}")
    print(f"Results: {passed} passed, {failed} failed out of {len(tests)}")
    print(f"{'=' * 70}")


if __name__ == '__main__':
    main()
