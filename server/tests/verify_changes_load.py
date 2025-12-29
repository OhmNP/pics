import sqlite3
import time
import os
import random

HASH_DB = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "build", "Release", "photosync.db")

def test_load():
    print(f"Connecting to {HASH_DB}...")
    conn = sqlite3.connect(HASH_DB)
    c = conn.cursor()
    
    print("Generating 100k change log entries...")
    start_time = time.time()
    
    # Bulk insert
    batch_size = 10000
    total = 100000
    
    for i in range(0, total, batch_size):
        params = []
        for j in range(batch_size):
            # 'CREATE', 'UPDATE', 'DELETE'
            op = random.choice(['CREATE', 'UPDATE', 'DELETE'])
            params.append((op, i+j, f'hash_{i+j}', f'file_{i+j}.jpg', 1024, 'image/jpeg'))
            
        c.executemany("""
            INSERT INTO change_log (op, media_id, blob_hash, filename, size, mime_type, changed_at)
            VALUES (?, ?, ?, ?, ?, ?, datetime('now'))
        """, params)
        conn.commit()
        print(f"Inserted {i+batch_size}...")
        
    duration = time.time() - start_time
    print(f"Insertion took {duration:.2f}s ({(total/duration):.0f} inserts/sec)")
    
    # Optimize/Analyze not strictly needed for SQLite usually but good for stats
    # c.execute("ANALYZE") 
    
    # Benchmark Queries
    print("Benchmarking Queries...")
    
    # 1. Range Scan at beginning
    start = time.time()
    c.execute("SELECT * FROM change_log WHERE change_id > 0 LIMIT 50")
    c.fetchall()
    print(f"Query (Beginning) took: {(time.time() - start)*1000:.2f}ms")
    
    # 2. Range Scan in middle
    mid_id = total // 2
    start = time.time()
    c.execute("SELECT * FROM change_log WHERE change_id > ? LIMIT 50", (mid_id,))
    c.fetchall()
    print(f"Query (Middle) took: {(time.time() - start)*1000:.2f}ms")
    
    # 3. Range Scan at end
    end_id = total - 100
    start = time.time()
    c.execute("SELECT * FROM change_log WHERE change_id > ? LIMIT 50", (end_id,))
    c.fetchall()
    print(f"Query (End) took: {(time.time() - start)*1000:.2f}ms")
    
    conn.close()
    print("SUCCESS: Load test complete.")

if __name__ == "__main__":
    test_load()
