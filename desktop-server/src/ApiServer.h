#pragma once

#include "ConfigManager.h"
#include "DatabaseManager.h"
#include <crow.h>
#include <map>
#include <mutex>
#include <string>

// REST API Server for UI Dashboard
// Provides HTTP endpoints for monitoring and management
class ApiServer {
public:
  ApiServer(DatabaseManager &db, ConfigManager &config);
  ~ApiServer();

  // Start the API server on specified port
  void start(int port = 50506);

  // Stop the API server
  void stop();

  // Check if server is running
  bool isRunning() const;

private:
  DatabaseManager &db_;
  ConfigManager &config_;
  bool running_;

  // Rate limiting for login attempts
  std::map<std::string, std::pair<int, time_t>>
      loginAttempts_; // IP -> (count, last_attempt_time)
  std::mutex loginAttemptsMutex_;

  // API endpoint handlers
  void setupRoutes();
  std::string handleGetStats();
  std::string handleGetPhotos(int page, int limit, const std::string &clientId,
                              const std::string &search);
  std::string handleGetClients();
  std::string handleGetSessions(int page, int limit,
                                const std::string &clientId,
                                const std::string &status);
  std::string handleGetConnections();
  std::string handleGetConfig();
  std::string handlePostConfig(const std::string &requestBody);

  // Authentication endpoint handlers
  std::string handlePostLogin(const std::string &requestBody);
  std::string handlePostLogout(const std::string &authHeader);
  std::string handleGetValidate(const std::string &authHeader);

  // Authentication middleware
  bool validateSession(const std::string &authHeader, int &userId);

  // Media grid endpoints
  std::string handleGetMedia(int offset, int limit, int clientId,
                             const std::string &startDate,
                             const std::string &endDate);
  void handleGetThumbnail(crow::response &res, int photoId);
  void handleGetMediaDownload(crow::response &res, int photoId);
};
