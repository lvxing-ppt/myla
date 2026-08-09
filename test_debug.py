"""Debug: send one HL7 MLLP message and show raw response bytes."""
import socket
import time

HOST = '127.0.0.1'
PORT = 2575
VT = b'\x0b'
FS = b'\x1c'
CR = b'\x0d'

now = time.strftime("%Y%m%d%H%M%S")
hl7 = (
    f"MSH|^~\\&|LIS_HOSPITAL|HOSPITAL|MLMS|LAB|{now}||ORM^O01|DEBUG0001|P|2.5\r"
    f"PID|1||12345678||张三||19850101|M\r"
    f"OBR|1|ORD001||CULTURE^BLOOD CULTURE\r"
)

frame = VT + hl7.encode('utf-8') + FS + CR
print(f"Sending {len(hl7)} bytes HL7 + 3 bytes MLLP wrapper")
print(f"HL7: {hl7[:100]}...")
print(f"Raw frame ({len(frame)} bytes): {frame[:20].hex()}...")

sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
sock.settimeout(10)
sock.connect((HOST, PORT))
sock.sendall(frame)

data = b''
while True:
    try:
        chunk = sock.recv(4096)
        if not chunk:
            break
        data += chunk
        if FS in data:
            fs_idx = data.rfind(FS)
            if fs_idx >= 0 and len(data) > fs_idx and data[fs_idx:fs_idx+2] == FS + CR:
                break
    except socket.timeout:
        break
sock.close()

print(f"\nReceived {len(data)} bytes raw response:")
print(f"  Hex: {data.hex()}")
print(f"  ASCII printable: {''.join(chr(b) if 32 <= b < 127 else '.' for b in data)}")
print(f"  Contains VT (0x0B): {VT in data}")
print(f"  Contains FS (0x1C): {FS in data}")
print(f"  Contains CR (0x0D): {CR in data}")

# Parse MLLP
if data:
    vt_pos = data.find(VT)
    fs_pos = data.find(FS)
    if vt_pos >= 0 and fs_pos > vt_pos:
        body = data[vt_pos+1:fs_pos]
        print(f"\nACK body ({len(body)} bytes):")
        print(f"  {body.decode('utf-8', errors='replace')}")
    else:
        print(f"\nCould not find VT or FS in response. Raw data:")
        for i, b in enumerate(data):
            print(f"  [{i}] 0x{b:02X} ({b:3d}) {chr(b) if 32 <= b < 127 else '.'}")
