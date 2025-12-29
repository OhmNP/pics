import requests
import time
import hashlib
import os

SERVER_URL = "https://127.0.0.1:50506"
import urllib3
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

ADMIN_USER = "admin"
# You might need to check logs for the initial password if you don't have it
# Assuming we can login or use a known user. 
# For this test, we'll try to login as admin.
ADMIN_PASS = "admin" # Replace if needed, but for local dev env usually standard

def login():
    try:
        # Try to create initial admin if not exists (backend does this on start)
        # Login
        # In a real scenario we'd need the password. 
        # If we can't login, we might need to bypass auth or assume a token.
        # Let's assume we can get a token or use a test token if possible.
        # Actually, let's use the 'verify_integrity.py' approach if it has login helpers
        # Or just try standard credentials.
        
        # NOTE: If we can't login without a random password, we might need to update the DB directly 
        # to set a known password or read the password from a file.
        # For now, let's try a direct DB insertion of a known user if login fails?
        # Or just assume the user can run this after setting changes.
        pass
    except:
        pass

# Helper to get auth token
# Since we don't know the random password, we will manually insert a session into the DB
# using sqlite3 command line or similar if we were completely external.
# BUT, we are on the same machine.
# Actually, the quickest way is to disable auth for the test or use the 'admin' user if we knew the pass.
# Let's try to register a new client and use that? No, clients don't use this API.
# The API is for dashboard.
# We will use a "Test Token" that we insert into the DB via the DatabaseManager if we could.
# Since we can't easily run C++ code from here to insert, we'll rely on the server having a known state
# OR we can assume the user has a valid token.
# Let's try to assume a token 'TEST_TOKEN' that we manually insert into the DB file.

import sqlite3

# Adjust path to where the server running in build/Release will create/find the DB
# Assuming we run the script from 'server/tests' and server runs in 'server/build/Release'
# The server config says "./photosync.db", so it will be in CWD (build/Release).
HASH_DB = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "build", "Release", "photosync.db")

def setup_test_token():
    conn = sqlite3.connect(HASH_DB)
    c = conn.cursor()
    # Create a user if not exists
    c.execute("INSERT OR IGNORE INTO admin_users (username, password_hash) VALUES ('test_user', 'hash')")
    user_id = c.execute("SELECT id FROM admin_users WHERE username='test_user'").fetchone()[0]
    
    # Create session
    import datetime
    token = "TEST_TOKEN_12345"
    expires_at = (datetime.datetime.utcnow() + datetime.timedelta(hours=1)).strftime("%Y-%m-%dT%H:%M:%SZ")
    c.execute("INSERT OR REPLACE INTO sessions (session_token, user_id, expires_at) VALUES (?, ?, ?)", (token, user_id, expires_at))
    conn.commit()
    conn.close()
    return token

def test_changes_feed():
    print(f"Setting up test token in {HASH_DB}...")
    try:
        token = setup_test_token()
    except sqlite3.OperationalError as e:
        print(f"Warning: DB locked or error, assuming token exists. Error: {e}")
        token = "TEST_TOKEN_12345"

    headers = {"Authorization": f"Bearer {token}"}
    
    print("1. Initial Fetch (Get Cursor)")
    r = requests.get(f"{SERVER_URL}/api/changes", headers=headers, verify=False)
    if r.status_code != 200:
        print(f"FAILED: Initial fetch failed {r.status_code} {r.text}")
        return
    
    data = r.json()
    initial_cursor = data['nextCursor']
    print(f"Initial Cursor: {initial_cursor}")
    
    print(f"Initial Cursor: {initial_cursor}")
    
    print("2. Create Action (Upload Photo)")
    # Run MockClientSSL.exe to upload 1 photo
    client_exe = os.path.join(os.path.dirname(HASH_DB), "MockClientSSL.exe")
    if not os.path.exists(client_exe):
        print(f"MockClientSSL.exe not found at {client_exe}")
        return

    import subprocess
    # Run client to upload 1 photo
    print(f"Running {client_exe} --photos 1")
    subprocess.run([client_exe, "--photos", "1"], cwd=os.path.dirname(client_exe), capture_output=True)
    
    # Wait a bit for async processing if any (though upload is sync usually)
    time.sleep(2)
    
    print("3. Fetch Changes")
    r = requests.get(f"{SERVER_URL}/api/changes?cursor={initial_cursor}", headers=headers, verify=False)
    if r.status_code != 200:
        print(f"FAILED: Fetch changes failed {r.status_code} {r.text}")
        return
        
    data = r.json()
    items = data.get('items', [])
    print(f"Fetched {len(items)} changes")
    
    found_create = False
    for item in items:
        print(f"Change: {item}")
        if item['op'] == 'CREATE':
            found_create = True
            
    if found_create:
        print("SUCCESS: Found CREATE event")
    else:
        print("FAILED: No CREATE event found")

if __name__ == "__main__":
    try:
        test_changes_feed()
    except Exception as e:
        print(f"Error: {e}")
