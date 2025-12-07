#pragma once

#include <memory>
#include <sqlite3.h>
#include <string>
#include <vector>

struct PhotoMetadata {
  std::string filename;
  long long size;
  std::string hash;
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

  struct ClientRecord {
    int id;
    std::string deviceId;
    std::string lastSeen;
    int photoCount;
    long long storageUsed;
  };
  std::vector<ClientRecord> getClients();

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

  // API Statistics Methods
  int getTotalPhotoCount();
  int getTotalClientCount();
  int getCompletedSessionCount();
  long long getTotalStorageUsed();

  std::string getCurrentTimestamp();

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

private:
  sqlite3 *db_;
  bool executeSQL(const std::string &sql);
};
