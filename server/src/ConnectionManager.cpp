#include "ConnectionManager.h"

ConnectionManager &ConnectionManager::getInstance() {
  static ConnectionManager instance;
  return instance;
}

void ConnectionManager::addConnection(int sessionId,
                                      const std::string &deviceId,
                                      const std::string &ipAddress,
                                      const std::string &userName) {
  std::lock_guard<std::mutex> lock(mutex_);

  ConnectionInfo info;
  info.sessionId = sessionId;
  info.deviceId = deviceId;
  info.userName = userName;
  info.ipAddress = ipAddress;
  info.connectedAt = std::chrono::system_clock::now();
  info.status = "handshake";
  info.photosUploaded = 0;
  info.bytesTransferred = 0;
  info.lastActivity = std::chrono::system_clock::now();

  connections_[sessionId] = info;
}

void ConnectionManager::removeConnection(int sessionId) {
  std::lock_guard<std::mutex> lock(mutex_);
  connections_.erase(sessionId);
}

void ConnectionManager::updateStatus(int sessionId, const std::string &status) {
  std::lock_guard<std::mutex> lock(mutex_);
  auto it = connections_.find(sessionId);
  if (it != connections_.end()) {
    it->second.status = status;
    it->second.lastActivity = std::chrono::system_clock::now();
  }
}

void ConnectionManager::updateProgress(int sessionId, int photosUploaded,
                                       long long bytesTransferred) {
  std::lock_guard<std::mutex> lock(mutex_);
  auto it = connections_.find(sessionId);
  if (it != connections_.end()) {
    it->second.photosUploaded = photosUploaded;
    it->second.bytesTransferred = bytesTransferred;
    it->second.lastActivity = std::chrono::system_clock::now();
  }
}

void ConnectionManager::updateActivity(int sessionId) {
  std::lock_guard<std::mutex> lock(mutex_);
  auto it = connections_.find(sessionId);
  if (it != connections_.end()) {
    it->second.lastActivity = std::chrono::system_clock::now();
  }
}

std::map<int, ConnectionInfo> ConnectionManager::getActiveConnections() {
  std::lock_guard<std::mutex> lock(mutex_);
  return connections_;
}

int ConnectionManager::getActiveCount() {
  std::lock_guard<std::mutex> lock(mutex_);
  return static_cast<int>(connections_.size());
}

bool ConnectionManager::isClientConnected(const std::string &deviceId) {
  std::lock_guard<std::mutex> lock(mutex_);
  for (const auto &pair : connections_) {
    if (pair.second.deviceId == deviceId) {
      return true;
    }
  }
  return false;
}

std::vector<int> ConnectionManager::cleanStaleConnections(int timeoutSeconds) {
  std::lock_guard<std::mutex> lock(mutex_);
  std::vector<int> removedSessions;
  auto now = std::chrono::system_clock::now();

  for (auto it = connections_.begin(); it != connections_.end();) {
    auto duration = std::chrono::duration_cast<std::chrono::seconds>(
        now - it->second.lastActivity);
    if (duration.count() > timeoutSeconds) {
      removedSessions.push_back(it->first);
      it = connections_.erase(it);
    } else {
      ++it;
    }
  }
  return removedSessions;
}
