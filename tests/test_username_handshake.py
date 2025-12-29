import socket
import json
import struct
import time
import requests

SERVER_IP = 'localhost'
SERVER_PORT = 50505
API_URL = 'http://localhost:50506/api/connections'

def create_packet(packet_type, payload):
    payload_bytes = json.dumps(payload).encode('utf-8')
    # Header: Magic(2) + Version(1) + Type(1) + Length(4)
    # Magic: 'P', 'S' -> 0x50, 0x53
    header = struct.pack('>BBBI', 0x50, 0x53, 1, packet_type, len(payload_bytes))
    return header + payload_bytes

def test_handshake():
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    try:
        s.connect((SERVER_IP, SERVER_PORT))
        print("Connected to server")

        # Send Pairing Request (Type 1) with User Name
        payload = {
            "deviceId": "test_device_verification_123",
            "token": "",
            "userName": "VerificationUser"
        }
        packet = create_packet(1, payload)
        s.sendall(packet)
        print("Sent Pairing Request")

        # Read Response Header
        header = s.recv(8)
        if len(header) < 8:
            print("Failed to receive header")
            return False
            
        magic1, magic2, version, ptype, length = struct.unpack('>BBBI', header)
        print(f"Received Packet Type: {ptype}")
        
        # Read Payload
        if length > 0:
            resp_payload = s.recv(length)
            resp_json = json.loads(resp_payload.decode('utf-8'))
            print(f"Response: {resp_json}")

        # Now check API
        time.sleep(1) # Wait for server to process
        
        response = requests.get(API_URL)
        if response.status_code == 200:
            data = response.json()
            connections = data.get('active_connections', [])
            found = False
            for conn in connections:
                if conn.get('device_id') == 'test_device_verification_123':
                    print(f"Found connection in API: {conn}")
                    if conn.get('user_name') == 'VerificationUser':
                        print("SUCCESS: User Name matches!")
                        found = True
                    else:
                        print(f"FAILURE: User Name mismatch (Expected 'VerificationUser', got '{conn.get('user_name')}')")
            
            if not found:
                 print("FAILURE: Connection not found in API")
        else:
            print(f"FAILURE: API request failed {response.status_code}")

    except Exception as e:
        print(f"Error: {e}")
    finally:
        s.close()

if __name__ == "__main__":
    test_handshake()
