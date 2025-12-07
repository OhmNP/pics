# Product Requirements

## Vision
To provide a fast, private, and reliable solution for backing up mobile photos to a desktop computer without relying on cloud services.

## User Personas
*   **The Archivist**: Wants to keep every photo forever and hates monthly cloud storage subscriptions.
*   **The Content Creator**: Needs to quickly move large video/photo files from phone to PC for editing.
*   **The Privacy Advocate**: Does not want personal photos stored on third-party servers.

## Functional Requirements

### 1. Photo Synchronization
*   **Auto-Discovery**: Client must automatically find the server on the local LAN without manual IP entry.
*   **Incremental Sync**: Only upload new photos. Existing photos on the server (matched by hash) must be skipped.
*   **Metadata Preservation**: Filenames, timestamps, and integrity (hashing) must be preserved.
*   **Batch Processing**: Photos should be synced in efficient batches to maximize throughput.
*   **Resume Capability**: If a connection drops, the sync should be able to resume without starting over.

### 2. Desktop Server Management
*   **Configuration**: User can configure storage paths, ports, and max storage limits.
*   **Monitoring**: Real-time view of active connections, transfer speeds, and storage usage.
*   **Client Management**: View list of all devices that have backed up photos.

### 2.1. Admin Authentication & Access Control
*   **Login**: Admin must authenticate with username/password to access dashboard.
*   **Session Management**: Sessions expire after configurable timeout (default 60 seconds).
*   **Security**: Passwords hashed with bcrypt, sessions stored server-side.
*   **Failed Login Protection**: Failed login attempts logged for security monitoring.

### 2.2. Client Pairing & Registration
*   **QR Code Pairing**: Admin generates one-time pairing token displayed as QR code.
*   **Token Security**: Tokens are cryptographically secure, expire after 15 minutes, single-use.
*   **Easy Onboarding**: User scans QR code on Android app for instant pairing.
*   **Token Management**: Admin can view active tokens and revoke if needed.

### 2.3. Media Search & Filtering
*   **Search**: Full-text search for media files by filename.
*   **Filters**: Filter by client, date range, file type (image/video).
*   **Thumbnails**: Visual grid view with thumbnail previews.
*   **Pagination**: Efficient pagination for large media libraries (default 50 items/page).
*   **Performance**: Search results return in < 500ms for libraries with 10,000+ files.

### 2.4. System Monitoring & Health
*   **Storage Metrics**: Real-time storage usage, utilization percentage, per-client breakdown.
*   **Health Dashboard**: Server uptime, background job queue status.
*   **Error Feed**: Real-time display of system errors and warnings.
*   **Auto-Refresh**: Metrics update automatically every 30 seconds.
*   **Historical Trends**: Storage usage trends over last 24 hours.

### 2.5. Audit Logging & Compliance
*   **Action Tracking**: All admin actions logged with timestamp, user, action type.
*   **Immutability**: Audit logs cannot be edited or deleted from dashboard.
*   **Retention**: Configurable retention policy (default 90 days for audit logs).
*   **Export**: Audit logs can be exported as JSON for compliance reporting.
*   **Filtering**: Filter audit logs by date, user, action type.

### 3. Android Client
*   **Status Dashboard**: Immediate visual feedback on connection status and sync progress.
*   **Background Service**: Sync should be robust enough to continue (to the extent Android allows) or resume when the app is foregrounded.
*   **Gallery View**: Visual confirmation of which photos are synced (checkmark badge).

## Non-Functional Requirements

### Performance
*   **Throughput**: Support > 1000 photos/second for metadata checking.
*   **Latency**: Connection handshake < 100ms.
*   **Resources**: Server should use < 100MB RAM when idle.

### Reliability
*   **Data Integrity**: Every file transfer must be verified with a checksum/hash.
*   **Concurrency**: Server must handle multiple (10+) clients syncing simultaneously.

### Security
*   **Network**: Communication occurs strictly over Local Area Network (LAN).
*   **Storage**: Photos are stored locally on the user's hard drive.
*   **Authentication**: Admin dashboard protected with bcrypt password hashing.
*   **Sessions**: Server-side session management with configurable timeout.
*   **Audit Trail**: All admin actions logged for accountability.

## Future Scope (Roadmap)
*   Two-way sync (view PC photos on phone).
*   Encrypted transfer (TLS).
*   Automatic background sync on generic Wi-Fi connection trigger.
