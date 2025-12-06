import socket
import time
import sys

def run_test(host='127.0.0.1', port=50505):
    try:
        print(f"Connecting to {host}:{port}...")
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.connect((host, port))
        
        # Send HELLO to start session
        print("Sending HELLO...")
        s.sendall(b"HELLO test_device\n")
        resp = s.recv(1024).decode()
        print(f"Received: {resp.strip()}")
        
        # Send bad DATA_TRANSFER
        print("Sending bad DATA_TRANSFER...")
        s.sendall(b"DATA_TRANSFER invalid_number\n")
        
        # Check if server closes connection or we can still send
        time.sleep(1)
        try:
            s.sendall(b"PING\n")
            resp = s.recv(1024).decode()
            print(f"Received response: {resp.strip()}")
            if not resp:
                print("Server closed connection (crash?)")
        except:
             print("Send failed (server crashed?)")

        s.close()
    except Exception as e:
        print(f"Error: {e}")

if __name__ == "__main__":
    run_test()
