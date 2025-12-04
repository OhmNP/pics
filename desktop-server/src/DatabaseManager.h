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

private:
  sqlite3 *db_;
  bool executeSQL(const std::string &sql);
};
