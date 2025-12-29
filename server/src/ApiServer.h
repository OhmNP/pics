#pragma once

#include "ConfigManager.h"
#include "DatabaseManager.h"
#include "IntegrityScanner.h" // Added
#include <chrono>
#include <crow.h>
#include <map>
#include <mutex>
#include <string>

// REST API Server for UI Dashboard
// Provides HTTP endpoints for management
class ApiServer {
public:
  ApiServer(DatabaseManager &db, ConfigManager &config,
            IntegrityScanner *scanner = nullptr);
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
  IntegrityScanner *scanner_; // Added
  bool running_;
  std::chrono::system_clock::time_point startTime_;

  // Rate limiting for login attempts
  std::map<std::string, std::pair<int, time_t>>
      loginAttempts_; // IP -> (count, last_attempt_time)
  std::mutex loginAttemptsMutex_;

  // Struct for integrity status
  struct IntegrityStatus {
    int tombstones = 0;
    std::string timestamp;
    std::string status = "idle";
    std::string message;
  };

  // API endpoint handlers
  void setupRoutes();
  std::string handleGetStats();
  std::string handleGetPhotos(int page, int limit, const std::string &clientId,
                              const std::string &search);
  std::string handleGetClients();
  std::string handleGetClientDetails(int clientId);
  std::string handleDeleteClient(int clientId);
  std::string handleGetSessions(int page, int limit,
                                const std::string &clientId,
                                const std::string &status);
  std::string handleGetConnections();
  std::string handleGetConfig();
  std::string handlePostConfig(const std::string &requestBody);
  std::string handleGetNetworkInfo();

  // Authentication endpoint handlers
  std::string handlePostLogin(const crow::request &req);
  std::string handlePostLogout(const crow::request &req);
  std::string handleGetValidate(const std::string &authHeader);

  // Authentication middleware
  bool validateSession(const std::string &authHeader, int &userId);
  bool validateAuth(const crow::request &req);

  // Phase 4: Sync Feed
  std::string handleGetChanges(const std::string &cursorStr, int limit);

  // Media grid endpoints
  std::string handleGetMedia(int offset, int limit, int clientId,
                             const std::string &startDate,
                             const std::string &endDate,
                             const std::string &searchQuery = "");
  std::string handleDeleteMedia(int photoId);
  void handleGetThumbnail(crow::response &res, int photoId);
  void handleGetMediaDownload(crow::response &res, int photoId);
  std::string handlePostGenerateToken(const crow::request &req);
  std::string handlePostRegenerateThumbnails(const crow::request &req);

  // Phase 6: Ops
  // Phase 6: Ops
  // Updated handleGetErrors signature to match implementation plans
  crow::json::wvalue handleGetErrors(int limit, int offset,
                                     const std::string &level,
                                     const std::string &deviceId,
                                     const std::string &since);
  crow::json::wvalue handleGetIntegrityStatus();
  crow::json::wvalue handleGetTopFiles();
  crow::json::wvalue handleGetHealth();
  crow::json::wvalue handleGetIntegrityDetails(const std::string &type,
                                               int limit);
  std::string handleRevokeClient(int clientId);
};
