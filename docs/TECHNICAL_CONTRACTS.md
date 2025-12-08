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

### `metadata`
Registry of all stored files (renamed from `photos` to support multiple media types).
```sql
CREATE TABLE metadata (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    client_id INTEGER,
    filename TEXT,
    size INTEGER,               -- Bytes
    hash TEXT UNIQUE,           -- SHA-256 for deduplication
    file_path TEXT,             -- Relative path in storage
    mime_type TEXT,             -- MIME type (e.g., image/jpeg, video/mp4)
    received_at TIMESTAMP,
    FOREIGN KEY(client_id) REFERENCES clients(id)
);
CREATE INDEX idx_metadata_hash ON metadata(hash);
CREATE INDEX idx_metadata_client_timestamp ON metadata(client_id, received_at);
CREATE INDEX idx_metadata_mime_type ON metadata(mime_type);
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

### `admin_users`
Admin users for dashboard authentication.
```sql
CREATE TABLE admin_users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT UNIQUE NOT NULL,
    password_hash TEXT NOT NULL,        -- bcrypt hash
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login TIMESTAMP,
    is_active BOOLEAN DEFAULT 1
);
```

### `sessions`
Server-side session storage for admin authentication.
```sql
CREATE TABLE sessions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_token TEXT UNIQUE NOT NULL, -- 32-byte random, hex-encoded
    user_id INTEGER NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    ip_address TEXT,
    FOREIGN KEY(user_id) REFERENCES admin_users(id)
);
CREATE INDEX idx_sessions_token ON sessions(session_token);
CREATE INDEX idx_sessions_expires ON sessions(expires_at);
```

### `pairing_tokens`
One-time tokens for pairing new Android clients.
```sql
CREATE TABLE pairing_tokens (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    token TEXT UNIQUE NOT NULL,        -- 32-byte random, base64url-encoded
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    used_by_client_id INTEGER,
    is_active BOOLEAN DEFAULT 1,
    FOREIGN KEY(used_by_client_id) REFERENCES clients(id)
);
CREATE INDEX idx_pairing_tokens_token ON pairing_tokens(token);
CREATE INDEX idx_pairing_tokens_expires ON pairing_tokens(expires_at);
```

### `logs`
System logs for errors, warnings, and informational messages.
```sql
CREATE TABLE logs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    severity TEXT NOT NULL,             -- 'error', 'warning', 'info'
    message TEXT NOT NULL,
    details TEXT,                       -- JSON with additional context
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    client_id INTEGER,
    component TEXT,                     -- 'server', 'sync', 'api', 'auth'
    FOREIGN KEY(client_id) REFERENCES clients(id)
);
CREATE INDEX idx_logs_severity ON logs(severity);
CREATE INDEX idx_logs_timestamp ON logs(timestamp DESC);
CREATE INDEX idx_logs_client ON logs(client_id);
```

### `audit_logs`
Audit trail for all admin actions and client interactions.
```sql
CREATE TABLE audit_logs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    user_id INTEGER,                    -- Admin user who performed action
    action TEXT NOT NULL,               -- Action type
    target_type TEXT,                   -- 'client', 'media', 'settings', 'token'
    target_id INTEGER,                  -- ID of affected resource
    details TEXT,                       -- JSON with additional context
    ip_address TEXT,
    status TEXT,                        -- 'success', 'failed'
    FOREIGN KEY(user_id) REFERENCES admin_users(id)
);
CREATE INDEX idx_audit_logs_timestamp ON audit_logs(timestamp DESC);
CREATE INDEX idx_audit_logs_user ON audit_logs(user_id);
CREATE INDEX idx_audit_logs_action ON audit_logs(action);
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

#### `BATCH_CHECK <count>`
*   **Direction**: Client -> Server
*   **Purpose**: Efficiently check which files (by hash) already exist on the server.
*   **Payload**: `<count>` lines, each containing a SHA-256 hash.
*   **Response**: 
    *   `BATCH_RESULT <found_count>` followed by `<found_count>` lines of hashes that exist on the server.
*   **Example**:
    ```
    C: BATCH_CHECK 3
    C: abc123...
    C: def456...
    C: ghi789...
    S: BATCH_RESULT 2
    S: abc123...
    S: ghi789...
    ```

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
Current server configuration (loaded from `server.conf`).
```json
{
  "network": {
    "port": 50505,
    "udpBroadcastInterval": 5000
  },
  "storage": {
    "photosDir": "./storage/photos",
    "thumbnailsDir": "./storage/thumbnails",
    "maxStorageGB": 100,
    "thumbnailSize": 256
  },
  "auth": {
    "sessionTimeoutSeconds": 60,
    "bcryptCost": 12
  },
  "retention": {
    "logRetentionDays": 30,
    "auditLogRetentionDays": 90
  },
  "ui": {
    "defaultPageSize": 50,
    "autoRefreshInterval": 30
  }
}
```

#### `PUT /api/config`
Update server configuration (applies dynamically without restart).
**Request Body**: Same structure as GET response.
**Response**: Updated configuration.

---

### Authentication Endpoints

#### `POST /api/auth/login`
Authenticate admin user and create session.
**Request**:
```json
{
  "username": "admin",
  "password": "password123"
}
```
**Response**:
```json
{
  "sessionToken": "abc123...",
  "expiresAt": "2025-12-06T10:01:00Z",
  "user": {
    "id": 1,
    "username": "admin"
  }
}
```

#### `POST /api/auth/logout`
Invalidate current session.
**Headers**: `Authorization: Bearer {sessionToken}`
**Response**: `{"success": true}`

#### `GET /api/auth/validate`
Validate current session token.
**Headers**: `Authorization: Bearer {sessionToken}`
**Response**:
```json
{
  "valid": true,
  "expiresAt": "2025-12-06T10:01:00Z"
}
```

---

### Client Management Endpoints

#### `GET /api/clients/{client_id}`
Get detailed information for a specific client.
**Response**:
```json
{
  "client": {
    "id": 1,
    "deviceId": "android_123",
    "lastSeen": "2025-12-06T10:00:00Z",
    "photoCount": 500,
    "storageUsed": 2048000,
    "isOnline": true,
    "firstSeen": "2025-01-01T00:00:00Z"
  },
  "recentUploads": {
    "items": [...],
    "pagination": {...}
  },
  "storageStats": {...}
}
```

#### `GET /api/clients/{client_id}/uploads`
Get paginated upload history for a client.
**Query Parameters**: `page`, `limit`
**Response**: Paginated list of uploads.

---

### Storage Endpoints

#### `GET /api/storage/overview`
Get aggregated storage statistics.
**Response**:
```json
{
  "totalStorageUsed": 4500000000,
  "totalFiles": 1502,
  "storageLimit": 107374182400,
  "utilizationPercent": 4.19,
  "byClient": [...],
  "byFileType": {...}
}
```

---

### Media Endpoints

#### `GET /api/media`
Get paginated list of media files.
**Query Parameters**: `client_id`, `limit`, `offset`, `start_date`, `end_date`
**Response**: Paginated media list with thumbnail URLs.

#### `GET /api/media/search`
Search media with filters.
**Query Parameters**: `client_id`, `start_date`, `end_date`, `mime_type`, `filename`, `limit`, `offset`
**Response**: Filtered and paginated media list.

#### `GET /api/thumbnails/{id}`
Get thumbnail for a media file.
**Response**: Image binary (JPEG) with cache-control headers.

#### `GET /api/media/{id}/download`
Download full-size media file.
**Response**: Media binary with appropriate MIME type.

---

### Pairing Token Endpoints

#### `POST /api/tokens/generate`
Generate new pairing token.
**Response**:
```json
{
  "token": "Xy9kL3mN8pQ2rS4tU6vW7xY8zA1bC3dE5fG7hI9jK0lM2nO4pQ6rS8tU0vW2xY4zA",
  "qrCodeData": "photosync://pair?token=...&server=192.168.1.100:50505",
  "expiresAt": "2025-12-06T10:15:00Z",
  "expiresInSeconds": 900
}
```

#### `GET /api/tokens`
List all pairing tokens.
**Response**: Array of tokens with status.

#### `POST /api/tokens/validate`
Validate pairing token (used by Android client).
**Request**: `{"token": "..."}`
**Response**: `{"valid": true, "clientCredentials": {...}}`

#### `DELETE /api/tokens/{token_id}`
Revoke active pairing token.
**Response**: `{"success": true}`

---

### Thumbnail Management Endpoints

#### `POST /api/thumbnails/regenerate`
Trigger thumbnail regeneration job.
**Request**:
```json
{
  "clientId": 1,
  "mediaIds": [123, 456],
  "force": false
}
```
**Response**:
```json
{
  "jobId": "job_abc123",
  "status": "queued",
  "totalItems": 2
}
```

#### `GET /api/thumbnails/status/{job_id}`
Get thumbnail regeneration job status.
**Response**: Job progress and status.

---

### Logging Endpoints

#### `GET /api/logs`
Get system logs with filtering.
**Query Parameters**: `level` (error,warning,info), `client_id`, `limit`, `offset`
**Response**: Paginated log entries.

---

### Health Endpoints

#### `GET /api/health`
Get server health metrics.
**Response**:
```json
{
  "uptime": 86400,
  "storage": {...},
  "queue": {...},
  "system": {
    "cpu": null,
    "ram": null,
    "note": "Placeholder for future"
  }
}
```

---

### Audit Endpoints

#### `GET /api/audit`
Get audit logs with filtering.
**Query Parameters**: `start_date`, `end_date`, `user_id`, `action`, `limit`, `offset`
**Response**: Paginated audit log entries.

#### `GET /api/audit/export`
Export audit logs as JSON.
**Query Parameters**: `start_date`, `end_date`
**Response**: JSON file download.
