
import os
import sys
import subprocess
import time
import socket
import json
import hashlib
import struct
import shutil
import glob

# Configuration
SERVER_BIN = os.path.join("build", "Release", "PhotoSyncServer.exe")
STORAGE_DIR = "test_integration_storage"
DB_PATH = "test_integration.db"
CONFIG_FILE = "test_integration.conf"
SERVER_PORT = 50555
SERVER_HOST = "127.0.0.1"

def calculate_sha256(data):
    sha = hashlib.sha256()
    sha.update(data)
    return sha.hexdigest()

def create_config():
    config = f"""
[network]
port={SERVER_PORT}
max_connections=5
timeout_seconds=5

[storage]
photos_dir={STORAGE_DIR}/photos
temp_dir={STORAGE_DIR}/temp
max_storage_gb=1

[database]
db_path={DB_PATH}

[logging]
log_level=INFO
log_file=test_integration.log
console_output=true

[integrity]
# Fast intervals for testing
scan_interval=1
verify_hash=true
missing_check_interval=2
orphan_sample_interval=3
full_scan_interval=5
orphan_sample_size=10
"""
    with open(CONFIG_FILE, "w") as f:
        f.write(config)

def cleanup():
    if os.path.exists(STORAGE_DIR):
        shutil.rmtree(STORAGE_DIR)
    if os.path.exists(DB_PATH):
        os.remove(DB_PATH)
    if os.path.exists(CONFIG_FILE):
        os.remove(CONFIG_FILE)
    if os.path.exists("test_integration.log"):
        os.remove("test_integration.log")

def wait_for_log(pattern, timeout=10):
    start = time.time()
    while time.time() - start < timeout:
        if os.path.exists("test_integration.log"):
            with open("test_integration.log", "r") as f:
                content = f.read()
                if pattern in content:
                    return True
        time.sleep(0.5)
    return False

def pack_string(s):
    return struct.pack(f">H{len(s)}s", len(s), s.encode('utf-8'))

def send_packet(sock, type_val, payload_dict):
    payload = json.dumps(payload_dict).encode('utf-8')
    # Header: Magic(2), Version(1), Type(1), Length(4)
    header = struct.pack(">HBBI", 0x5048, 1, type_val, len(payload)) 
    sock.sendall(header + payload)

def read_response(sock):
    header = sock.recv(8) # Header is 8 bytes
    if not header or len(header) < 8: return None
    magic, ver, type_val, length = struct.unpack(">HBBI", header)
    payload = sock.recv(length)
    return json.loads(payload)

def run_integration_test():
    print(f"=== Starting Integration Test ===")
    cleanup()
    create_config()
    
    # ensure storage dirs
    os.makedirs(f"{STORAGE_DIR}/photos", exist_ok=True)
    os.makedirs(f"{STORAGE_DIR}/temp", exist_ok=True)

    # Start Server
    print("Starting server...")
    server_proc = subprocess.Popen([SERVER_BIN, CONFIG_FILE], 
                                   stdout=subprocess.PIPE, 
                                   stderr=subprocess.PIPE,
                                   text=True)
    
    # Wait for server ready
    time.sleep(3)
    
    sock = None
    try:
        # Connect
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        context = __import__('ssl').create_default_context()
        context.check_hostname = False
        context.verify_mode = __import__('ssl').CERT_NONE
        wrapped_sock = context.wrap_socket(sock, server_hostname=SERVER_HOST)
        wrapped_sock.connect((SERVER_HOST, SERVER_PORT))
        
        # 1. Pair
        print("Pairing...")
        send_packet(wrapped_sock, 2, {"deviceId": "testdev", "userName": "tester"}) # PAIRING_REQUEST
        resp = read_response(wrapped_sock)
        if not resp["success"]:
            raise Exception("Pairing failed")
        print("Paired.")

        # 2. Upload File (Test Corrupt & Missing)
        file_content = b"A" * 1024
        file_hash = calculate_sha256(file_content)
        file_name = "integrity_test_A.jpg"
        
        print(f"Uploading {file_name}...")
        # METADATA = 5
        send_packet(wrapped_sock, 5, {"filename": file_name, "size": len(file_content), "hash": file_hash}) 
        resp = read_response(wrapped_sock) # READY
        
        # Send chunks
        # FILE_CHUNK = 7
        header = struct.pack(">HBBI", 0x5048, 1, 7, len(file_content)) 
        wrapped_sock.sendall(header + file_content)
        
        # Finish
        # TRANSFER_COMPLETE = 8
        send_packet(wrapped_sock, 8, {"hash": file_hash})
        # No Ack in V1? 
        time.sleep(1) 
        
        # Verify file exists on disk
        # We need to find where it is
        # storage/photos/YYYY/MM/hash.jpg
        # We can find it by walking
        found_path = None
        for root, dirs, files in os.walk(f"{STORAGE_DIR}/photos"):
            for f in files:
                if f.startswith(file_hash):
                    found_path = os.path.join(root, f)
                    break
        
        if not found_path:
            raise Exception("File upload failed, not found on disk")
        print(f"File uploaded to {found_path}")

        # --- TEST 1: CORRUPT BLOB ---
        print("\n[TEST 1] Testing Corrupt Blob Detection...")
        # Corrupt the file
        with open(found_path, "wb") as f:
            f.write(b"CORRUPTED_DATA" * 50)
        
        print("Waiting for scanner (Missing/Corrupt check runs every 2s)...")
        if wait_for_log("CORRUPT BLOB", 10):
            print("PASS: Detected Corrupt Blob")
        else:
            print("FAIL: Did not detect Corrupt Blob")
            raise Exception("Corrupt Blob Test Failed")

        # --- TEST 2: MISSING BLOB ---
        print("\n[TEST 2] Testing Missing Blob Detection...")
        # Delete the file
        os.remove(found_path)
        
        print("Waiting for scanner...")
        if wait_for_log("MISSING BLOB", 10):
            print("PASS: Detected Missing Blob")
        else:
            print("FAIL: Did not detect Missing Blob")
            raise Exception("Missing Blob Test Failed")

        # --- TEST 3: ORPHAN BLOB ---
        print("\n[TEST 3] Testing Orphan Blob Detection...")
        # Create a file that isn't in DB
        orphan_hash = "0000000000000000000000000000000000000000000000000000000000000000"
        orphan_path = f"{STORAGE_DIR}/photos/{orphan_hash}.jpg"
        with open(orphan_path, "wb") as f:
            f.write(b"ORPHAN" * 10)
        
        print("Waiting for scanner (Orphan check runs every 3s)...")
        if wait_for_log("ORPHAN BLOB", 10):
            print("PASS: Detected Orphan Blob")
        else:
            print("FAIL: Did not detect Orphan Blob")
            raise Exception("Orphan Blob Test Failed")

        print("\n=== Integration Test SUCCEEDED ===")

    except Exception as e:
        print(f"\n=== Integration Test FAILED: {e} ===")
        import traceback
        traceback.print_exc()
        
        # Dump Logs
        if os.path.exists("test_integration.log"):
            print("\n--- SERVER LOG ---")
            with open("test_integration.log", "r") as f:
                print(f.read())
        
        # Dump Stdout/Stderr
        if server_proc.poll() is not None:
             out, err = server_proc.communicate()
             print(f"\n--- SERVER STDOUT ---\n{out}")
             print(f"\n--- SERVER STDERR ---\n{err}")

    finally:
        if sock: sock.close()
        # Kill server
        server_proc.terminate()
        try:
             server_proc.wait(timeout=2)
        except:
             server_proc.kill()
        
        # Print remaining output if alive
        if server_proc.returncode is None:
             out, err = server_proc.communicate()
             if out: print(f"\n--- SERVER STDOUT ---\n{out}")
             if err: print(f"\n--- SERVER STDERR ---\n{err}")

if __name__ == "__main__":
    run_integration_test()
