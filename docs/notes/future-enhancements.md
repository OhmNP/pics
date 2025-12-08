# PhotoSync Authentication - Future Enhancements

**Document Purpose**: Future improvement requests for the authentication system  
**Status**: Not yet implemented - reference for future work

---

## Enhancement 1: Multiple Concurrent Sessions Support

**Priority**: Medium  
**Estimated Time**: 1-2 hours  
**Complexity**: Low

### Current Limitation
Currently, when a user logs in, only one session can be active. A new login doesn't explicitly invalidate previous sessions, but there's no way to manage multiple sessions.

### Proposed Solution

#### 1. Database Changes
No schema changes needed - the current design already supports multiple sessions per user.

#### 2. Session Management API

**New Endpoint: GET /api/auth/sessions**
```json
{
  "sessions": [
    {
      "id": 1,
      "created_at": "2025-12-07T08:00:00Z",
      "expires_at": "2025-12-07T09:00:00Z",
      "ip_address": "192.168.1.100",
      "device": "Chrome on Windows",
      "is_current": true
    },
    {
      "id": 2,
      "created_at": "2025-12-07T07:30:00Z",
      "expires_at": "2025-12-07T08:30:00Z",
      "ip_address": "192.168.1.101",
      "device": "Firefox on Mac",
      "is_current": false
    }
  ]
}
```

**New Endpoint: DELETE /api/auth/sessions/:id**
- Allows users to revoke specific sessions
- Cannot revoke current session (use logout instead)

#### 3. Implementation Steps

1. **Add device tracking** (30 min):
   - Parse User-Agent header
   - Store device info in sessions table
   - Add `device_info` column to sessions table

2. **Implement session listing** (20 min):
   - Create `getSessionsByUserId()` in DatabaseManager
   - Add endpoint handler in ApiServer
   - Return list with current session marked

3. **Implement session revocation** (20 min):
   - Create `deleteSessionById()` in DatabaseManager
   - Add endpoint handler in ApiServer
   - Validate user owns the session

4. **Frontend UI** (30 min):
   - Create "Active Sessions" page
   - Show list of sessions with device/location
   - Add "Revoke" button for each session

#### 4. Code Changes Required

**DatabaseManager.h**:
```cpp
std::vector<AuthSession> getSessionsByUserId(int userId);
bool deleteSessionById(int sessionId, int userId); // userId for validation
```

**ApiServer.cpp**:
```cpp
// GET /api/auth/sessions
CROW_ROUTE((*g_app), "/api/auth/sessions")
    .methods("GET"_method)([this](const crow::request &req) {
        int userId;
        if (!validateSession(req.get_header_value("Authorization"), userId)) {
            return crow::response(401, "{\"error\":\"Unauthorized\"}");
        }
        
        auto sessions = db_.getSessionsByUserId(userId);
        // Format and return JSON
    });

// DELETE /api/auth/sessions/<id>
CROW_ROUTE((*g_app), "/api/auth/sessions/<int>")
    .methods("DELETE"_method)([this](const crow::request &req, int sessionId) {
        int userId;
        if (!validateSession(req.get_header_value("Authorization"), userId)) {
            return crow::response(401, "{\"error\":\"Unauthorized\"}");
        }
        
        bool deleted = db_.deleteSessionById(sessionId, userId);
        // Return success/failure
    });
```

#### 5. Benefits
- Users can see all active sessions
- Revoke sessions from lost/stolen devices
- Better security awareness
- Audit trail of login activity

---

## Enhancement 2: Production Hardening

**Priority**: High (for production deployment)  
**Estimated Time**: 4-6 hours  
**Complexity**: Medium

### 2.1 Monitoring & Alerts

#### Failed Login Monitoring
**Implementation**: 2 hours

**Metrics to Track**:
- Failed login attempts per minute/hour
- Lockout events
- Unusual login patterns (time, location)

**Alert Triggers**:
- More than 10 failed attempts in 1 minute
- More than 5 lockout events in 1 hour
- Login from new IP address (optional)

**Implementation**:
```cpp
// Add to ApiServer.cpp
void ApiServer::logSecurityEvent(const std::string &event, const std::string &details) {
    // Log to security log file
    std::ofstream securityLog("security.log", std::ios::app);
    auto now = std::time(nullptr);
    securityLog << std::put_time(std::gmtime(&now), "%Y-%m-%d %H:%M:%S")
                << " [SECURITY] " << event << ": " << details << std::endl;
    
    // Send to monitoring system (e.g., Prometheus, Datadog)
    // metrics.increment("auth.failed_login");
}
```

**Alert Configuration** (`server.conf`):
```ini
[monitoring]
enable_security_alerts = true
alert_email = admin@example.com
failed_login_threshold = 10
lockout_threshold = 5
```

---

#### Session Metrics
**Implementation**: 1 hour

**Metrics**:
- Active sessions count
- Average session duration
- Session creation rate
- Session expiration rate

**Dashboard Queries**:
```sql
// Active sessions
SELECT COUNT(*) FROM sessions WHERE expires_at > datetime('now');

// Sessions created today
SELECT COUNT(*) FROM sessions WHERE created_at > date('now');

// Average session duration
SELECT AVG(julianday(expires_at) - julianday(created_at)) * 24 * 60 
FROM sessions WHERE created_at > date('now', '-7 days');
```

---

### 2.2 Database Backups

#### Automated Backup Strategy
**Implementation**: 2 hours

**Backup Schedule**:
- Full backup: Daily at 2 AM
- Incremental backup: Every 6 hours
- Retention: 30 days

**Implementation** (PowerShell script):
```powershell
# backup_database.ps1
$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$backupDir = ".\backups"
$dbPath = ".\photosync.db"
$backupPath = "$backupDir\photosync_$timestamp.db"

# Create backup directory
New-Item -ItemType Directory -Force -Path $backupDir

# Copy database
Copy-Item $dbPath $backupPath

# Compress backup
Compress-Archive -Path $backupPath -DestinationPath "$backupPath.zip"
Remove-Item $backupPath

# Delete backups older than 30 days
Get-ChildItem $backupDir -Filter "*.zip" | 
    Where-Object { $_.LastWriteTime -lt (Get-Date).AddDays(-30) } | 
    Remove-Item

Write-Host "Backup completed: $backupPath.zip"
```

**Scheduled Task** (Windows):
```powershell
$action = New-ScheduledTaskAction -Execute "PowerShell.exe" `
    -Argument "-File C:\path\to\backup_database.ps1"
$trigger = New-ScheduledTaskTrigger -Daily -At 2am
Register-ScheduledTask -TaskName "PhotoSync DB Backup" `
    -Action $action -Trigger $trigger
```

---

#### Backup Verification
**Implementation**: 30 minutes

**Verification Script**:
```powershell
# verify_backup.ps1
$backupPath = ".\backups\photosync_latest.db"

# Extract backup
Expand-Archive -Path "$backupPath.zip" -DestinationPath ".\temp"

# Verify database integrity
sqlite3 .\temp\photosync_*.db "PRAGMA integrity_check;"

# Verify table structure
sqlite3 .\temp\photosync_*.db ".schema" > schema_backup.txt
sqlite3 .\photosync.db ".schema" > schema_current.txt
Compare-Object (Get-Content schema_backup.txt) (Get-Content schema_current.txt)

# Cleanup
Remove-Item -Recurse .\temp
```

---

### 2.3 Security Hardening

#### HTTPS Configuration
**Implementation**: 1 hour

**Reverse Proxy** (nginx):
```nginx
server {
    listen 443 ssl http2;
    server_name photosync.example.com;

    ssl_certificate /path/to/cert.pem;
    ssl_certificate_key /path/to/key.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;

    location / {
        proxy_pass http://localhost:50506;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

---

#### Security Headers
**Implementation**: 30 minutes

**Add to ApiServer.cpp**:
```cpp
void addSecurityHeaders(crow::response &res) {
    res.add_header("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
    res.add_header("X-Content-Type-Options", "nosniff");
    res.add_header("X-Frame-Options", "DENY");
    res.add_header("X-XSS-Protection", "1; mode=block");
    res.add_header("Content-Security-Policy", 
        "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'");
}
```

---

#### Audit Logging
**Implementation**: 1 hour

**Events to Log**:
- User login/logout
- Failed login attempts
- Session creation/deletion
- Password changes
- Admin actions

**Log Format** (JSON):
```json
{
  "timestamp": "2025-12-07T09:20:30Z",
  "event": "user_login",
  "user_id": 1,
  "username": "admin",
  "ip_address": "192.168.1.100",
  "success": true,
  "session_id": "abc123..."
}
```

**Implementation**:
```cpp
void ApiServer::auditLog(const std::string &event, const json &details) {
    std::ofstream auditLog("audit.log", std::ios::app);
    json logEntry = {
        {"timestamp", getCurrentTimestamp()},
        {"event", event},
        {"details", details}
    };
    auditLog << logEntry.dump() << std::endl;
}
```

---

## Implementation Priority

### Phase 1: Production Hardening (High Priority)
1. ✅ HTTPS configuration (1 hour)
2. ✅ Security headers (30 min)
3. ✅ Database backups (2 hours)
4. ✅ Failed login monitoring (2 hours)
5. ✅ Audit logging (1 hour)

**Total Time**: ~6.5 hours

### Phase 2: Multiple Sessions (Medium Priority)
1. ✅ Session listing endpoint (20 min)
2. ✅ Session revocation endpoint (20 min)
3. ✅ Device tracking (30 min)
4. ✅ Frontend UI (30 min)

**Total Time**: ~1.5 hours

---

## Configuration Changes Required

**server.conf additions**:
```ini
[monitoring]
enable_security_alerts = true
alert_email = admin@example.com
failed_login_threshold = 10
lockout_threshold = 5
enable_audit_log = true
audit_log_path = ./audit.log

[backup]
enable_auto_backup = true
backup_schedule = daily
backup_time = 02:00
backup_retention_days = 30
backup_path = ./backups

[security]
enable_https = true
require_https = true
enable_security_headers = true
```

---

## Testing Checklist

### Multiple Sessions
- [ ] Create multiple sessions for same user
- [ ] List all active sessions
- [ ] Revoke specific session
- [ ] Verify revoked session is invalid
- [ ] Verify current session cannot be revoked

### Production Hardening
- [ ] Verify HTTPS redirects work
- [ ] Check security headers in response
- [ ] Test backup script execution
- [ ] Verify backup integrity
- [ ] Test alert triggers
- [ ] Review audit logs
- [ ] Load test with monitoring enabled

---

## Estimated Total Time

- **Multiple Concurrent Sessions**: 1-2 hours
- **Production Hardening**: 4-6 hours
- **Testing & Documentation**: 1-2 hours

**Grand Total**: 6-10 hours

---

## Notes

- These enhancements are **optional** for basic production use
- Prioritize based on your specific requirements
- Production hardening is **highly recommended** before public deployment
- Multiple sessions is a **nice-to-have** feature for user convenience

---

# Integrated Review Findings (2025-12-07)

## Enhancement 3: Dynamic API Configuration

**Priority**: High (Must Fix Before Deployment)  
**Estimated Time**: 1 hour  
**Complexity**: Low

### Current Limitation
The API URL is hardcoded in `src/services/api.ts` as `http://localhost:50506/api`. This prevents the application from working in:
1. Production environments where the API might be on a different port or domain.
2. Network access scenarios (accessing from mobile via IP).

### Proposed Solution
1. **Use Relative Path**: Change base URL to `/api` (assumes serving from same origin or proxy).
2. **Environment Variables**: Use `import.meta.env.VITE_API_URL` to allow build-time configuration.

---

## Enhancement 4: Server-Side Search

**Priority**: Medium  
**Estimated Time**: 2 hours  
**Complexity**: Low

### Current Limitation
The search bar in `Photos.tsx` only filters the *currently loaded* photos on the client side. If a user has 1000 photos but only 50 are loaded, searching for "Holiday" might miss results that are on page 2.

### Proposed Solution
1. **Backend**: Update `handleGetMedia` to accept a `search` query parameter and filter the SQL query:
   ```sql
   SELECT ... FROM metadata WHERE filename LIKE '%search%' ...
   ```
2. **Frontend**: Update `useInfiniteScroll` triggering to pass the search term to `fetchFunction`.

---

## Enhancement 5: Infinite Scroll Optimization

**Priority**: Low  
**Estimated Time**: 1 hour  
**Complexity**: Medium

### Current Limitation
The infinite scroll mechanism relies on a "sentinel" element. If the list is reset (e.g., changing filters) and the new content is too short to push the sentinel off-screen, or if the layout shifts, the "load more" trigger might behave inconsistently.

### Proposed Solution
1. **virtualization**: Consider `react-window` or `react-virtuoso` for better performance with large lists.
2. **Manual Check**: Add a check on filter change to manually invoke `loadMore` if the screen isn't full.

---

**Document Created**: 2025-12-07  
**Last Updated**: 2025-12-07  
**Status**: Ready for implementation when needed
