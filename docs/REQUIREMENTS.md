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

## Future Scope (Roadmap)
*   Two-way sync (view PC photos on phone).
*   Encrypted transfer (TLS).
*   Automatic background sync on generic Wi-Fi connection trigger.
