# Server Efficient Reconciliation Protocol

**Status**: Draft
**Date**: 2025-12-08
**Author**: Antigravity

## 1. Summary
Introduce a new `BATCH_CHECK` command to the TCP Sync Protocol. This allows clients to efficiently verify the sync status of a large number of files by sending only their hashes in a batch, rather than initiating full transfer sessions for each file.

## 2. Motivation
The Android client needs to "reconcile" its local state with the server (e.g., after a fresh install or data clear). Using the existing `PHOTO_METADATA` command for thousands of files is inefficient because:
1.  It requires sending filenames and sizes (extra bandwidth).
2.  It creates a 1:1 request/response chatter.
3.  It lacks semantic clarity (checking vs transferring).

A `BATCH_CHECK` command allows sending just hashes in bulk, significantly reducing overhead and latency.

## 3. Technical Design

### Protocol Update (TCP :50505)

#### `BATCH_CHECK <count>`
*   **Description**: Initiates a batch check for presence of files by hash.
*   **Direction**: Client -> Server
*   **Payload**: `<count>` lines, each containing a SHA-256 hash.
*   **Response**: `BATCH_RESULT <count_found>` followed by the list of *found* hashes (or a bitmask, but existing protocol is line-based text, so returning found hashes is robust).

**Example Flow**:
```text
C: BATCH_CHECK 3
C: <hash_A>
C: <hash_B>
C: <hash_C>
S: BATCH_RESULT 2
S: <hash_A>
S: <hash_C>
```
*In this example, Hash B was not found on the server.*

### Server Implementation
*   **TcpListener**: Parse new state `WAITING_FOR_BATCH_CHECK`.
*   **Database**: Efficient `SELECT hash FROM metadata WHERE hash IN (...)` query.

## 4. Requirements
*   [ ] Server accepts `BATCH_CHECK` command.
*   [ ] Server correctly parses N lines of hashes.
*   [ ] Server returns `BATCH_RESULT` with only the hashes that exist in `metadata` table.
*   [ ] Performance: Check 1000 items in < 500ms.
