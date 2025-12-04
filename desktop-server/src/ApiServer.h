#pragma once

#include "ConfigManager.h"
#include "DatabaseManager.h"
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
};
