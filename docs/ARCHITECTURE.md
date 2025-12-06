# System Architecture

## Overview
PhotoSync is a distributed system designed to synchronize photos from Android devices to a Windows desktop server over a local network. It prioritizes performance, reliability, and privacy by keeping all data on the local network.

## High-Level Architecture

```mermaid
graph TD
    subgraph "Android Client"
        UI[Jetpack Compose UI]
        SyncService[Sync Service]
        LocalDB[Local MediaStore]
    end

    subgraph "Desktop Server (Windows)"
        TcpListener[TCP Listener :50505]
        ApiServer[API Server (Crow)]
        SQLite[(SQLite Database)]
        Disk[Disk Storage]
    end

    subgraph "Dashboard (Web/Electron)"
        DashboardUI[React Dashboard]
    end

    UI --> SyncService
    SyncService -- Media Data --> TcpListener
    SyncService -- Discovery (UDP) --> TcpListener
    
    TcpListener --> SQLite
    TcpListener --> Disk
    
    DashboardUI -- HTTP REST --> ApiServer
    ApiServer --> SQLite
    ApiServer --> TcpListener : Query Status
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
*   **Role**: Central hub for data reception and storage.
*   **Tech Stack**: C++17, CMake, Boost.Asio (Networking), SQLite3 (Metadata), Crow (REST API).
*   **Key Responsibilities**:
    *   **TcpListener**: Handles high-performance concurrent client connections.
    *   **ProtocolParser**: Decodes the custom sync protocol.
    *   **DatabaseManager**: Manages ACID transactions for metadata.
    *   **FileManager**: Handles efficient file writing to disk.
    *   **ApiServer**: Exposes system status and data via REST endpoints.
    *   **UdpBroadcaster**: Broadcasts presence for auto-discovery.

### 3. UI Dashboard
*   **Role**: Monitoring and management interface.
*   **Tech Stack**: React, Vite, Material UI (MUI), Recharts.
*   **Deployment**: 
    *   Served statically by the C++ `ApiServer` at `http://localhost:50505`.
    *   Can also run as a standalone Electron app during development.
*   **Key Responsibilities**:
    *   Visualizes storage usage and sync stats.
    *   Displays connected clients and session history.
    *   Allows configuration of server settings.

## Data Flow

### Synchronization Process
1.  **Discovery**: Client listens for UDP broadcast to find Server IP.
2.  **Handshake**: Client connects to TCP port 50505 and sends `SESSION_START`.
3.  **Metadata**: Client sends `PHOTO_METADATA` for a batch of photos.
4.  **Deduplication**: Server checks DB. If hash exists, it tells client to skip.
5.  **Transfer**: For new photos, Client sends `DATA_TRANSFER` with binary content.
6.  **Finalization**: `BATCH_END` and `SESSION_END` confirm transactions.

### Monitoring
1.  Dashboard polls `/api/stats` and `/api/connections`.
2.  `ApiServer` queries `ConnectionManager` for real-time memory state.
3.  `ApiServer` queries `DatabaseManager` for historical stats.
