# Technical Contracts

This document defines the interface contracts between different system components.

## 1. Database Schema (SQLite)

The server utilizes a SQLite database `photosync.db` for metadata management.

### `clients`
Tracks devices that have connected to the server.
```sql
CREATE TABLE clients (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    device_id TEXT UNIQUE,      -- Android Hardware ID or UUID
    last_seen TIMESTAMP,        -- ISO8601 UTC
    total_photos INTEGER DEFAULT 0
);
```

### `photos`
Registry of all stored files.
```sql
CREATE TABLE photos (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    client_id INTEGER,
    filename TEXT,
    size INTEGER,               -- Bytes
    hash TEXT UNIQUE,           -- SHA-256 for deduplication
    file_path TEXT,             -- Relative path in storage
    received_at TIMESTAMP,
    FOREIGN KEY(client_id) REFERENCES clients(id)
);
CREATE INDEX idx_photos_hash ON photos(hash);
```

### `sync_sessions`
History of sync operations.
```sql
CREATE TABLE sync_sessions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    client_id INTEGER,
    started_at TIMESTAMP,
    ended_at TIMESTAMP,
    photos_received INTEGER DEFAULT 0,
    status TEXT,                -- 'active', 'completed', 'failed'
    FOREIGN KEY(client_id) REFERENCES clients(id)
);
```

---

## 2. Sync Protocol (TCP :50505)

Custom line-based text protocol. Commands are newline-terminated. Binary data is transferred in fixed-size chunks following a header.

### Commands

#### `SESSION_START <device_id>`
*   **Direction**: Client -> Server
*   **Response**: `SESSION_ACK <session_id>`

#### `BATCH_START <batch_size>`
*   **Direction**: Client -> Server
*   **Response**: `ACK`

#### `PHOTO_METADATA <filename> <size_bytes> <hash_sha256>`
*   **Direction**: Client -> Server
*   **Response**: 
    *   `SEND_REQ` (Server needs file)
    *   `SKIP` (File already exists)

#### `DATA_TRANSFER <size_bytes>`
*   **Direction**: Client -> Server
*   **Payload**: Immediately followed by `<size_bytes>` of binary data.
*   **Response**: `ACK` (after successful receipt and verification)

#### `BATCH_END`
*   **Direction**: Client -> Server
*   **Response**: `ACK`

#### `SESSION_END`
*   **Direction**: Client -> Server
*   **Response**: `ACK`

---

## 3. Server REST API (HTTP)

Used by the Dashboard UI. served on port 50505.

### Global
*   **Headers**: `Content-Type: application/json`
*   **CORS**: Enabled for all origins (`*`) for development convenience.

### Endpoints

#### `GET /api/stats`
Returns aggregate system statistics.
```json
{
  "totalPhotos": 1502,
  "connectedClients": 1,
  "totalClients": 3,
  "totalSessions": 45,
  "storageUsed": 4500000000, // Bytes
  "storageLimit": 107374182400, // Bytes (100GB)
  "serverStatus": "running"
}
```

#### `GET /api/connections`
Returns list of currently active TCP sessions.
```json
{
  "active_connections": [
    {
      "session_id": 12,
      "device_id": "android_123",
      "ip_address": "192.168.1.50",
      "connected_at": "2023-10-25T10:00:00Z",
      "status": "syncing",
      "photos_uploaded": 55,
      "bytes_transferred": 104857600
    }
  ]
}
```

#### `GET /api/clients`
List of all known devices.
```json
{
  "clients": [
    {
      "id": 1,
      "deviceId": "android_123",
      "lastSeen": "2023-10-25T10:00:00Z",
      "photoCount": 500,
      "storageUsed": 2048000,
      "isOnline": true
    }
  ]
}
```

#### `GET /api/config`
Current server configuration.
```json
{
  "network": { "port": 50505 ... },
  "storage": { "photosDir": "./storage/photos" ... }
}
```
