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
};

struct SyncSession {
  int id;
  int clientId;
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

class DatabaseManager {
public:
  DatabaseManager();
  ~DatabaseManager();

  bool open(const std::string &dbPath);
  void close();
  bool createSchema();

  // Client operations
  int getOrCreateClient(const std::string &deviceId);
  bool updateClientLastSeen(int clientId);
  bool deleteClient(int clientId);

  struct ClientRecord {
    int id;
    std::string deviceId;
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

  std::string getCurrentTimestamp();

  // Migration operations
  bool migratePhotosToMetadata();

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

private:
  sqlite3 *db_;
  bool executeSQL(const std::string &sql);
};
