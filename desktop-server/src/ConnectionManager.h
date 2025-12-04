#pragma once

#include <chrono>
#include <map>
#include <mutex>
#include <string>

struct ConnectionInfo {
  int sessionId;
  std::string deviceId;
  std::string ipAddress;
  std::chrono::system_clock::time_point connectedAt;
  std::string status; // "handshake", "syncing", "idle"
  int photosUploaded;
  long long bytesTransferred;
  std::chrono::system_clock::time_point lastActivity;
};

class ConnectionManager {
public:
  static ConnectionManager &getInstance();

  void addConnection(int sessionId, const std::string &deviceId,
                     const std::string &ipAddress);
  void removeConnection(int sessionId);
  void updateStatus(int sessionId, const std::string &status);
  void updateProgress(int sessionId, int photosUploaded,
                      long long bytesTransferred);
  void updateActivity(int sessionId);

  std::map<int, ConnectionInfo> getActiveConnections();
  int getActiveCount();

private:
  ConnectionManager() = default;
  ~ConnectionManager() = default;
  ConnectionManager(const ConnectionManager &) = delete;
  ConnectionManager &operator=(const ConnectionManager &) = delete;

  std::map<int, ConnectionInfo> connections_;
  std::mutex mutex_;
};
