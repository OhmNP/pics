import requests
import time
import subprocess
import os
import sys

# Configuration
API_URL = "http://localhost:50506"
SERVER_EXE = os.path.join("build", "Debug", "PhotoSyncServer.exe")

def wait_for_server(url, retries=10):
    print(f"Waiting for server at {url}...")
    for i in range(retries):
        try:
            response = requests.get(f"{url}/api/stats", verify=False)
            if response.status_code == 200:
                print("Server is up!")
                return True
        except requests.exceptions.ConnectionError:
            pass
        time.sleep(1)
    return False

def test_api():
    session = requests.Session()
    session.verify = False 
    base_url = "https://localhost:50506"

    print("=== Starting API Integration Tests ===")
    
    # 0. Negative Test: Login Fail
    print("--- Negative Test: Invalid Login ---")
    bad_login = {"username": "admin", "password": "wrongpassword"}
    resp = session.post(f"{base_url}/api/auth/login", json=bad_login)
    if resp.status_code == 200:
        # Check if it returns error json
        data = resp.json()
        if "error" in data:
            print("[PASS] Invalid Login handled gracefully")
        else:
             print(f"[FAIL] Invalid Login returned success: {data}")
    else:
        # 401 is also acceptable
        if resp.status_code in [401, 403]:
             print("[PASS] Invalid Login rejected with status", resp.status_code)
        else:
             print(f"[FAIL] Invalid Login unexpected status: {resp.status_code}")

    # 1. Status Check
    try:
        resp = session.get(f"{base_url}/api/stats")
        if resp.status_code == 200:
            print("[PASS] GET /api/stats")
            print("   Response:", resp.json())
        else:
            print(f"[FAIL] GET /api/stats - Status: {resp.status_code}")
            return False
    except Exception as e:
        print(f"[FAIL] Connection Error: {e}")
        # Try HTTP just in case
        base_url = "http://localhost:50506"
        print(f"Retrying with HTTP {base_url}...")
        try:
            resp = session.get(f"{base_url}/api/stats")
            if resp.status_code == 200:
                print("[PASS] GET /api/stats (HTTP)")
            else:
                print(f"[FAIL] GET /api/stats (HTTP) - Status: {resp.status_code}")
                return False
        except:
             print("[FATAL] Could not connect via HTTPS or HTTP")
             return False

    # 2. Login
    login_payload = {"username": "admin", "password": "password123"} # Default is admin123 actually
    # DatabaseManager::insertInitialAdminUser says "admin123"
    login_payload = {"username": "admin", "password": "admin123"}
    
    resp = session.post(f"{base_url}/api/auth/login", json=login_payload)
    if resp.status_code == 200:
        data = resp.json()
        if "sessionToken" in data:
            print("[PASS] POST /api/auth/login")
            token = data["sessionToken"]
            session.headers.update({"Authorization": f"Bearer {token}"})
        else:
             print("[FAIL] Login response missing sessionToken")
             print("Response:", data)
             return False
    else:
        print(f"[FAIL] Login failed: {resp.status_code} {resp.text}")
        return False

    # 3. Get Clients (Authenticated)
    resp = session.get(f"{base_url}/api/clients")
    if resp.status_code == 200:
        print(f"[PASS] GET /api/clients - Found {len(resp.json())} clients")
    else:
        print(f"[FAIL] GET /api/clients: {resp.status_code}")
        return False

    # 4. Get Photos (Authenticated)
    resp = session.get(f"{base_url}/api/photos")
    if resp.status_code == 200:
        print(f"[PASS] GET /api/photos - Found {len(resp.json())} photos")
    else:
        print(f"[FAIL] GET /api/photos: {resp.status_code}")
        return False
        
    print("=== All API Tests Passed ===")
    return True

if __name__ == "__main__":
    # Ensure server is running?
    # For CI/Automation, we should spawn it.
    # But for now, we assume user might have it running, or we spawn it.
    
    server_process = None
    try:
        # Check if running
        try:
             requests.get("https://localhost:50506/api/status", verify=False, timeout=1)
             print("Server already running.")
        except:
             print("Starting server...")
             if os.path.exists(SERVER_EXE):
                 server_process = subprocess.Popen([SERVER_EXE], cwd=os.getcwd())
                 time.sleep(5)
             else:
                 print(f"Server binary not found at {SERVER_EXE}")
                 sys.exit(1)
                 
        if test_api():
            sys.exit(0)
        else:
            sys.exit(1)
            
    finally:
        if server_process:
            server_process.terminate()
