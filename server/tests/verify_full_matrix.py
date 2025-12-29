import socket
import ssl
import json
import struct
import time
import uuid
import hashlib

# Configuration
HOST = '127.0.0.1'
PORT = 50505
DEVICE_ID = "test-device-matrix"

# Packet Types (V2)
PACKET_UPLOAD_INIT = 0x10
PACKET_UPLOAD_ACK = 0x11
PACKET_UPLOAD_CHUNK = 0x12
PACKET_UPLOAD_FINISH = 0x13
PACKET_UPLOAD_RESULT = 0x14
PACKET_UPLOAD_ABORT = 0x15
PACKET_UPLOAD_CHUNK_ACK = 0x16

def create_packet(ptype, payload=None):
    if payload is None:
        payload = b''
    elif isinstance(payload, dict):
        payload = json.dumps(payload).encode('utf-8')
    
    header = struct.pack('>2sBB I', b'PH', 2, ptype, len(payload)) # Version 2
    return header + payload

def read_packet(conn):
    header_data = conn.read(8)
    if not header_data:
        raise Exception("Connection closed")
    magic, version, ptype, length = struct.unpack('>2sBB I', header_data)
    if magic != b'PH':
        raise Exception("Invalid Magic")
    
    payload = b''
    if length > 0:
        payload = conn.read(length)
    return ptype, payload

def connect_and_pair():
    # SSL Context
    context = ssl.create_default_context()
    context.check_hostname = False
    context.verify_mode = ssl.CERT_NONE
    
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    conn = context.wrap_socket(sock, server_hostname=HOST)
    conn.connect((HOST, PORT))
    
    # Pairing (V1)
    pairing_payload = json.dumps({"deviceName": "MatrixTest", "deviceType": "Python", "deviceId": DEVICE_ID}).encode('utf-8')
    packet = struct.pack('>2sBB I', b'PH', 1, 0x02, len(pairing_payload)) + pairing_payload
    conn.write(packet)
    
    ptype, payload = read_packet(conn)
    if ptype != 3: # PAIRING_RESPONSE
        raise Exception(f"Pairing failed, got type {ptype}")
    
    resp = json.loads(payload)
    if not resp.get('success'):
        raise Exception("Pairing rejected")
        
    return conn

def test_chunk_rules():
    print("\n--- Test V-4/V-5: Idempotency & Gaps ---")
    conn = connect_and_pair()
    
    # 1. Init Upload
    file_content = b"A" * (1024 * 1024 * 3) # 3MB
    file_hash = hashlib.sha256(file_content).hexdigest()
    filename = f"matrix_test_{int(time.time())}.dat"
    
    init_payload = {
        "filename": filename,
        "size": len(file_content),
        "hash": file_hash,
        "traceId": str(uuid.uuid4())
    }
    conn.write(create_packet(PACKET_UPLOAD_INIT, init_payload))
    _, payload = read_packet(conn)
    ack = json.loads(payload)
    upload_id = ack['uploadId']
    print(f"Session Created: {upload_id}")

    # 2. Send Chunk 0
    chunk0 = file_content[:1024*1024]
    chunk_payload = upload_id.encode('ascii') + struct.pack('>Q', 0) + chunk0
    conn.write(create_packet(PACKET_UPLOAD_CHUNK, chunk_payload))
    ptype, _ = read_packet(conn)
    if ptype != PACKET_UPLOAD_CHUNK_ACK:
        print("FAIL: Chunk 0 rejected")
        return False
    print("Chunk 0 Accepted")

    # 3. Test V-4: Duplicate Chunk 0
    print("Sending Duplicate Chunk 0...")
    conn.write(create_packet(PACKET_UPLOAD_CHUNK, chunk_payload))
    ptype, payload = read_packet(conn)
    if ptype == PACKET_UPLOAD_CHUNK_ACK:
        print("PASS: Duplicate Chunk 0 ACKed (Idempotent)")
    else:
        print(f"FAIL: Duplicate Chunk 0 got type {ptype}")
        return False

    # 4. Test V-5: Gap (Skip Chunk 1, Send Chunk 2)
    print("Sending Gap Chunk 2 (Offset 2MB)...")
    chunk2 = file_content[2*1024*1024:]
    chunk_payload_2 = upload_id.encode('ascii') + struct.pack('>Q', 2*1024*1024) + chunk2
    conn.write(create_packet(PACKET_UPLOAD_CHUNK, chunk_payload_2))
    
    ptype, payload = read_packet(conn)
    if ptype == 0x09: # PROTOCOL_ERROR
        err = json.loads(payload)
        print(f"PASS: Gap detected. Error: {err}")
    else:
        print(f"FAIL: Gap chunk was accepted or wrong response {ptype}")
        return False
        
    conn.close()
    return True

def test_hash_mismatch():
    print("\n--- Test V-6: Final Hash Mismatch ---")
    conn = connect_and_pair()
    
    # 1. Init
    file_content = b"B" * (1024 * 1024) # 1MB
    true_hash = hashlib.sha256(file_content).hexdigest()
    fake_hash = hashlib.sha256(b"CORRUPT").hexdigest()
    filename = f"matrix_hash_bad_{int(time.time())}.dat"
    
    init_payload = {
        "filename": filename,
        "size": len(file_content),
        "hash": true_hash, # Server session bound to TRUE hash
        "traceId": str(uuid.uuid4())
    }
    # Note: If we init with fake_hash, server expects fake_hash content.
    # To test mismatch, we must upload content that DOES NOT match the Init hash.
    # So let's Init with Hash A, Upload Content B.
    
    conn.write(create_packet(PACKET_UPLOAD_INIT, init_payload))
    _, payload = read_packet(conn)
    ack = json.loads(payload)
    upload_id = ack['uploadId']
    
    # Upload Content "C" (Mismatch)
    bad_content = b"C" * (1024 * 1024)
    chunk_payload = upload_id.encode('ascii') + struct.pack('>Q', 0) + bad_content
    conn.write(create_packet(PACKET_UPLOAD_CHUNK, chunk_payload))
    read_packet(conn) # ACK
    
    # Finish
    finish_payload = {
        "uploadId": upload_id,
        "sha256": true_hash # Claim it matches Init hash
    }
    conn.write(create_packet(PACKET_UPLOAD_FINISH, finish_payload))
    
    ptype, payload = read_packet(conn)
    if ptype == 0x09: # PROTOCOL_ERROR
        err = json.loads(payload)
        print(f"PASS: Hash mismatch caught. Error: {err}")
        if err['code'] == 409:
             print("PASS: Error Code is 409 (Conflict)")
        else:
             print(f"FAIL: Error Code mismatch. Expected 409, got {err['code']}")
             return False
    else:
        print(f"FAIL: Hash mismatch ignored! Got {ptype} {payload}")
        return False
        
    conn.close()
    return True

if __name__ == "__main__":
    try:
        r1 = test_chunk_rules()
        r2 = test_hash_mismatch()
        
        if r1 and r2:
            print("\nALL MATRIX TESTS PASSED")
            exit(0)
        else:
            print("\nTESTS FAILED")
            exit(1)
    except Exception as e:
        print(f"\nCRITICAL ERROR: {e}")
        exit(1)
