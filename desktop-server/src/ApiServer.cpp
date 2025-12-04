#include "ApiServer.h"
#include "ConnectionManager.h"
#include "Logger.h"
#include <crow.h>
#include <fstream>
#include <nlohmann/json.hpp>
#include <sstream>
#include <thread>

using json = nlohmann::json;

// Global Crow app instance
static crow::SimpleApp *g_app = nullptr;
static std::thread *g_apiThread = nullptr;

// Global UI path - detected at runtime
static std::string g_uiPath;

// Detect the correct UI path based on available directories
static std::string detectUIPath() {
  // Priority order: installed location, dev build, dev source
  std::vector<std::string> possiblePaths = {
      "./web/renderer/",               // Installed location
      "./ui-dashboard/dist/renderer/", // Development build
      "./ui-dashboard/"                // Development source
  };

  for (const auto &path : possiblePaths) {
    std::string testFile = path + "index.html";
    std::ifstream test(testFile);
    if (test.good()) {
      LOG_INFO("Using UI path: " + path);
      return path;
    }
  }

  LOG_WARN("No valid UI path found, defaulting to ./web/renderer/");
  return "./web/renderer/";
}

ApiServer::ApiServer(DatabaseManager &db, ConfigManager &config)
    : db_(db), config_(config), running_(false) {}

ApiServer::~ApiServer() { stop(); }

void ApiServer::start(int port) {
  if (running_) {
    LOG_WARN("API server already running");
    return;
  }

  LOG_INFO("Starting API server on port " + std::to_string(port));

  // Detect UI path before setting up routes
  g_uiPath = detectUIPath();

  g_app = new crow::SimpleApp();
  g_app->loglevel(crow::LogLevel::Warning);

  setupRoutes();

  // Start server in separate thread
  g_apiThread =
      new std::thread([port]() { g_app->port(port).multithreaded().run(); });

  running_ = true;
  LOG_INFO("API server started successfully");
}

void ApiServer::stop() {
  if (!running_) {
    return;
  }

  LOG_INFO("Stopping API server");

  if (g_app) {
    g_app->stop();
  }

  if (g_apiThread && g_apiThread->joinable()) {
    g_apiThread->join();
    delete g_apiThread;
    g_apiThread = nullptr;
  }

  delete g_app;
  g_app = nullptr;

  running_ = false;
  LOG_INFO("API server stopped");
}

bool ApiServer::isRunning() const { return running_; }

void ApiServer::setupRoutes() {
  // Enable CORS for UI app
  CROW_ROUTE((*g_app), "/api/<string>")
      .methods("OPTIONS"_method)([](const std::string &) {
        auto res = crow::response(200);
        res.add_header("Access-Control-Allow-Origin", "*");
        res.add_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        res.add_header("Access-Control-Allow-Headers", "Content-Type");
        return res;
      });

  // GET /api/stats - Server statistics
  CROW_ROUTE((*g_app), "/api/stats").methods("GET"_method)([this]() {
    auto res = crow::response(handleGetStats());
    res.add_header("Access-Control-Allow-Origin", "*");
    res.add_header("Content-Type", "application/json");
    return res;
  });

  // GET /api/photos - Photo list with pagination
  CROW_ROUTE((*g_app), "/api/photos")
      .methods("GET"_method)([this](const crow::request &req) {
        int page = req.url_params.get("page")
                       ? std::stoi(req.url_params.get("page"))
                       : 1;
        int limit = req.url_params.get("limit")
                        ? std::stoi(req.url_params.get("limit"))
                        : 50;
        std::string clientId = req.url_params.get("client_id")
                                   ? req.url_params.get("client_id")
                                   : "";
        std::string search =
            req.url_params.get("search") ? req.url_params.get("search") : "";

        auto res =
            crow::response(handleGetPhotos(page, limit, clientId, search));
        res.add_header("Access-Control-Allow-Origin", "*");
        res.add_header("Content-Type", "application/json");
        return res;
      });

  // GET /api/clients - Client list
  CROW_ROUTE((*g_app), "/api/clients").methods("GET"_method)([this]() {
    auto res = crow::response(handleGetClients());
    res.add_header("Access-Control-Allow-Origin", "*");
    res.add_header("Content-Type", "application/json");
    return res;
  });

  // GET /api/sessions - Session history
  CROW_ROUTE((*g_app), "/api/sessions")
      .methods("GET"_method)([this](const crow::request &req) {
        int page = req.url_params.get("page")
                       ? std::stoi(req.url_params.get("page"))
                       : 1;
        int limit = req.url_params.get("limit")
                        ? std::stoi(req.url_params.get("limit"))
                        : 50;
        std::string clientId = req.url_params.get("client_id")
                                   ? req.url_params.get("client_id")
                                   : "";
        std::string status =
            req.url_params.get("status") ? req.url_params.get("status") : "";

        auto res =
            crow::response(handleGetSessions(page, limit, clientId, status));
        res.add_header("Access-Control-Allow-Origin", "*");
        res.add_header("Content-Type", "application/json");
        return res;
      });

  // GET /api/connections - Active connections
  CROW_ROUTE((*g_app), "/api/connections").methods("GET"_method)([this]() {
    auto res = crow::response(handleGetConnections());
    res.add_header("Access-Control-Allow-Origin", "*");
    res.add_header("Content-Type", "application/json");
    return res;
  });

  // GET /api/config - Server configuration
  CROW_ROUTE((*g_app), "/api/config").methods("GET"_method)([this]() {
    auto res = crow::response(handleGetConfig());
    res.add_header("Access-Control-Allow-Origin", "*");
    res.add_header("Content-Type", "application/json");
    return res;
  });

  // POST /api/config - Update configuration
  CROW_ROUTE((*g_app), "/api/config")
      .methods("POST"_method)([this](const crow::request &req) {
        auto res = crow::response(handlePostConfig(req.body));
        res.add_header("Access-Control-Allow-Origin", "*");
        res.add_header("Content-Type", "application/json");
        return res;
      });

  // Explicit route for root path
  CROW_ROUTE((*g_app), "/")
  ([]() {
    std::string indexPath = g_uiPath + "index.html";
    std::ifstream file(indexPath, std::ios::binary);
    if (!file) {
      LOG_ERROR("Failed to load index.html from: " + indexPath);
      return crow::response(404, "UI Dashboard not found");
    }
    std::string content((std::istreambuf_iterator<char>(file)),
                        std::istreambuf_iterator<char>());
    file.close();
    auto res = crow::response(content);
    res.add_header("Content-Type", "text/html");
    return res;
  });

  // Catchall route for static files - must be last
  CROW_ROUTE((*g_app), "/<path>")
  ([](std::string path) {
    // Default to index.html for root
    if (path.empty()) {
      path = "index.html";
    }

    // Build file path using detected UI path
    std::string file_path = g_uiPath + path;

    // Try to open file
    std::ifstream file(file_path, std::ios::binary);
    if (!file) {
      // For SPA routing, serve index.html for non-API, non-assets routes
      if (path.find("api/") != 0 && path.find("assets/") != 0) {
        std::string indexPath = g_uiPath + "index.html";
        std::ifstream indexFile(indexPath, std::ios::binary);
        if (indexFile) {
          std::string content((std::istreambuf_iterator<char>(indexFile)),
                              std::istreambuf_iterator<char>());
          indexFile.close();
          auto res = crow::response(content);
          res.add_header("Content-Type", "text/html");
          return res;
        }
      }
      LOG_WARN("File not found: " + file_path);
      return crow::response(404, "Not found");
    }

    // Read file content
    std::string content((std::istreambuf_iterator<char>(file)),
                        std::istreambuf_iterator<char>());
    file.close();

    // Determine content type based on file extension
    std::string content_type = "text/html";
    if (path.find(".css") != std::string::npos) {
      content_type = "text/css";
    } else if (path.find(".js") != std::string::npos) {
      content_type = "application/javascript";
    } else if (path.find(".json") != std::string::npos) {
      content_type = "application/json";
    } else if (path.find(".png") != std::string::npos) {
      content_type = "image/png";
    } else if (path.find(".jpg") != std::string::npos ||
               path.find(".jpeg") != std::string::npos) {
      content_type = "image/jpeg";
    } else if (path.find(".svg") != std::string::npos) {
      content_type = "image/svg+xml";
    } else if (path.find(".ico") != std::string::npos) {
      content_type = "image/x-icon";
    } else if (path.find(".woff") != std::string::npos) {
      content_type = "font/woff";
    } else if (path.find(".woff2") != std::string::npos) {
      content_type = "font/woff2";
    }

    auto res = crow::response(content);
    res.add_header("Content-Type", content_type);
    return res;
  });
}

std::string ApiServer::handleGetStats() {
  try {
    // Get statistics from database
    int totalPhotos = db_.getTotalPhotoCount();
    int totalClients = db_.getTotalClientCount();
    int completedSessions = db_.getCompletedSessionCount();
    long long storageUsed = db_.getTotalStorageUsed();

    json response = {{"totalPhotos", totalPhotos},
                     {"connectedClients", 0}, // TODO: Track active connections
                     {"totalClients", totalClients},
                     {"totalSessions", completedSessions},
                     {"storageUsed", storageUsed},
                     {"storageLimit",
                      config_.getMaxStorageGB() * 1073741824LL}, // GB to bytes
                     {"uptime", 0}, // TODO: Track server uptime
                     {"serverStatus", "running"}};

    return response.dump();
  } catch (const std::exception &e) {
    LOG_ERROR("Error in handleGetStats: " + std::string(e.what()));
    json error = {{"error", e.what()}};
    return error.dump();
  }
}

std::string ApiServer::handleGetPhotos(int page, int limit,
                                       const std::string &clientId,
                                       const std::string &search) {
  try {
    // TODO: Implement pagination in DatabaseManager
    json response = {
        {"photos", json::array()},
        {"pagination",
         {{"page", page}, {"limit", limit}, {"total", 0}, {"pages", 0}}}};

    return response.dump();
  } catch (const std::exception &e) {
    LOG_ERROR("Error in handleGetPhotos: " + std::string(e.what()));
    json error = {{"error", e.what()}};
    return error.dump();
  }
}

std::string ApiServer::handleGetClients() {
  try {
    // TODO: Implement getClients in DatabaseManager
    json response = {{"clients", json::array()}};

    return response.dump();
  } catch (const std::exception &e) {
    LOG_ERROR("Error in handleGetClients: " + std::string(e.what()));
    json error = {{"error", e.what()}};
    return error.dump();
  }
}

std::string ApiServer::handleGetSessions(int page, int limit,
                                         const std::string &clientId,
                                         const std::string &status) {
  try {
    // TODO: Implement getSessions in DatabaseManager
    json response = {
        {"sessions", json::array()},
        {"pagination",
         {{"page", page}, {"limit", limit}, {"total", 0}, {"pages", 0}}}};

    return response.dump();
  } catch (const std::exception &e) {
    LOG_ERROR("Error in handleGetSessions: " + std::string(e.what()));
    json error = {{"error", e.what()}};
    return error.dump();
  }
}

std::string ApiServer::handleGetConnections() {
  try {
    auto &connMgr = ConnectionManager::getInstance();
    auto connections = connMgr.getActiveConnections();

    json activeConnections = json::array();

    for (const auto &[sessionId, info] : connections) {
      // Calculate duration
      auto now = std::chrono::system_clock::now();
      auto duration = std::chrono::duration_cast<std::chrono::seconds>(
                          now - info.connectedAt)
                          .count();

      // Format timestamps
      auto connectedTime =
          std::chrono::system_clock::to_time_t(info.connectedAt);
      auto lastActivityTime =
          std::chrono::system_clock::to_time_t(info.lastActivity);

      char connectedBuf[32], activityBuf[32];
      std::strftime(connectedBuf, sizeof(connectedBuf), "%Y-%m-%dT%H:%M:%SZ",
                    std::gmtime(&connectedTime));
      std::strftime(activityBuf, sizeof(activityBuf), "%Y-%m-%dT%H:%M:%SZ",
                    std::gmtime(&lastActivityTime));

      json conn = {{"session_id", info.sessionId},
                   {"device_id", info.deviceId},
                   {"ip_address", info.ipAddress},
                   {"connected_at", connectedBuf},
                   {"status", info.status},
                   {"photos_uploaded", info.photosUploaded},
                   {"bytes_transferred", info.bytesTransferred},
                   {"last_activity", activityBuf},
                   {"duration_seconds", duration}};

      activeConnections.push_back(conn);
    }

    json response = {{"active_connections", activeConnections},
                     {"total_active", connMgr.getActiveCount()}};

    return response.dump();
  } catch (const std::exception &e) {
    LOG_ERROR("Error in handleGetConnections: " + std::string(e.what()));
    json error = {{"error", e.what()}};
    return error.dump();
  }
}

std::string ApiServer::handleGetConfig() {
  try {
    json response = {{"network",
                      {{"port", config_.getPort()},
                       {"maxConnections", config_.getMaxConnections()},
                       {"timeout", config_.getTimeoutSeconds()}}},
                     {"storage",
                      {{"photosDir", config_.getPhotosDir()},
                       {"dbPath", config_.getDbPath()},
                       {"maxStorageGB", config_.getMaxStorageGB()}}},
                     {"logging",
                      {{"level", config_.getLogLevel()},
                       {"file", config_.getLogFile()},
                       {"consoleOutput", config_.getConsoleOutput()}}}};

    return response.dump();
  } catch (const std::exception &e) {
    LOG_ERROR("Error in handleGetConfig: " + std::string(e.what()));
    json error = {{"error", e.what()}};
    return error.dump();
  }
}

std::string ApiServer::handlePostConfig(const std::string &requestBody) {
  try {
    auto requestData = json::parse(requestBody);

    // TODO: Update configuration
    // This would require modifying ConfigManager to support runtime updates

    json response = {{"success", true},
                     {"message", "Configuration updated. Restart required."}};

    return response.dump();
  } catch (const std::exception &e) {
    LOG_ERROR("Error in handlePostConfig: " + std::string(e.what()));
    json error = {{"error", e.what()}};
    return error.dump();
  }
}
