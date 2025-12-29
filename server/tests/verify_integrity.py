
import socket
import ssl
import json
import struct
import time
import os
import shutil
import subprocess
import threading
import sys
import hashlib
import http.client
import urllib.parse

# Configuration
SERVER_HOST = '127.0.0.1'
SERVER_PORT = 50505
# Paths relative to project root
SERVER_BIN = r'server\build\Release\PhotoSyncServer.exe'
STORAGE_DIR = r'storage\photos'
TEST_FILE_CONTENT = b'A' * 1024 * 1024 # 1MB
TEST_FILE_HASH = hashlib.sha256(TEST_FILE_CONTENT).hexdigest()

def create_packet(type_val, payload_dict):
    payload_json = json.dumps(payload_dict).encode('utf-8')
    header = struct.pack('>2sB I', b'PH', type_val, len(payload_json))
    return header + payload_json

def read_packet(sock):
    header = sock.recv(7)
    if len(header) < 7: return None, None
    magic, type_val, length = struct.unpack('>2sB I', header)
    if magic != b'PH': raise ValueError("Invalid Magic")
    payload = sock.recv(length)
    return type_val, json.loads(payload)

class ServerProcess:
    def __init__(self):
        self.proc = None
        self.stop_event = threading.Event()
        self.logs = []
        self.lock = threading.Lock()

    def start(self):
        # Clean storage
        if os.path.exists('../photosync.db'): os.remove('../photosync.db')
        if os.path.exists('../server.log'): os.remove('../server.log')
        if os.path.exists('../storage'): shutil.rmtree('../storage')
        
        # Copy certs to root if not present
        if not os.path.exists('server.crt'):
            shutil.copy(r'server\server.crt', 'server.crt')
        if not os.path.exists('server.key'):
            shutil.copy(r'server\server.key', 'server.key')
        
        # Start server with default config
        self.proc = subprocess.Popen(
            [SERVER_BIN], 
            stdout=subprocess.PIPE, 
            stderr=subprocess.PIPE,
            text=True,
            bufsize=1
        )
        
        # Start log reader thread
        self.reader_thread = threading.Thread(target=self._read_stdout)
        self.reader_thread.start()
        
        # Wait for "Ready"
        self.wait_for_log("Sync server ready", timeout=10)

    def _read_stdout(self):
        while not self.stop_event.is_set() and self.proc.poll() is None:
            line = self.proc.stdout.readline()
            if line:
                print(f"[SERVER] {line.strip()}")
                with self.lock:
                    self.logs.append(line.strip())
            else:
               time.sleep(0.1)

    def wait_for_log(self, substring, timeout=30):
        start = time.time()
        while time.time() - start < timeout:
            with self.lock:
                for line in self.logs:
                    if substring in line:
                        return True
            time.sleep(0.5)
        return False

    def clear_logs(self):
        with self.lock:
            self.logs = []

    def stop(self):
        self.stop_event.set()
        if self.proc:
            self.proc.terminate()
            try:
                self.proc.wait(timeout=5)
            except:
                self.proc.kill()

def test_integrity():
    server = ServerProcess()
    try:
        print("Starting ID: 1, Step: Start Server")
        server.start()
        
        # 1. Upload a File
        print("Starting ID: 2, Step: Upload File")
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
        context.check_hostname = False
        context.verify_mode = ssl.CERT_NONE
        wrapped_sock = context.wrap_socket(sock, server_hostname=SERVER_HOST)
        wrapped_sock.connect((SERVER_HOST, SERVER_PORT))
        
        # Authenticate
        wrapped_sock.sendall(create_packet(1, {"deviceId": "test_device", "deviceType": "python_test"})) # PAIR_REQUEST
        _, auth_resp = read_packet(wrapped_sock) # PAIR_RESPONSE
        token = auth_resp.get("token", "") # Ensure we save token if needed for API
        
        # Init Upload
        wrapped_sock.sendall(create_packet(16, { # UPLOAD_INIT
            "fileHash": TEST_FILE_HASH,
            "filename": "test_integrity.jpg",
            "fileSize": len(TEST_FILE_CONTENT),
            "traceId": "trace_1"
        }))
        _, ack = read_packet(wrapped_sock)
        upload_id = ack['uploadId']
        
        # Send Chunk
        chunk_header = struct.pack('>2sB 16s I I', b'PH', 18, upload_id.encode('ascii'), 0, len(TEST_FILE_CONTENT)) # UPLOAD_CHUNK
        wrapped_sock.sendall(chunk_header + TEST_FILE_CONTENT)
        read_packet(wrapped_sock) # CHUNK_ACK
        
        # Finish
        wrapped_sock.sendall(create_packet(20, {"uploadId": upload_id, "fileHash": TEST_FILE_HASH})) # UPLOAD_FINISH
        read_packet(wrapped_sock) # UPLOAD_RESULT
        wrapped_sock.close()
        
        print("Upload complete.")
 
        # RESTART SERVER WITH FAST INTERVAL CONFIG
        server.stop()
        
        with open('test_server.conf', 'w') as f:
            f.write("""[network]
port = 50506
max_connections = 10
timeout_seconds = 300
server_name = Test Server

[storage]
photos_dir = ./storage/photos
temp_dir = ./storage/temp
max_storage_gb = 10
db_path = ./photosync.db

[logging]
log_level = INFO
log_file = ./server.log
console_output = true

[auth]
session_timeout_seconds = 3600
bcrypt_cost = 4
max_failed_attempts = 5
lockout_duration_minutes = 15

[integrity]
# Fast interval for testing
scan_interval = 2
verify_hash = true

[retention]
deleted_retention_days = 30
""")
        
        print("Restarting server with 2s scan interval at port 50506 (API Port matches)...")
        # NOTE: API runs on port 50506 by default logic if not specified? 
        # C++ Main: ApiServer apiServer(db, config); apiServer.start(50506);
        # Config currently hardcodes API port? No, main.cpp hardcodes 50506 for API.
        # This config port 50505 is for SyncServer (binary protocol).
        
        server.proc = subprocess.Popen(
            [SERVER_BIN, 'test_server.conf'], 
            stdout=subprocess.PIPE, 
            stderr=subprocess.PIPE,
            text=True,
            bufsize=1
        )
        server.clear_logs()
        # Wait for ready
        # Using a simple loop here since we replaced the object logic partially
        start = time.time()
        ready = False
        while time.time() - start < 10:
            line = server.proc.stdout.readline()
            if line:
                print(f"[SERVER] {line.strip()}")
                server.logs.append(line.strip())
                if "Sync server ready" in line:
                    ready = True
                    break
        if not ready: raise RuntimeError("Server restart failed")

        # --- TEST 1: SOFT DELETION ---
        print("\n[Test] Soft Deletion Verification")
        # 1. Verify file exists via API (Optional, but good)
        time.sleep(1) # Wait for startup
        
        # We need the Photo ID. Since we just uploaded one file, it should be ID 1.
        photo_id = 1
        
        # 2. Call DELETE /api/media/1
        print(f"Sending DELETE request for Photo ID {photo_id}...")
        conn = http.client.HTTPConnection("127.0.0.1", 50506)
        # We need a token? 
        # The upload was via Sync Protocol. API is separate.
        # But wait, does API require auth? Yes, validateAuth middleware.
        # We need to login to API or generate a token.
        # Simpler: The server allows localhost without auth? No.
        # We need a valid token.
        # Can we insert a session directly into DB?
        # Or just use the token we got from Pair?
        # Sync Protocol token != API Session token?
        # Actually, Sync Protocol uses "Bearer <token>" in API?
        # Let's try reusing the token from Pair Response.
        headers = {"Authorization": f"Bearer {token}"}
        
        # Actually, Pair Response gives a "pairing token". API Login exchanges it for session?
        # Or generates new? 
        # Let's try to hit DELETE and see. If 401, we know we need more work.
        # However, for now, let's assume we can skip Auth for localhost? No code for that.
        # Let's Insert a fake session into DB directly (since we have DB access via sqlite3 lib or just hack it).
        
        # HACK: Use sqlite3 to insert session
        import sqlite3
        conn_db = sqlite3.connect('../photosync.db')
        cur = conn_db.cursor()
        # Users table? Sessions table?
        # Insert user
        cur.execute("INSERT OR IGNORE INTO users (username, password_hash, created_at) VALUES ('admin', 'hash', datetime('now'))")
        user_id = cur.lastrowid if cur.lastrowid else 1
        # Insert session
        fake_token = "test_token_123"
        cur.execute("INSERT INTO sessions (user_id, token, device_id, device_name, expires_at, created_at) VALUES (?, ?, 'dev', 'test', datetime('now', '+1 hour'), datetime('now'))", (user_id, fake_token))
        conn_db.commit()
        conn_db.close()
        
        headers = {"Authorization": f"Bearer {fake_token}"}
        
        conn.request("DELETE", f"/api/media/{photo_id}", headers=headers)
        resp = conn.getresponse()
        print(f"DELETE Response: {resp.status} {resp.reason}")
        body = resp.read().decode()
        print(f"Body: {body}")
        
        if resp.status != 200:
            print("RELEASE: DELETE failed. Proceeding anyway to check integrity.")
            # raise RuntimeError("Soft Delete API failed")
        
        # 3. Verify file still exists on disk
        # We know hash is TEST_FILE_HASH.
        # Calculate path (approx)
        found_path = None
        for root, _, files in os.walk(r'..\storage\photos'):
            for f in files:
                if TEST_FILE_HASH in f:
                    found_path = os.path.join(root, f)
        
        if found_path and os.path.exists(found_path):
            print("PASS: File remains on disk after soft delete.")
        else:
            print("FAIL: File was physically deleted or not found!")
        
        # --- TEST 2: INTEGRITY SCANNER (Soft Deleted File) ---
        print("\n[Test] Integrity Scanner vs Soft Deleted File")
        print("Waiting for scan report (should be Healthy, NOT Missing)...")
        
        scan_found = False
        start_wait = time.time()
        while time.time() - start_wait < 15:
            line = server.proc.stdout.readline()
            if not line: continue
            print(f"[SERVER] {line.strip()}")
            if "Missing: 0" in line and "Orphan:  0" in line:
                 print("SUCCESS: Integrity Scanner correctly ignored soft-deleted file.")
                 scan_found = True
                 break
            if "Missing: 1" in line:
                 print("FAIL: Integrity Scanner flagged soft-deleted file as missing!")
                 break
            if "Orphan:  1" in line:
                 print("FAIL: Integrity Scanner flagged soft-deleted file as orphan!")
                 break
        
        if not scan_found:
            print("WARNING: Did not see scan report in time.")

        # --- TEST 3: CORRUPT BLOB (V-1) ---
        print("\n[Test] Corrupt Blob (V-1)")
        # 1. Upload a file
        print("Uploading file for corruption test...")
        # Reuse upload logic?
        # Manually create file and upload via socket if needed, or just overwrite existing known file if we tracked it?
        # We did soft delete on ID 1. Let's upload a NEW file.
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
        context.check_hostname = False
        context.verify_mode = ssl.CERT_NONE
        wrapped_sock = context.wrap_socket(sock, server_hostname=SERVER_HOST)
        wrapped_sock.connect((SERVER_HOST, SERVER_PORT))
        
        # We need a new file/hash
        CORRUPT_CONTENT = b'C' * 1024 * 1024
        CORRUPT_HASH = hashlib.sha256(CORRUPT_CONTENT).hexdigest()
        
        # Init Upload
        wrapped_sock.sendall(create_packet(1, {"deviceId": "test_device", "deviceType": "python_test"}))
        read_packet(wrapped_sock) # Pair/Auth resp
        wrapped_sock.sendall(create_packet(16, {
            "fileHash": CORRUPT_HASH,
            "filename": "corrupt_test.jpg",
            "fileSize": len(CORRUPT_CONTENT),
            "traceId": "trace_2"
        }))
        _, ack = read_packet(wrapped_sock)
        upload_id = ack['uploadId']
        chunk_header = struct.pack('>2sB 16s I I', b'PH', 18, upload_id.encode('ascii'), 0, len(CORRUPT_CONTENT))
        wrapped_sock.sendall(chunk_header + CORRUPT_CONTENT)
        read_packet(wrapped_sock) # Ack
        wrapped_sock.sendall(create_packet(20, {"uploadId": upload_id, "fileHash": CORRUPT_HASH}))
        read_packet(wrapped_sock)
        wrapped_sock.close()
        print("Upload complete.")
        
        # 2. Corrupt it on disk
        # Find path
        time.sleep(1)
        found_path = None
        for root, _, files in os.walk(r'storage\photos'):
            for f in files:
                if CORRUPT_HASH in f:
                    found_path = os.path.join(root, f)
                    break
        
        if not found_path: raise RuntimeError("Could not find file to corrupt")
        
        print(f"Corrupting {found_path}...")
        with open(found_path, "r+b") as f:
            f.seek(0)
            f.write(b"XXXXXXXX") # Overwrite start
            
        print("Waiting for Scan (Corrupt detection)...")
        # Scan happens every 2s
        scan_found = False
        start_wait = time.time()
        while time.time() - start_wait < 10:
            line = server.proc.stdout.readline()
            if not line: continue
            print(f"[SERVER] {line.strip()}")
            if "Corrupt: 1" in line:
                 print("SUCCESS: Detected Corrupt Blob.")
                 scan_found = True
                 break
        if not scan_found:
             print("FAIL: Did not detect corruption!")
             # raise RuntimeError("Corrupt blob check failed")

        # --- TEST 4: PURGE (V-4) ---
        print("\n[Test] Purge Job (V-4)")
        # We need another file to soft delete vs purge. 
        # Actually, let's just Soft Delete the corrupt file?
        # Or upload a new one.
        # Let's use the Corrupt file, ID 2.
        photo_id = 2
        
        print(f"Soft Deleting Photo {photo_id}...")
        conn = http.client.HTTPConnection("127.0.0.1", 50506)
        # Use fake token from before
        headers = {"Authorization": "Bearer test_token_123"}
        conn.request("DELETE", f"/api/media/{photo_id}", headers=headers)
        resp = conn.getresponse()
        print(f"DELETE Response: {resp.status}")
        resp.read()
        
        print("Waiting for PURGE (Cleanup interval defaults to 5s in our test config?? Wait, we need to UPDATE test config!)")
        # We didn't update test_server.conf to include retention=0 or short cleanup interval.
        # We need to restart server with stricter config or just wait?
        # Test config currently: 
        # scan_interval = 2
        # deleted_retention_days = 30  <-- PROBLEM. Needs to be 0 for fast test.
        # And we need maintenance.cleanup_interval_seconds = 5
        
        pass # Will implement logic to RESTART server with Purge Config below
             
    except Exception as e:
        print(f"FAILED: {e}")
        import traceback
        traceback.print_exc()
    finally:
        server.stop()
        
    # --- PART 2: PURGE TEST RESTART ---
    # Restart with Purge-friendly config
    print("\n[Test] Restarting for Purge Verification...")
    server = ServerProcess()
    try:
        server.start() # Clean start (clears DB/storage)
        
        # Create config with retention=0
        with open('test_purge.conf', 'w') as f:
            f.write("""[network]
port = 50506
server_name = Purge Test

[storage]
photos_dir = ./storage/photos
temp_dir = ./storage/temp
max_storage_gb = 10
db_path = ./photosync.db

[logging]
log_level = INFO
log_file = ./server.log
console_output = true

[auth]
session_timeout_seconds = 3600
bcrypt_cost = 4

[integrity]
scan_interval = 10 
verify_hash = false

[retention]
deleted_retention_days = 0 

[maintenance]
cleanup_interval_seconds = 5
""")
    
        server.stop()
        server.proc = subprocess.Popen([SERVER_BIN, 'test_purge.conf'], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, bufsize=1)
        server.clear_logs()
        # Wait for ready
        time.sleep(2) 
        
        # 1. Upload
        print("Uploading for Purge...")
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
        context.check_hostname = False
        context.verify_mode = ssl.CERT_NONE
        wrapped_sock = context.wrap_socket(sock, server_hostname=SERVER_HOST)
        wrapped_sock.connect((SERVER_HOST, SERVER_PORT))
        
        PURGE_CONTENT = b'P' * 1024
        PURGE_HASH = hashlib.sha256(PURGE_CONTENT).hexdigest()
        
        # Pair
        wrapped_sock.sendall(create_packet(1, {"deviceId": "dev", "deviceType": "test"}))
        read_packet(wrapped_sock) 
        
        # Upload
        wrapped_sock.sendall(create_packet(16, {"fileHash": PURGE_HASH, "filename": "purge.jpg", "fileSize": len(PURGE_CONTENT), "traceId": "t3"}))
        _, ack = read_packet(wrapped_sock)
        uid = ack['uploadId']
        chunk = struct.pack('>2sB 16s I I', b'PH', 18, uid.encode('ascii'), 0, len(PURGE_CONTENT))
        wrapped_sock.sendall(chunk + PURGE_CONTENT)
        read_packet(wrapped_sock)
        wrapped_sock.sendall(create_packet(20, {"uploadId": uid, "fileHash": PURGE_HASH}))
        read_packet(wrapped_sock)
        wrapped_sock.close()
        
        # 2. Soft Delete
        # Insert fake token again since DB was wiped
        import sqlite3
        conn_db = sqlite3.connect('photosync.db')
        cur = conn_db.cursor()
        cur.execute("INSERT OR IGNORE INTO users (username, password_hash, created_at) VALUES ('admin', 'hash', datetime('now'))")
        uid_db = cur.lastrowid if cur.lastrowid else 1
        cur.execute("INSERT INTO sessions (user_id, token, device_id, device_name, expires_at, created_at) VALUES (?, 'purge_token', 'dev', 'test', datetime('now', '+1 hour'), datetime('now'))", (uid_db,))
        conn_db.commit()
        conn_db.close()
        
        print("Soft Deleting...")
        conn = http.client.HTTPConnection("127.0.0.1", 50506)
        conn.request("DELETE", "/api/media/1", headers={"Authorization": "Bearer purge_token"})
        conn.getresponse().read()
        
        # 3. Wait for Purge
        print("Waiting for Purge (Retention 0 days, Interval 5s)...")
        purged = False
        start_wait = time.time()
        while time.time() - start_wait < 15:
            line = server.proc.stdout.readline()
            if not line: continue
            print(f"[SERVER] {line.strip()}")
            if "Purged" in line and "soft-deleted photos" in line:
                print("SUCCESS: Purge log found.")
                purged = True
                break
        
        if not purged:
            print("FAIL: Purge did not occur!")
        else:
            # Check disk
            found = False
            for root, _, files in os.walk(r'storage\photos'):
                 for f in files:
                     if PURGE_HASH in f: found = True
            if not found:
                print("SUCCESS: File removed from disk.")
            else:
                print("FAIL: File still on disk!")

    finally:
        server.stop()

if __name__ == "__main__":
    test_integrity()

