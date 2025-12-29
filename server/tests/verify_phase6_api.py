import requests
import json
import time
import os
import sys
import sqlite3
from datetime import datetime

# Configuration
API_BASE = "https://localhost:50506/api"

def seed_db():
    print("--- Seeding DB with mock data ---")
    try:
        conn = sqlite3.connect("photosync.db")
        cursor = conn.cursor()
        
        # 1. Insert Mock Client
        # Check if exists first
        cursor.execute("SELECT id FROM clients WHERE device_id='mock_device_1'")
        if not cursor.fetchone():
            cursor.execute("INSERT INTO clients (device_id, user_name, last_seen, total_photos) VALUES (?, ?, ?, ?)",
                           ('mock_device_1', 'Test Device', datetime.now().isoformat(), 10))
            print("   Inserted mock_device_1")
        
        # Get client ID
        cursor.execute("SELECT id FROM clients WHERE device_id='mock_device_1'")
        client_id = cursor.fetchone()[0]

        # 2. Insert Change Log (Uploads 24h)
        # Seed 5 uploads
        for i in range(5):
             cursor.execute("INSERT INTO change_log (op, media_id, blob_hash, change_id, device_id, changed_at) VALUES (?, ?, ?, ?, ?, ?)",
                            ('CREATE', 100+i, f'hash{i}', int(time.time()*1000)+i, 'mock_device_1', datetime.now().isoformat()))
        print("   Inserted 5 mocked uploads")

        # 3. Insert Error Log (Failures 24h)
        # Seed 2 failures
        for i in range(2):
            cursor.execute("INSERT INTO error_logs (code, message, severity, device_id, timestamp) VALUES (?, ?, ?, ?, ?)",
                           (500, f'Mock Error {i}', 'ERROR', 'mock_device_1', datetime.now().isoformat()))
        print("   Inserted 2 mocked errors")

        conn.commit()
        conn.close()
        return client_id
    except Exception as e:
        print(f"[FAIL] DB Seeding failed: {e}")
        return None

def run_tests():
    session = requests.Session()
    session.verify = False 
    
    print("=== Phase 6 API Verification ===")

    # Seed Data
    mock_client_id = seed_db()

    # 1. Login
    print("\n--- 1. Authentication ---")
    try:
        resp = session.post(f"{API_BASE}/auth/login", json={"username": "admin", "password": "Rsv9Q@Bpyg!D%jUz"})
        if resp.status_code == 200:
            token = resp.json().get("sessionToken")
            if token:
                session.headers.update({"Authorization": f"Bearer {token}"})
                print("[PASS] Login successful")
            else:
                print("[FAIL] Login response missing token")
                return False
        else:
            print(f"[FAIL] Login failed: {resp.status_code} {resp.text}")
            return False
    except Exception as e:
        print(f"[FAIL] Connection error: {e}")
        return False

    # 2. GET /api/health
    print("\n--- 2. System Health ---")
    try:
        resp = session.get(f"{API_BASE}/health")
        if resp.status_code == 200:
            data = resp.json()
            # Updated keys per implementation
            required_keys = ["uptime", "version", "diskFree", "dbSize", "pendingUploads", "activeSessions", "lastIntegrityScan", "integrityIssues"]
            missing = [k for k in required_keys if k not in data]
            if not missing:
                print("[PASS] GET /api/health returned all required fields")
                print(f"   Uptime: {data['uptime']}s, DB Size: {data['dbSize']} bytes")
                print(f"   Integrity Issues: {data['integrityIssues']}")
            else:
                print(f"[FAIL] Missing keys in health response: {missing}")
        else:
            print(f"[FAIL] GET /api/health: {resp.status_code}")
    except Exception as e:
        print(f"[FAIL] Error testing health: {e}")

    # 3. GET /api/clients (24h Stats)
    print("\n--- 3. Client Stats (24h) ---")
    try:
        resp = session.get(f"{API_BASE}/clients")
        if resp.status_code == 200:
            data = resp.json()
            clients = data.get("clients", [])
            print(f"   Found {len(clients)} clients")
            
            target = next((c for c in clients if c["deviceId"] == "mock_device_1"), None)
            
            if target:
                if "uploads24h" in target and "failures24h" in target:
                     print(f"   Stats: Uploads={target['uploads24h']}, Failures={target['failures24h']}")
                     if target["uploads24h"] >= 5 and target["failures24h"] >= 2:
                         print(f"[PASS] Client has expected 24h stats")
                     else:
                         print(f"[FAIL] Client stats mismatch (Expected >=5, >=2)")
                else:
                    print(f"[FAIL] Client missing 24h stats fields")
            else:
                print("[WARN] Mock client not found in response")
        else:
            print(f"[FAIL] GET /api/clients: {resp.status_code}")
    except Exception as e:
        print(f"[FAIL] Error testing clients: {e}")

    # 4. GET /api/errors (Filtering)
    print("\n--- 4. Error Filtering ---")
    try:
        # 4a. No filters
        resp = session.get(f"{API_BASE}/errors")
        if resp.status_code == 200:
            print("[PASS] GET /api/errors (No filter)")
            errors = resp.json().get("errors", [])
            if errors and "severity" in errors[0]:
                 print("[PASS] Error struct has 'severity' field")
            
        # 4b. Filter by Level
        # We seeded with 'ERROR', so filtering by 'WARN' should return 0 (or some if system generated)
        # Filtering by 'ERROR' should return at least 2.
        resp = session.get(f"{API_BASE}/errors", params={"level": "ERROR"})
        if resp.status_code == 200:
            errs = resp.json().get("errors", [])
            print(f"   Found {len(errs)} ERRORs") 
            if len(errs) >= 2:
                print("[PASS] Filtering by level works")
            else:
                print("[WARN] Filtering returned fewer errors than seeded")
        else:
            print(f"[FAIL] GET /api/errors?level=ERROR: {resp.status_code}")

    except Exception as e:
        print(f"[FAIL] Error testing errors: {e}")

    # 5. GET /api/integrity/details
    print("\n--- 5. Integrity Details ---")
    try:
        resp = session.get(f"{API_BASE}/integrity/details", params={"type": "missing", "limit": 10})
        if resp.status_code == 200:
            data = resp.json()
            if "type" in data and "items" in data:
                print(f"[PASS] GET /api/integrity/details returned type '{data['type']}' with {len(data['items'])} items")
            else:
                print(f"[FAIL] Malformed details response: {data}")
        else:
            print(f"[FAIL] GET /api/integrity/details: {resp.status_code}")
    except Exception as e:
        print(f"[FAIL] Error testing integrity details: {e}")

    # 6. POST /api/devices/:id/revoke
    print("\n--- 6. Revoke Client ---")
    if mock_client_id:
        try:
            print(f"   Revoking client ID {mock_client_id}...")
            resp = session.post(f"{API_BASE}/devices/{mock_client_id}/revoke")
            if resp.status_code == 200:
                print("[PASS] Revoke call successful")
            else:
                 print(f"[FAIL] Revoke failed: {resp.status_code}")
        except Exception as e:
             print(f"[FAIL] Error revoking client: {e}")
    else:
        print("[SKIP] No client ID available to revoke")

    print("\n=== Verification Complete ===")
    return True

if __name__ == "__main__":
    try:
        import urllib3
        urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)
        run_tests()
    except KeyboardInterrupt:
        print("\nInterrupted.")
