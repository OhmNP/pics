
import socket
import ssl
import json
import struct
import time
import os
import hashlib

SERVER_HOST = '127.0.0.1'
SERVER_PORT = 50505
CA_CERT = 'server.crt' # Ensure this exists or use verify_mode=CERT_NONE for loopback dev if accepted

# Constants
PROTOCOL_VERSION_2 = 0x02
PACKET_UPLOAD_INIT = 0x10
PACKET_UPLOAD_ACK = 0x11
PACKET_UPLOAD_CHUNK = 0x12
PACKET_UPLOAD_CHUNK_ACK = 0x16 # 0x16 in ProtocolParser.h
PACKET_UPLOAD_FINISH = 0x13
PACKET_UPLOAD_RESULT = 0x14
PACKET_UPLOAD_ABORT = 0x15

def create_packet(packet_type, payload_bytes):
    # Header: Magic(2) + Version(1) + Type(1) + PayloadLen(4)
    # Magic: 'P', 'H'
    header = struct.pack('>2sBB I', b'PH', PROTOCOL_VERSION_2, packet_type, len(payload_bytes))
    return header + payload_bytes

def connect():
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    # Wrap with SSL
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
    context.check_hostname = False
    context.verify_mode = ssl.CERT_NONE # For dev self-signed
    
    conn = context.wrap_socket(s, server_hostname=SERVER_HOST)
    conn.connect((SERVER_HOST, SERVER_PORT))
    return conn

def read_packet(conn):
    header = conn.read(8)
    if not header:
        return None, None
    magic, version, ptype, length = struct.unpack('>2sBB I', header)
    payload = conn.read(length)
    return ptype, payload

def verify_resume_flow():
    print("--- Starting Resume Flow Test ---")
    conn = connect()
    
    # 1. Pairing (V1) - Mocked or reused from previous tests?
    # Actually, we need to be authorized. V2 implementation checks `clientId_ != -1`.
    # Login usually happens via Pairing or existing session?
    # Server expects PAIRING_REQUEST or just checks IP?
    # ApiServer handles Login (HTTP). TcpListener handles Sync.
    # TcpListener.cpp: handlePairingRequest
    # We need to pair first to get clientId.
    
    # Payload: {"deviceName": "TestClient", "deviceType": "Desktop", "deviceId": "test-device-id-123"}
    pairing_payload = json.dumps({"deviceName": "TestClient", "deviceType": "PythonTest", "deviceId": "test-device-id-123"}).encode('utf-8')
    packet = struct.pack('>2sBB I', b'PH', 1, 0x02, len(pairing_payload)) + pairing_payload # 0x02 = PAIRING_REQUEST
    conn.write(packet)
    
    ptype, payload = read_packet(conn)
    # Should get PAIRING_RESPONSE
    print(f"Pairing Response Type: {ptype}")
    if ptype == 3: # PAIRING_RESPONSE
        resp = json.loads(payload)
        print(f"Pairing Payload: {resp}")
        if not resp.get('success'):
             print("PAIRING FAILED!")
             return False
    
    # 2. UPLOAD_INIT
    file_content = b"A" * (1024 * 1024 * 5) # 5MB
    file_hash = hashlib.sha256(file_content).hexdigest()
    file_size = len(file_content)
    
    init_payload = json.dumps({
        "filename": "test_resume.jpg",
        "size": file_size,
        "hash": file_hash
    }).encode('utf-8')
    
    conn.write(create_packet(PACKET_UPLOAD_INIT, init_payload))
    
    ptype, payload = read_packet(conn)
    if ptype != PACKET_UPLOAD_ACK:
        print(f"FAILED: Expected UPLOAD_ACK, got {ptype}")
        try:
             err = json.loads(payload)
             print(f"Error Payload: {err}")
        except:
             print(f"Raw Payload: {payload}")
        return False
        
    ack = json.loads(payload)
    print(f"UPLOAD_ACK: {ack}")
    upload_id = ack['uploadId']
    
    # 3. Send Chunk 1 (1MB)
    chunk1 = file_content[:1024*1024]
    # Chunk Payload: UploadID(36) + Offset(8) + Data
    offset_bytes = struct.pack('>Q', 0) # Big Endian 64-bit
    chunk_payload = upload_id.encode('ascii') + offset_bytes + chunk1
    
    conn.write(create_packet(PACKET_UPLOAD_CHUNK, chunk_payload))
    
    ptype, payload = read_packet(conn)
    print(f"Chunk 1 Response: {ptype}")
    if ptype != PACKET_UPLOAD_CHUNK_ACK:
         print(f"FAILED: Expected UPLOAD_CHUNK_ACK")
         return False
         
    # 4. Simulate Disconnect
    print("Simulating Disconnect...")
    conn.close()
    
    # 5. Reconnect and Resume
    conn = connect()
    # Re-pair needed? Yes, generic session state lost on disconnect usually, unless strict session resumption impl.
    # My TcpListener implementation: `clientId_` is reset on new connection.
    # So we MUST pair again to get authorized.
    conn.write(packet) # Re-send pairing
    read_packet(conn) # Read pairing response
    
    # Send UPLOAD_INIT again with SAME hash
    conn.write(create_packet(PACKET_UPLOAD_INIT, init_payload))
    
    ptype, payload = read_packet(conn)
    ack = json.loads(payload)
    print(f"Resume UPLOAD_ACK: {ack}")
    
    if ack['receivedBytes'] != 1024*1024:
        print(f"FAILED: Expected offset 1048576, got {ack['receivedBytes']}")
        return False
        
    if ack['status'] != "RESUMING":
         print(f"FAILED: Expected status RESUMING, got {ack['status']}")
         return False

    print("SUCCESS: Resume Verified")
    return True

if __name__ == "__main__":
    try:
        if verify_resume_flow():
            print("TEST PASSED")
            exit(0)
        else:
            print("TEST FAILED")
            exit(1)
    except Exception as e:
        print(f"ERROR: {e}")
        exit(1)
