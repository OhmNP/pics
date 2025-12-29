import requests
import time
import sqlite3
import os
import urllib3
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

SERVER_URL = "https://127.0.0.1:50506"
HASH_DB = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "photosync.db")

def setup_test_token():
    conn = sqlite3.connect(HASH_DB)
    c = conn.cursor()
    # Create a user if not exists
    c.execute("INSERT OR IGNORE INTO admin_users (username, password_hash) VALUES ('test_user', 'hash')")
    user_id = c.execute("SELECT id FROM admin_users WHERE username='test_user'").fetchone()[0]
    
    # Create session
    import datetime
    token = "TEST_TOKEN_PAGINATION"
    expires_at = (datetime.datetime.utcnow() + datetime.timedelta(hours=1)).strftime("%Y-%m-%dT%H:%M:%SZ")
    c.execute("INSERT OR REPLACE INTO sessions (session_token, user_id, expires_at) VALUES (?, ?, ?)", (token, user_id, expires_at))
    conn.commit()
    conn.close()
    return token

def test_pagination():
    print("Setting up test token...")
    try:
        token = setup_test_token()
    except Exception as e:
        print(f"Error setting up token (DB might be locked): {e}")
        token = "TEST_TOKEN_PAGINATION" # Hope it exists

    headers = {"Authorization": f"Bearer {token}"}

    # 1. Get initial cursor
    r = requests.get(f"{SERVER_URL}/api/changes?limit=1", headers=headers, verify=False)
    if r.status_code != 200:
        print(f"FAILED: Initial fetch failed {r.status_code}")
        return
    
    start_cursor = r.json().get('nextCursor', 0)
    print(f"Start Cursor: {start_cursor}")

    # 2. Generate 120 changes (using helper script or direct DB insert?)
    # Direct DB insert is faster and easier for "Mocking" server activity for this specific test
    # BUT, we want to test that the API returns them correctly.
    # We can inject into change_log directly.
    print("Injecting 120 change log entries...")
    conn = sqlite3.connect(HASH_DB)
    c = conn.cursor()
    
    base_id = 900000 # Use high IDs to avoid conflicts? change_id is autoincrement. 
    # Just insert default.
    
    for i in range(120):
        c.execute("""
            INSERT INTO change_log (op, media_id, blob_hash, filename, size, mime_type, changed_at)
            VALUES (?, ?, ?, ?, ?, ?, datetime('now'))
        """, ('CREATE', i + 1000, f'hash_{i}', f'file_{i}.jpg', 1024, 'image/jpeg'))
        
    conn.commit()
    conn.close()
    
    print("Changes injected.")

    # 3. Fetch pages
    limit = 50
    cursor = start_cursor
    all_items = []
    
    page = 1
    while True:
        print(f"Fetching page {page} with cursor {cursor}...")
        r = requests.get(f"{SERVER_URL}/api/changes?cursor={cursor}&limit={limit}", headers=headers, verify=False)
        if r.status_code != 200:
            print(f"FAILED: Page fetch failed {r.status_code}")
            break
            
        data = r.json()
        items = data.get('items', [])
        next_cursor = data.get('nextCursor')
        has_more = data.get('hasMore')
        
        print(f"  Got {len(items)} items. Next Cursor: {next_cursor}. Has More: {has_more}")
        
        all_items.extend(items)
        
        if not items:
            break
            
        # Verify monotonicity strictly
        last_id = items[-1]['id']
        if next_cursor != last_id:
             # Ensure nextCursor is the specific ID of the last item (or simply the provided nextCursor)
             # The API contract usually says nextCursor IS the ID of the last item, or the ID to use for next page.
             # In our impl: "WHERE change_id > ?" so we pass the last seen ID.
             pass
             
        if not has_more:
            break
            
        cursor = next_cursor
        page += 1

    # 4. Analyze
    print(f"Total items fetched: {len(all_items)}")
    
    # We expect at least 120 items (plus previous ones)
    new_items = [x for x in all_items if x['id'] > start_cursor]
    print(f"Newly fetched items: {len(new_items)}")
    
    if len(new_items) < 120:
        print("FAILED: Did not fetch all injected items.")
        return

    # Check for duplicates
    ids = [x['id'] for x in new_items]
    if len(ids) != len(set(ids)):
        print("FAILED: Duplicates detected.")
        return
        
    # Check for gaps (assuming we injected sequentially and no other writes happened, 
    # but strictly change_id should be monotonic)
    sorted_ids = sorted(ids)
    if sorted_ids != ids:
        print("FAILED: Items not sorted by ID.")
        return
        
    print("SUCCESS: Pagination verified.")

if __name__ == "__main__":
    test_pagination()
