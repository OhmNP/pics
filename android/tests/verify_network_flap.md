# Manual Verification: Network Flap Robustness

This document defines **manual, reproducible verification steps** to ensure the PhotoSync Android client remains reliable during network fluctuations (Wi-Fi flapping) without creating duplicate sync loops, silent failures, or battery-draining storms.

---

## Required Setup (Before All Tests)

- Logging level set to `INFO` or `DEBUG`
- Android UI visibly shows:
  - Pending count
  - Failed count
  - Last successful backup timestamp
  - Current sync state (Idle / Syncing / Paused / Error)
- Select **one large file** (video ~200–500MB) to guarantee long upload duration
- Server dashboard or logs must show:
  - uploadId
  - traceId
  - receivedBytes (for resume verification)

---

## Global Pass / Fail Invariants (Apply to ALL Tests)

These must hold true across all scenarios:

- **Invariant A:** At most **ONE active sync executor** at any time  
  Evidence: logs contain a single `SyncRun START runId=...` without overlap

- **Invariant B:** Pending count eventually decreases after recovery

- **Invariant C:** No item transitions `SYNCED → PENDING` without explicit user action

- **Invariant D:** Resumed uploads must **not restart from offset 0**  
  Evidence: server returns `receivedBytes > 0` on resume

Violation of any invariant = **FAIL**

---

## Test Case 1: Automatic Re-Sync on Network Availability

**Goal:** Pending items resume automatically when Wi-Fi returns, without manual user action.

### Steps
1. Disable Wi-Fi on the device
2. Add or capture **5 new photos**
3. Confirm UI shows Pending increased by 5
4. Enable Wi-Fi
5. Keep screen on and wait up to **30 seconds**

### Pass Criteria
- Logs contain:
  - `[Orchestrator] RequestSync reason=NETWORK_AVAILABLE`
  - exactly **one** `SyncRun START runId=...`
- Within 30 seconds:
  - Pending count begins decreasing
- Within 5 minutes:
  - Pending count reaches 0 (or stable if exclusions apply)
- Failed count does **not** increase

### Fail Criteria
- No sync run starts within 30 seconds
- Pending remains unchanged for 5 minutes
- Multiple sync runs overlap

---

## Test Case 2: Graceful Pause on Network Loss (Mid-Upload)

**Goal:** Active uploads pause cleanly and resume correctly after reconnection.

### Steps
1. Start syncing the large file
2. Confirm item state is `UPLOADING`
3. Disable Wi-Fi mid-upload
4. Wait 10 seconds
5. Enable Wi-Fi
6. Wait up to **60 seconds**

### Pass Criteria
- On Wi-Fi OFF:
  - Log: `Network lost -> pausing uploads`
  - Item state becomes `PAUSED (PAUSED_NETWORK)`
  - No crash or ANR
- On Wi-Fi ON:
  - Orchestrator requests sync after debounce
  - Client sends `UPLOAD_INIT`
  - Server returns `UPLOAD_ACK` with `receivedBytes > 0`
  - Upload resumes and completes
- Failed count remains unchanged

### Fail Criteria
- Item becomes `FAILED` due to network loss
- Upload restarts from offset 0
- Infinite retry loops or log spam

---

## Test Case 3: Single-Flight Guard & Rate Limiting

**Goal:** Rapid Wi-Fi flapping does NOT trigger multiple concurrent sync loops.

### Steps
1. Toggle Wi-Fi ON/OFF **5 times within 10 seconds**
2. Keep screen on
3. Wait 60 seconds

### Pass Criteria
- Logs show:
  - `RequestSync rejected - rate limited`
  - `Single-flight: already running`
- Total `SyncRun START` entries ≤ **2**
- No duplicate upload sessions on server for the same file hash

### Fail Criteria
- 3 or more sync runs start within 60 seconds
- Concurrent runs observed
- Notification spam or noticeable device heating

---

## Test Case 4: Transient Socket Break (Server Restart)

**Goal:** Broken connections trigger reconnect + resume, not permanent failure.

### Steps
1. Start uploading the large file
2. Restart the server process mid-upload
3. Wait up to 60 seconds

### Pass Criteria
- Client logs show:
  - `Transient error detected (IOException...)`
  - `Reconnect + UPLOAD_INIT`
- Server logs show same upload session resuming
- `receivedBytes` preserved
- Upload completes without manual intervention
- Item does NOT end in `FAILED`

### Fail Criteria
- Item permanently fails
- Repeated restart from offset 0
- Endless reconnect loop without progress

---

## Test Case 5: WorkManager + Foreground Service Overlap Prevention

**Goal:** WorkManager execution does not start a second sync while service is active.

### Steps
1. Start a long upload (foreground service running)
2. Force-run Worker (debug menu or `adb jobscheduler run`)
3. Observe logs for 60 seconds

### Pass Criteria
- Worker requests sync
- Orchestrator rejects request due to single-flight
- No second executor starts

### Fail Criteria
- Worker and service upload concurrently
- Duplicate sync loops detected

---

## Test Results Log

Fill this table after execution:

| Test Case | Pass / Fail | Start Time | End Time | Notes (runId, uploadId, offsets) |
|----------|-------------|------------|----------|----------------------------------|
| TC-1     |             |            |          |                                  |
| TC-2     |             |            |          |                                  |
| TC-3     |             |            |          |                                  |
| TC-4     |             |            |          |                                  |
| TC-5     |             |            |          |                                  |

---

## Final Acceptance Rule

All test cases must PASS and all Global Invariants must hold.

If any test fails:
- The sync orchestration logic must be fixed
- This document must be re-executed in full
