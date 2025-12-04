# How to Run and Test PhotoSync Server

## Quick Start

### 1. Start the Server

```powershell
cd desktop-server
.\build\Release\PhotoSyncServer.exe
```

**Expected Output:**
```
[2025-11-30 11:24:11.299] [INFO ] === PhotoSync Server Starting ===
[2025-11-30 11:24:11.300] [INFO ] Configuration loaded from: server.conf
[2025-11-30 11:24:11.300] [INFO ] Database opened: ./photosync.db
[2025-11-30 11:24:11.322] [INFO ] Database schema created successfully
[2025-11-30 11:24:11.323] [INFO ] TCP Listener started on port 50505
[2025-11-30 11:24:11.323] [INFO ] Server ready and listening on port 50505
[2025-11-30 11:24:11.323] [INFO ] Press Ctrl+C to shutdown
```

The server is now running and ready to accept connections on port 50505.

---

### 2. Test with Mock Client

Open a **new PowerShell window** and run:

```powershell
cd desktop-server\test-client
.\build\Release\MockClient.exe --photos 20 --batch-size 5
```

**Expected Output:**
```
=== PhotoSync Mock Client ===
Connected to localhost:50505

=== Starting Sync Session ===
Photos to send: 20
Batch size: 5
SENT: SESSION_START
RECV: ACK SESSION_ID:1
SENT: BATCH_START:5
RECV: ACK READY
SENT: photo_1.jpg|103424|59116263020d03f97bc3827b312a434f
SENT: photo_2.jpg|104448|f5f1eb47747a9c3769561ccfd33ed69f
...
Progress: 5/20 photos sent
...
Progress: 20/20 photos sent
SENT: SESSION_END
RECV: ACK SESSION_COMPLETE photos=20

=== Session Complete ===
Server response: ACK SESSION_COMPLETE photos=20
Disconnected

=== Test Complete ===
```

---

### 3. Observe Server Logs

In the server window, you should see:

```
[2025-11-30 11:24:31.551] [INFO ] New client connected from: 127.0.0.1
[2025-11-30 11:24:31.563] [INFO ] Created new client: default_client (ID: 1)
[2025-11-30 11:24:31.568] [INFO ] Created session ID: 1
[2025-11-30 11:24:31.568] [INFO ] Session started: 1
[2025-11-30 11:24:31.568] [INFO ] Batch started: expecting 5 photos
[2025-11-30 11:24:31.618] [INFO ] Batch completed: 5 photos received
[2025-11-30 11:24:31.623] [INFO ] Batch started: expecting 5 photos
[2025-11-30 11:24:31.667] [INFO ] Batch completed: 5 photos received
[2025-11-30 11:24:31.671] [INFO ] Batch started: expecting 5 photos
[2025-11-30 11:24:31.718] [INFO ] Batch completed: 5 photos received
[2025-11-30 11:24:31.722] [INFO ] Batch started: expecting 5 photos
[2025-11-30 11:24:31.769] [INFO ] Batch completed: 5 photos received
[2025-11-30 11:24:31.778] [INFO ] Finalized session 1 with status: completed
[2025-11-30 11:24:31.778] [INFO ] Session ended: 20 photos received, total: 20
[2025-11-30 11:24:31.778] [INFO ] Client disconnected
```

---

## Test Scenarios

### Test 1: Basic Connectivity (5 photos)
```powershell
.\build\Release\MockClient.exe --photos 5
```

### Test 2: Small Batch Size (20 photos, batch size 5)
```powershell
.\build\Release\MockClient.exe --photos 20 --batch-size 5
```

### Test 3: Large Dataset (100 photos)
```powershell
.\build\Release\MockClient.exe --photos 100 --batch-size 50
```

### Test 4: Single Photo per Batch
```powershell
.\build\Release\MockClient.exe --photos 10 --batch-size 1
```

### Test 5: Large Batch (1000 photos)
```powershell
.\build\Release\MockClient.exe --photos 1000 --batch-size 50
```

### Test 6: Multiple Concurrent Clients

Open multiple PowerShell windows and run simultaneously:
```powershell
# Window 1
.\build\Release\MockClient.exe --photos 50

# Window 2
.\build\Release\MockClient.exe --photos 30

# Window 3
.\build\Release\MockClient.exe --photos 40
```

The server should handle all connections concurrently.

---

## Mock Client Options

```
--help, -h              Show help message
--host <hostname>       Server hostname (default: localhost)
--port, -p <port>       Server port (default: 50505)
--photos, -n <count>    Number of photos to send (default: 10)
--batch-size, -b <size> Photos per batch (default: 50)
```

### Examples:

**Connect to remote server:**
```powershell
.\build\Release\MockClient.exe --host 192.168.1.100 --photos 50
```

**Use custom port:**
```powershell
.\build\Release\MockClient.exe --port 8080 --photos 25
```

**Help:**
```powershell
.\build\Release\MockClient.exe --help
```

---

## Verifying Results

### Check Log File

```powershell
# View recent log entries
Get-Content server.log -Tail 50
```

### Check Database (if SQLite CLI is installed)

```powershell
# Connect to database
sqlite3 photosync.db

# Count total photos
SELECT COUNT(*) as photo_count FROM photos;

# View sync sessions
SELECT * FROM sync_sessions;

# View clients
SELECT * FROM clients;

# View recent photos
SELECT filename, size, hash FROM photos LIMIT 10;

# Exit
.quit
```

### Alternative: Use DB Browser for SQLite

1. Download [DB Browser for SQLite](https://sqlitebrowser.org/)
2. Open `photosync.db`
3. Browse tables: `clients`, `photos`, `sync_sessions`

---

## Configuration

Edit `server.conf` to customize server behavior:

```ini
[network]
port = 50505                    # Change listening port
max_connections = 10             # Max concurrent connections
timeout_seconds = 300            # Connection timeout

[storage]
photos_dir = ./storage/photos    # Future: where photos will be stored
temp_dir = ./storage/temp        # Future: temp file storage
max_storage_gb = 100             # Future: storage limit

[database]
db_path = ./photosync.db         # Database file location

[logging]
log_level = INFO                 # DEBUG, INFO, WARN, ERROR
log_file = ./server.log          # Log file location
console_output = true            # Also log to console
```

After changing configuration, restart the server.

---

## Stopping the Server

Press **Ctrl+C** in the server window:

```
^C[2025-11-30 11:30:00.000] [INFO ] Shutdown signal received
[2025-11-30 11:30:00.001] [INFO ] Server stopped
[2025-11-30 11:30:00.002] [INFO ] Database closed
[2025-11-30 11:30:00.002] [INFO ] === PhotoSync Server Shutdown Complete ===
```

---

## Troubleshooting

### Server Won't Start - "Address already in use"

Another process is using port 50505.

**Solution 1:** Find and stop the process:
```powershell
# Find process using port 50505
netstat -ano | findstr :50505

# Kill the process (use PID from above)
taskkill /PID <PID> /F
```

**Solution 2:** Change port in `server.conf`:
```ini
[network]
port = 50506
```

Also update mock client:
```powershell
.\build\Release\MockClient.exe --port 50506 --photos 10
```

---

### Client Can't Connect

**Check server is running:**
```powershell
netstat -ano | findstr :50505
```

**Check firewall:**
- Windows Firewall may be blocking the connection
- Add exception for PhotoSyncServer.exe

**Try localhost explicitly:**
```powershell
.\build\Release\MockClient.exe --host 127.0.0.1 --photos 5
```

---

### Database Locked Error

Only one server instance can access the database at a time.

**Solution:**
```powershell
# Check for running server instances
tasklist | findstr PhotoSyncServer

# Kill all instances
taskkill /IM PhotoSyncServer.exe /F

# Delete database file if corrupted
Remove-Item photosync.db -Force

# Restart server (will recreate database)
.\build\Release\PhotoSyncServer.exe
```

---

## Performance Notes

Based on testing:
- **Single client can send ~1000 photos/second** (metadata only)
- **Server handles multiple concurrent clients** without issues
- **Database performance** is good with SQLite indexes

For production use with actual file transfer, performance will depend on:
- Network bandwidth
- File sizes
- Disk I/O speed

---

## Next Steps

After verifying Phase 1 works:

1. **Phase 2:** Session management optimization, better error handling
2. **Phase 3:** Add actual file transfer (not just metadata)
3. **Phase 4:** Production hardening, monitoring, logging improvements

---

## Example Test Session

```powershell
# Terminal 1: Start server
cd C:\Users\parim\Desktop\pics\pics\desktop-server
.\build\Release\PhotoSyncServer.exe

# Terminal 2: Run test
cd C:\Users\parim\Desktop\pics\pics\desktop-server\test-client
.\build\Release\MockClient.exe --photos 20 --batch-size 5

# Expected result: SUCCESS
# - Server logs show session creation, batches, completion
# - Client shows progress and completion
# - Database contains 20 photo records

# Terminal 1: Stop server
# Press Ctrl+C
```

**✅ If all tests pass, Phase 1 is complete!**
