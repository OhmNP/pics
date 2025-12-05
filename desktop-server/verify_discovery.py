import socket
import json
import time

def verify_discovery():
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.bind(('', 50505))
    sock.settimeout(10)
    
    print("Listening for PhotoSync broadcasts on port 50505...")
    try:
        data, addr = sock.recvfrom(1024)
        print(f"Received from {addr}: {data.decode()}")
        msg = json.loads(data.decode())
        
        if msg['service'] == 'photosync' and msg['port'] == 50505:
            print("Verification SUCCESS: Valid broadcast received")
            return True
        else:
            print("Verification FAILED: Invalid message content")
            return False
            
    except socket.timeout:
        print("Verification FAILED: Timeout waiting for broadcast")
        return False
    except Exception as e:
        print(f"Verification FAILED: {e}")
        return False
    finally:
        sock.close()

if __name__ == "__main__":
    verify_discovery()
