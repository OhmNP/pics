import subprocess
import time
import os
import shutil
import threading
import sys

# Configuration
SERVER_BIN = r'build\Release\PhotoSyncServer.exe'
CLIENT_BIN = r'build\Release\MockClientSSL.exe'

class ServerProcess:
    def __init__(self):
        self.proc = None
        self.stop_event = threading.Event()
        self.logs = []
        self.lock = threading.Lock()

    def start(self):
        # Clean storage
        if os.path.exists('photosync.db'): os.remove('photosync.db')
        if os.path.exists('server.log'): os.remove('server.log')
        if os.path.exists('storage'): shutil.rmtree('storage')
        
        # Create config
        with open('test_cpp.conf', 'w') as f:
            f.write("""[network]
port = 50505
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
scan_interval = 3600
verify_hash = true
[retention]
deleted_retention_days = 30
""")

        # Start server
        self.proc = subprocess.Popen(
            [SERVER_BIN, 'test_cpp.conf'], 
            stdout=subprocess.PIPE, 
            stderr=subprocess.PIPE,
            text=True,
            bufsize=1
        )
        
        self.reader_thread = threading.Thread(target=self._read_stdout)
        self.reader_thread.start()
        
        if not self.wait_for_log("Sync server ready", timeout=10):
            print("Server failed to start!")
            self.stop()
            raise RuntimeError("Server start timeout")

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

    def stop(self):
        self.stop_event.set()
        if self.proc:
            self.proc.terminate()
            try:
                self.proc.wait(timeout=5)
            except:
                self.proc.kill()

if __name__ == "__main__":
    server = ServerProcess()
    try:
        print("Starting Server...")
        server.start()
        print("Server Started. Running MockClientSSL...")
        
        # Run MockClientSSL
        # usage: MockClientSSL <host> <port> [mode]
        # We assume default mode does a handshake and maybe a ping or upload.
        # Wait, I don't know MockClientSSL args exactly. 
        # Source code says: MockClientSSL.cpp usually takes host port.
        
        client_proc = subprocess.run([CLIENT_BIN, '127.0.0.1', '50505'], capture_output=True, text=True)
        print(f"Client RC: {client_proc.returncode}")
        print(f"Client STDOUT:\n{client_proc.stdout}")
        print(f"Client STDERR:\n{client_proc.stderr}")
        
        if client_proc.returncode == 0:
            print("SUCCESS: C++ Client connected.")
        else:
            print("FAILURE: C++ Client failed.")

    except Exception as e:
        print(f"Error: {e}")
    finally:
        server.stop()
