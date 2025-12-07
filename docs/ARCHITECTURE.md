# System Architecture

## Overview
PhotoSync is a distributed system designed to synchronize photos from Android devices to a Windows desktop server over a local network. It prioritizes performance, reliability, and privacy by keeping all data on the local network.

## High-Level Architecture

```mermaid
graph TD
    subgraph "Android Client"
        UI[Jetpack Compose UI]
        SyncService[Sync Service]
        PairingUI[Pairing/QR Scanner]
        LocalDB[Local MediaStore]
    end

    subgraph "Desktop Server (Windows)"
        TcpListener[TCP Listener :50505]
        ApiServer[API Server (Crow)]
        AuthSystem[Authentication System]
        BackgroundJobs[Background Job System]
        ThumbnailService[Thumbnail Service]
        PairingService[Pairing Service]
        LoggingSystem[Logging & Audit System]
        SQLite[(SQLite Database)]
        Disk[Disk Storage]
        ConfigFile[server.conf]
    end

    subgraph "Dashboard (Web/Electron)"
        DashboardUI[React Dashboard]
        LoginUI[Login Screen]
    end

    UI --> SyncService
    PairingUI --> PairingService
    SyncService -- Media Data --> TcpListener
    SyncService -- Discovery (UDP) --> TcpListener
    
    TcpListener --> SQLite
    TcpListener --> Disk
    TcpListener --> LoggingSystem
    
    LoginUI -- Auth --> AuthSystem
    DashboardUI -- HTTP REST --> ApiServer
    ApiServer --> AuthSystem : Validate Session
    ApiServer --> SQLite
    ApiServer --> TcpListener : Query Status
    ApiServer --> BackgroundJobs : Trigger Jobs
    ApiServer --> ThumbnailService : Generate/Serve
    ApiServer --> PairingService : Generate Tokens
    ApiServer --> LoggingSystem : Query Logs
    ApiServer --> ConfigFile : Read/Write Config
    
    BackgroundJobs --> ThumbnailService
    BackgroundJobs --> SQLite : Cleanup
    BackgroundJobs --> LoggingSystem
    
    ThumbnailService --> Disk : Read/Write Thumbnails
```

## Component Details

### 1. Android Client
*   **Role**: Source of photo data. Initiates synchronization.
*   **Tech Stack**: Kotlin, Jetpack Compose, Coroutines, MVVM Architecture.
*   **Key Responsibilities**:
    *   Scans local storage for new photos.
    *   Calculates hashes for deduplication.
    *   Connects to server via TCP.
    *   Uploads metadata and file content.
    *   Displays progress to user.

### 2. Desktop Server
*   **Role**: Central hub for data reception, storage, and management.
*   **Tech Stack**: C++17, CMake, Boost.Asio (Networking), SQLite3 (Metadata), Crow (REST API), bcrypt (Password Hashing).
*   **Key Responsibilities**:
    *   **TcpListener**: Handles high-performance concurrent client connections.
    *   **ProtocolParser**: Decodes the custom sync protocol.
    *   **DatabaseManager**: Manages ACID transactions for metadata.
    *   **FileManager**: Handles efficient file writing to disk.
    *   **ApiServer**: Exposes system status and data via REST endpoints.
    *   **UdpBroadcaster**: Broadcasts presence for auto-discovery.
    *   **AuthenticationSystem**: Manages admin login, sessions, and access control.
    *   **BackgroundJobSystem**: Executes asynchronous tasks (thumbnails, cleanup).
    *   **ThumbnailService**: Generates and serves thumbnails for images/videos.
    *   **PairingService**: Generates one-time tokens for client registration.
    *   **LoggingSystem**: Centralized logging and audit trail management.
    *   **ConfigManager**: Loads and hot-reloads configuration from `server.conf`.

### 3. UI Dashboard
*   **Role**: Monitoring, management, and administration interface.
*   **Tech Stack**: React, Vite, Material UI (MUI), Recharts, qrcode.react.
*   **Deployment**: 
    *   Served statically by the C++ `ApiServer` at `http://localhost:50505`.
    *   Can also run as a standalone Electron app during development.
*   **Key Responsibilities**:
    *   **Authentication**: Admin login with session management.
    *   **Client Management**: View all clients, detailed stats, and online status.
    *   **Media Management**: Browse, search, and filter media with thumbnails.
    *   **Storage Monitoring**: Visualize storage usage and trends.
    *   **Pairing**: Generate QR codes for new client registration.
    *   **Configuration**: Update server settings dynamically.
    *   **Health Monitoring**: View server health metrics and queue status.
    *   **Error Monitoring**: Display system errors and warnings.
    *   **Audit Logging**: Track all admin actions for compliance.
    *   **Thumbnail Management**: Manually trigger thumbnail regeneration.

## Data Flow

### Synchronization Process
1.  **Discovery**: Client listens for UDP broadcast to find Server IP.
2.  **Handshake**: Client connects to TCP port 50505 and sends `SESSION_START`.
3.  **Metadata**: Client sends `PHOTO_METADATA` for a batch of photos.
4.  **Deduplication**: Server checks DB. If hash exists, it tells client to skip.
5.  **Transfer**: For new photos, Client sends `DATA_TRANSFER` with binary content.
6.  **Finalization**: `BATCH_END` and `SESSION_END` confirm transactions.

### Monitoring
1.  Dashboard polls `/api/stats`, `/api/connections`, `/api/health` every 30 seconds.
2.  `ApiServer` queries `ConnectionManager` for real-time memory state.
3.  `ApiServer` queries `DatabaseManager` for historical stats.
4.  `LoggingSystem` aggregates errors/warnings for display in error feed.

### Authentication Flow
1.  Admin enters credentials on dashboard login screen.
2.  Dashboard sends `POST /api/auth/login` with username/password.
3.  `AuthenticationSystem` validates credentials (bcrypt hash comparison).
4.  Server creates session token, stores in `sessions` table.
5.  Dashboard stores token and includes in `Authorization` header for all requests.
6.  Session expires after configured timeout (default 60 seconds).
7.  Expired sessions cleaned up by background job.

### Client Pairing Flow
1.  Admin clicks "Generate Pairing Token" in dashboard.
2.  `PairingService` generates cryptographically secure token.
3.  Dashboard displays QR code with deep link: `photosync://pair?token=...&server=IP:PORT`.
4.  User scans QR code on Android app during first-time setup.
5.  Android app sends `POST /api/tokens/validate` with token.
6.  Server validates token, marks as used, returns client credentials.
7.  Android app stores credentials for future sync sessions.

### Background Jobs
1.  **Thumbnail Generation**: Processes media files to create thumbnails (256x256 JPEG).
2.  **Session Cleanup**: Removes expired sessions from database (runs every 5 minutes).
3.  **Log Cleanup**: Removes old logs based on retention policy (runs daily).
4.  **Audit Cleanup**: Removes old audit logs based on retention policy (runs daily).
5.  **Token Cleanup**: Removes expired pairing tokens (runs hourly).

Jobs run sequentially in a queue to avoid resource contention.

---

## New Components Detail

### Authentication System
*   **Purpose**: Secure dashboard access with admin user management.
*   **Components**:
    *   Password hashing using bcrypt (cost factor 12)
    *   Server-side session storage in SQLite
    *   Session token generation (32-byte random, hex-encoded)
    *   Session validation middleware for protected endpoints
*   **Database Tables**: `admin_users`, `sessions`

### Background Job System
*   **Purpose**: Execute long-running tasks asynchronously without blocking API requests.
*   **Implementation**: In-memory job queue with worker thread.
*   **Job Types**:
    *   Thumbnail generation (triggered manually or on upload)
    *   Session cleanup (scheduled every 5 minutes)
    *   Log cleanup (scheduled daily)
    *   Audit log cleanup (scheduled daily)
    *   Pairing token cleanup (scheduled hourly)
*   **Execution**: Sequential (one job at a time) to avoid resource contention.

### Thumbnail Service
*   **Purpose**: Generate and serve thumbnails for media preview.
*   **Storage**: `./storage/thumbnails/{client_id}/{media_id}.jpg`
*   **Format**: JPEG, 256x256 pixels, quality 85
*   **Video Support**: Extract frame at 1 second for video thumbnails
*   **Caching**: Serves with cache-control headers for client-side caching

### Pairing Service
*   **Purpose**: Secure client registration without exposing credentials.
*   **Token Format**: 32-byte random, base64url-encoded
*   **Expiration**: 15 minutes (configurable)
*   **One-Time Use**: Token invalidated after successful pairing
*   **QR Code**: Deep link format for easy mobile scanning
*   **Database Table**: `pairing_tokens`

### Logging & Audit System
*   **Purpose**: Centralized logging and compliance tracking.
*   **System Logs**: Errors, warnings, info messages from all components
*   **Audit Logs**: Immutable trail of all admin actions
*   **Retention**: Configurable (default: 30 days for logs, 90 days for audit)
*   **Database Tables**: `logs`, `audit_logs`
*   **Export**: Audit logs can be exported as JSON for compliance
