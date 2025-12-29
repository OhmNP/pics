#pragma once

#include <map>
#include <memory>
#include <sqlite3.h>
#include <string>
#include <vector>

struct PhotoMetadata {
  int id = -1;
  std::string filename;
  std::string originalPath;
  std::string hash;
  long long size = 0;
  int width = 0;
  int height = 0;
  std::string mimeType;
  std::string takenAt;
  std::string receivedAt;
  int clientId = -1;

  // EXIF Data
  std::string cameraMake;
  std::string cameraModel;
  double exposureTime = 0.0;
  double fNumber = 0.0;
  int iso = 0;
  double focalLength = 0.0;
  double gpsLat = 0.0;
  double gpsLon = 0.0;
  double gpsAlt = 0.0;

  // Phase 3: Integrity & Tombstones
  std::string deletedAt;
};

struct SyncSession {
  int id;
  int clientId;
  std::string deviceId;   // Added
  std::string clientName; // Added
  std::string startedAt;
  std::string endedAt;
  int photosReceived;
  std::string status;
};

struct AdminUser {
  int id;
  std::string username;
  std::string passwordHash;
  std::string createdAt;
  std::string lastLogin;
  bool isActive;
};

struct AuthSession {
  int id;
  std::string sessionToken;
  int userId;
  std::string createdAt;
  std::string expiresAt;
  std::string ipAddress;
};

struct PairingToken {
  int id;
  std::string token;
  std::string createdAt;
  std::string expiresAt;
  bool isUsed;
};

// Phase 2: Resumable Upload Session
struct UploadSession {
  std::string uploadId;
  int clientId;
  std::string fileHash;
  std::string filename;
  long long fileSize;
  long long receivedBytes;
  std::string createdAt;
  std::string expiresAt;
  std::string status; // PENDING, COMPLETE
};

// Phase 6: Error Logging
struct ErrorLog {
  int id;
  int code;
  std::string message;
  std::string traceId;
  std::string timestamp;
  std::string severity; // INFO, WARN, ERROR, CRITICAL
  std::string deviceId;
  std::string context; // JSON string
};

class DatabaseManager {
public:
  DatabaseManager();
  ~DatabaseManager();

  bool open(const std::string &dbPath);
  void close();
  bool createSchema();

  // Client operations
  int getOrCreateClient(const std::string &deviceId,
                        const std::string &userName = "");
  bool updateClientLastSeen(int clientId);
  bool deleteClient(int clientId);

  struct ClientRecord {
    int id;
    std::string deviceId;
    std::string userName;
    std::string lastSeen;
    int photoCount;
    long long storageUsed;
  };
  std::vector<ClientRecord> getClients();
  ClientRecord getClientDetails(int clientId);

  // Session operations
  int createSession(int clientId);
  void updateSessionPhotosReceived(int sessionId, int photosReceived);
  void updateSessionPhotoCount(int sessionId, int photoCount);
  void finalizeSession(int sessionId, const std::string &status);
  std::vector<SyncSession> getSessions(int offset, int limit, int clientId = -1,
                                       const std::string &status = "");

  // Photo operations
  bool insertPhoto(int clientId, const PhotoMetadata &photo,
                   const std::string &filePath = "");
  bool photoExists(const std::string &hash);
  int getPhotoCount(int clientId);
  std::vector<std::string>
  batchCheckHashes(const std::vector<std::string> &hashes);

  // Media grid operations
  std::vector<PhotoMetadata>
  getPhotosWithPagination(int offset, int limit, int clientId = -1,
                          const std::string &startDate = "",
                          const std::string &endDate = "",
                          const std::string &searchQuery = "");
  PhotoMetadata getPhotoById(int photoId);
  int getFilteredPhotoCount(int clientId = -1,
                            const std::string &startDate = "",
                            const std::string &endDate = "",
                            const std::string &searchQuery = "");

  // API Statistics Methods
  int getTotalPhotoCount();
  int getTotalClientCount();
  int getCompletedSessionCount();
  long long getTotalStorageUsed();

  // Phase 6: Error Logging Methods
  bool logError(int code, const std::string &message,
                const std::string &traceId = "",
                const std::string &severity = "ERROR",
                const std::string &deviceId = "",
                const std::string &context = "");
  std::vector<ErrorLog> getRecentErrors(int limit = 50, int offset = 0,
                                        const std::string &level = "",
                                        const std::string &deviceId = "",
                                        const std::string &since = "");

  // Phase 6: System Health & Stats
  struct DiskUsage {
    long long free;
    long long total;
    long long available;
  };
  DiskUsage getDiskUsage();
  long long getDbSize();
  int getPendingUploadCount();
  int getFailedUploadCount();
  int getActiveSessionCount();

  // Phase 6: Device Ops
  bool revokeClientAuth(int clientId);
  struct DeviceStats {
    int uploads24h;
    int failures24h;
  };
  DeviceStats getDeviceStats24h(int clientId);

  // Phase 6: Integrity Details
  std::vector<std::string> getIntegrityDetails(const std::string &type,
                                               int limit);

  // Phase 6: Top Files
  struct FileInfo {
    int id;
    std::string filename;
    std::string mimeType;
    long long size;
    std::string originalPath;
  };
  std::vector<FileInfo> getLargestFiles(int limit = 50);

  // Backlog: Deferred Cleanup
  bool completeUploadSession(
      const std::string &uploadId); // Marks COMPLETE + extends expiry

  // Phase 3: Integrity & Tombstones
  std::vector<PhotoMetadata> getAllPhotos(); // For Integrity Scanner
  bool softDeletePhoto(int photoId);
  int purgeDeletedPhotos(int retentionDays); // Returns count purged
  std::vector<std::string>
  getOrphanBlobs(const std::vector<std::string> &filesOnDisk);

  std::string getCurrentTimestamp();

  // Migration operations
  bool migratePhotosToMetadata();
  bool migrateSchema();

  // Authentication operations
  bool createAdminUser(const std::string &username,
                       const std::string &passwordHash);
  AdminUser getAdminUserByUsername(const std::string &username);
  bool createAuthSession(const std::string &sessionToken, int userId,
                         const std::string &expiresAt,
                         const std::string &ipAddress = "");
  AuthSession getSessionByToken(const std::string &sessionToken);
  bool deleteSession(const std::string &sessionToken);
  int cleanupExpiredSessions();
  bool insertInitialAdminUser();

  // Password reset operations
  bool createPasswordResetToken(const std::string &username,
                                const std::string &token,
                                const std::string &expiresAt);
  bool validatePasswordResetToken(const std::string &token);
  std::string getUsernameFromResetToken(const std::string &token);
  bool resetPassword(const std::string &token,
                     const std::string &newPasswordHash);
  int cleanupExpiredResetTokens();

  // Pairing Token operations
  std::string generatePairingToken();
  bool validatePairingToken(const std::string &token);
  bool markPairingTokenUsed(const std::string &token);
  int cleanupExpiredPairingTokens();

  // Phase 2: Upload Session operations
  bool createUploadSessionTable();
  std::string createUploadSession(int clientId, const std::string &fileHash,
                                  const std::string &filename,
                                  long long fileSize);
  UploadSession getUploadSession(const std::string &uploadId);
  UploadSession getUploadSessionByHash(int clientId,
                                       const std::string &fileHash,
                                       long long fileSize);
  bool updateSessionReceivedBytes(const std::string &uploadId,
                                  long long receivedBytes);
  bool deleteUploadSession(const std::string &uploadId);
  std::vector<std::string> getExpiredUploadSessionIds();
  int cleanupExpiredUploadSessions(); // Now just deletes from DB, use after
                                      // file cleanup? Or remove?
  // I'll keep it but usage will change.

  // Phase 4: Incremental Sync Feed (Changes)
  struct ChangeLogEntry {
    long long changeId;
    std::string op; // CREATE, UPDATE, DELETE
    int mediaId;
    std::string blobHash;
    std::string changedAt;
    // Denormalized/Snapshot data
    std::string filename;
    long long size;
    std::string mimeType;
    std::string takenAt;
    std::string deviceId;
  };

  std::vector<ChangeLogEntry> getChanges(long long sinceId, int limit);

  // Public for Testing/Maintenance
  bool executeSQL(const std::string &sql);

private:
  int logChange(const std::string &op, int mediaId, const std::string &blobHash,
                const std::string &filename, long long size,
                const std::string &mimeType, const std::string &takenAt,
                int clientId);
  sqlite3 *db_;
};
