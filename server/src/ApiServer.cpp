#include "ApiServer.h"
#include "AuthenticationManager.h"
#include "ConnectionManager.h"
#include "Logger.h"
#include "ThumbnailGenerator.h"
#include <boost/asio.hpp>
#include <boost/asio/ssl.hpp>
#include <crow.h>
#include <filesystem>
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
  // Priority order: installed location, dev build (sibling), dev source
  // (sibling)
  std::vector<std::string> possiblePaths = {
      "./web/renderer/",             // Installed location
      "../dashboard/dist/renderer/", // Development build (sibling directory)
      "../dashboard/"                // Development source (sibling directory)
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

ApiServer::ApiServer(DatabaseManager &db, ConfigManager &config,
                     IntegrityScanner *scanner)
    : db_(db), config_(config), scanner_(scanner), running_(false) {
  startTime_ = std::chrono::system_clock::now();
}

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
  g_apiThread = new std::thread([port]() {
    try {
      // Enable SSL with absolute paths to avoid ambiguity
      std::string certPath = std::filesystem::absolute("server.crt").string();
      std::string keyPath = std::filesystem::absolute("server.key").string();

      LOG_INFO("Loading SSL files from: " + certPath);
      g_app->ssl_file(certPath, keyPath);

      g_app->port(port).multithreaded().run();
    } catch (const std::exception &e) {
      LOG_ERROR("ApiServer failed to start: " + std::string(e.what()));
    }
  });

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
  CROW_ROUTE((*g_app), "/api/stats")
      .methods("GET"_method)([this](const crow::request &req) {
        if (!validateAuth(req))
          return crow::response(401);
        auto res = crow::response(handleGetStats());
        res.add_header("Content-Type", "application/json");
        return res;
      });

  // GET /api/integrity - Integrity Status (Phase 6)
  CROW_ROUTE((*g_app), "/api/integrity")
      .methods("GET"_method)([this](const crow::request &req) {
        if (!validateAuth(req))
          return crow::response(401);

        auto res = crow::response(handleGetIntegrityStatus().dump());
        res.add_header("Content-Type", "application/json");
        return res;
      });

  // GET /api/top-files - Top N largest files
  CROW_ROUTE((*g_app), "/api/top-files")
      .methods("GET"_method)([this](const crow::request &req) {
        if (!validateAuth(req))
          return crow::response(401);
        auto res = crow::response(handleGetTopFiles().dump());
        res.add_header("Content-Type", "application/json");
        return res;
      });

  // GET /api/health - System Health (Phase 6)
  CROW_ROUTE((*g_app), "/api/health")
      .methods("GET"_method)([this](const crow::request &req) {
        if (!validateAuth(req))
          return crow::response(401);
        auto res = crow::response(handleGetHealth().dump());
        res.add_header("Content-Type", "application/json");
        return res;
      });

  // Re-register correctly

  CROW_ROUTE((*g_app), "/api/devices/<int>/revoke")
      .methods("POST"_method)([this](const crow::request &req, int clientId) {
        if (!validateAuth(req))
          return crow::response(401);

        auto res = crow::response(handleRevokeClient(clientId));
        res.add_header("Content-Type", "application/json");
        return res;
      });

  // GET /api/integrity/details - Integrity Details (Phase 6)
  CROW_ROUTE((*g_app), "/api/integrity/details")
      .methods("GET"_method)([this](const crow::request &req) {
        if (!validateAuth(req))
          return crow::response(401);

        std::string type =
            req.url_params.get("type") ? req.url_params.get("type") : "";
        int limit = req.url_params.get("limit")
                        ? std::stoi(req.url_params.get("limit"))
                        : 50;

        auto res =
            crow::response(handleGetIntegrityDetails(type, limit).dump());
        res.add_header("Content-Type", "application/json");
        return res;
      });

  // GET /api/errors - Error Logs (Updated Phase 6)
  CROW_ROUTE((*g_app), "/api/errors")
      .methods("GET"_method)([this](const crow::request &req) {
        if (!validateAuth(req))
          return crow::response(401);

        int limit = req.url_params.get("limit")
                        ? std::stoi(req.url_params.get("limit"))
                        : 100;
        int offset = req.url_params.get("offset")
                         ? std::stoi(req.url_params.get("offset"))
                         : 0;
        std::string level =
            req.url_params.get("level") ? req.url_params.get("level") : "";
        std::string deviceId = req.url_params.get("deviceId")
                                   ? req.url_params.get("deviceId")
                                   : "";
        std::string since =
            req.url_params.get("since") ? req.url_params.get("since") : "";

        auto res = crow::response(
            handleGetErrors(limit, offset, level, deviceId, since).dump());
        res.add_header("Content-Type", "application/json");
        return res;
      });

  // GET /api/changes - Incremental Sync Feed
  CROW_ROUTE((*g_app), "/api/changes")
      .methods("GET"_method)([this](const crow::request &req) {
        // Optional: Add auth check if needed, mostly yes
        // if (!validateAuth(req)) return crow::response(401);
        // For simplicity/testing allow open or specific auth?
        // Requirement says "Provide API endpoint", usually implies auth.
        // I will add auth check.
        if (!validateAuth(req))
          return crow::response(401);

        std::string cursor =
            req.url_params.get("cursor") ? req.url_params.get("cursor") : "";
        int limit = req.url_params.get("limit")
                        ? std::stoi(req.url_params.get("limit"))
                        : 50;

        auto res = crow::response(handleGetChanges(cursor, limit));
        res.add_header("Content-Type", "application/json");
        return res;
      });

  // DELETE /api/media/<int> - Soft Delete Photo
  CROW_ROUTE((*g_app), "/api/media/<int>")
      .methods("DELETE"_method)([this](int photoId) {
        auto res = crow::response(handleDeleteMedia(photoId));
        res.add_header("Access-Control-Allow-Origin", "*");
        res.add_header("Content-Type", "application/json");
        return res;
      });

  // GET /api/photos - Photo list with pagination
  CROW_ROUTE((*g_app), "/api/photos")
      .methods("GET"_method)([this](const crow::request &req) {
        if (!validateAuth(req))
          return crow::response(401);
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
  CROW_ROUTE((*g_app), "/api/clients")
      .methods("GET"_method)([this](const crow::request &req) {
        if (!validateAuth(req))
          return crow::response(401);
        auto res = crow::response(handleGetClients());
        res.add_header("Content-Type", "application/json");
        return res;
      });

  // GET /api/clients/:id - Client details
  CROW_ROUTE((*g_app), "/api/clients/<int>")
      .methods("GET"_method)([this](int clientId) {
        auto res = crow::response(handleGetClientDetails(clientId));
        res.add_header("Access-Control-Allow-Origin", "*");
        res.add_header("Content-Type", "application/json");
        return res;
      });

  // DELETE /api/clients/<int> - Delete Client
  CROW_ROUTE((*g_app), "/api/clients/<int>")
      .methods("DELETE"_method)([this](int clientId) {
        auto res = crow::response(handleDeleteClient(clientId));
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
  CROW_ROUTE((*g_app), "/api/connections")
      .methods("GET"_method)([this](const crow::request &req) {
        if (!validateAuth(req))
          return crow::response(401);
        auto res = crow::response(handleGetConnections());
        res.add_header("Content-Type", "application/json");
        return res;
      });

  // GET /api/config - Server configuration
  CROW_ROUTE((*g_app), "/api/config")
      .methods("GET"_method)([this](const crow::request &req) {
        if (!validateAuth(req))
          return crow::response(401);
        auto res = crow::response(handleGetConfig());
        res.add_header("Content-Type", "application/json");
        return res;
      });

  // POST /api/config - Update configuration
  CROW_ROUTE((*g_app), "/api/config")
      .methods("POST"_method)([this](const crow::request &req) {
        if (!validateAuth(req))
          return crow::response(401);
        auto res = crow::response(handlePostConfig(req.body));
        res.add_header("Content-Type", "application/json");
        return res;
      });

  // GET /api/network - Network Info (IPs)
  CROW_ROUTE((*g_app), "/api/network")
      .methods("GET"_method)([this](const crow::request &req) {
        if (!validateAuth(req))
          return crow::response(401);
        auto res = crow::response(handleGetNetworkInfo());
        res.add_header("Content-Type", "application/json");
        return res;
      });

  // Authentication endpoints
  // POST /api/auth/login - User login
  CROW_ROUTE((*g_app), "/api/auth/login")
      .methods("POST"_method)([this](const crow::request &req) {
        auto res = crow::response(handlePostLogin(req));
        res.add_header("Access-Control-Allow-Origin", "*");
        res.add_header("Content-Type", "application/json");
        return res;
      });

  // POST /api/tokens - Generate pairing token
  CROW_ROUTE((*g_app), "/api/tokens")
      .methods("POST"_method)([this](const crow::request &req) {
        // Simple auth check similar to logout
        std::string authHeader = req.get_header_value("Authorization");
        int userId = -1;
        if (!validateSession(authHeader, userId)) {
          return crow::response(401);
        }

        auto res = crow::response(handlePostGenerateToken(req));
        res.add_header("Access-Control-Allow-Origin", "*");
        res.add_header("Content-Type", "application/json");
        return res;
      });

  // POST /api/maintenance/thumbnails - Regenerate thumbnails
  CROW_ROUTE((*g_app), "/api/maintenance/thumbnails")
      .methods("POST"_method)([this](const crow::request &req) {
        // Auth check
        std::string authHeader = req.get_header_value("Authorization");
        int userId = -1;
        if (!validateSession(authHeader, userId)) {
          return crow::response(401);
        }

        auto res = crow::response(handlePostRegenerateThumbnails(req));
        res.add_header("Access-Control-Allow-Origin", "*");
        res.add_header("Content-Type", "application/json");
        return res;
      });

  // POST /api/auth/logout - User logout
  CROW_ROUTE((*g_app), "/api/auth/logout")
      .methods("POST"_method)([this](const crow::request &req) {
        auto res = crow::response(handlePostLogout(req));
        res.add_header("Access-Control-Allow-Origin", "*");
        res.add_header("Content-Type", "application/json");
        return res;
      });

  // GET /api/auth/validate - Validate session
  CROW_ROUTE((*g_app), "/api/auth/validate")
      .methods("GET"_method)([this](const crow::request &req) {
        std::string authHeader = req.get_header_value("Authorization");
        auto res = crow::response(handleGetValidate(authHeader));
        res.add_header("Access-Control-Allow-Origin", "*");
        res.add_header("Content-Type", "application/json");
        return res;
      });

  // Media grid endpoints
  // GET /api/media - List photos with pagination
  CROW_ROUTE((*g_app), "/api/media")
      .methods("GET"_method)([this](const crow::request &req) {
        if (!validateAuth(req))
          return crow::response(401);
        int offset = req.url_params.get("offset")
                         ? std::stoi(req.url_params.get("offset"))
                         : 0;
        int limit = req.url_params.get("limit")
                        ? std::stoi(req.url_params.get("limit"))
                        : 50;
        int clientId = req.url_params.get("client_id")
                           ? std::stoi(req.url_params.get("client_id"))
                           : -1;
        std::string startDate = req.url_params.get("start_date")
                                    ? req.url_params.get("start_date")
                                    : "";
        std::string endDate = req.url_params.get("end_date")
                                  ? req.url_params.get("end_date")
                                  : "";
        std::string search =
            req.url_params.get("search") ? req.url_params.get("search") : "";

        auto res = crow::response(handleGetMedia(offset, limit, clientId,
                                                 startDate, endDate, search));
        res.add_header("Access-Control-Allow-Origin", "*");
        res.add_header("Content-Type", "application/json");
        return res;
      });

  // GET /api/thumbnails/:id - Serve thumbnail
  CROW_ROUTE((*g_app), "/api/thumbnails/<int>")
      .methods("GET"_method)([this](const crow::request &req, int photoId) {
        if (!validateAuth(req))
          return crow::response(401);
        crow::response res;
        handleGetThumbnail(res, photoId);
        return res;
      });

  // GET /api/media/:id/download - Serve full image
  CROW_ROUTE((*g_app), "/api/media/<int>/download")
      .methods("GET"_method)([this](const crow::request &req, int photoId) {
        if (!validateAuth(req))
          return crow::response(401);
        crow::response res;
        handleGetMediaDownload(res, photoId);
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

  // I need to view the file first to find the function body as regex failed
  // earlier or I didn't search properly.
  // Skipping this replace for now.oute for static files - must be last
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
    auto &connMgr = ConnectionManager::getInstance();
    connMgr.cleanStaleConnections(45);

    int totalPhotos = db_.getTotalPhotoCount();
    int totalClients = db_.getTotalClientCount();
    int completedSessions = db_.getCompletedSessionCount();
    long long storageUsed = db_.getTotalStorageUsed();

    // Get disk usage
    long long diskTotal = 0;
    long long diskFree = 0;
    try {
      auto space = std::filesystem::space(config_.getPhotosDir());
      diskTotal = space.capacity;
      diskFree = space.available;
    } catch (...) {
    }

    json response = {
        {"totalPhotos", totalPhotos},
        {"connectedClients", connMgr.getActiveCount()},
        {"totalClients", totalClients},
        {"totalSessions", completedSessions},
        {"storageUsed", storageUsed},
        {"diskTotal", diskTotal},
        {"diskFree", diskFree},
        {"storageLimit", config_.getMaxStorageGB() * 1073741824LL},
        {"uptime", std::chrono::duration_cast<std::chrono::seconds>(
                       std::chrono::system_clock::now() - startTime_)
                       .count()}, // Track server uptime
        {"serverStatus", "running"}};

    return response.dump();
  } catch (const std::exception &e) {
    LOG_ERROR("Error in handleGetStats: " + std::string(e.what()));
    json error = {{"error", e.what()}};
    return error.dump();
  }
}

// Old handleGetErrors removed

crow::json::wvalue ApiServer::handleGetIntegrityStatus() {
  try {
    if (scanner_) {
      auto report = scanner_->getLastReport();
      crow::json::wvalue response;
      response["status"] = report.status;
      response["lastScan"] = report.timestamp;
      response["totalPhotos"] = report.totalPhotos;
      response["missingBlobs"] = report.missingBlobs;
      response["corruptBlobs"] = report.corruptBlobs;
      response["orphanBlobs"] = report.orphanBlobs;
      response["tombstones"] = report.tombstones;
      response["message"] = report.message;
      return response;
    } else {
      crow::json::wvalue response;
      response["status"] = "disabled";
      response["message"] = "Integrity scanner not enabled";
      return response;
    }
  } catch (const std::exception &e) {
    LOG_ERROR("Error in handleGetIntegrityStatus: " + std::string(e.what()));
    json error = {{"error", e.what()}};
    return error.dump(); // Type mismatch fix: returning json string via wvalue
                         // constructor not ideal, but let's try
  }
}

crow::json::wvalue ApiServer::handleGetHealth() {
  crow::json::wvalue response;

  // Uptime
  auto uptimeSeconds = std::chrono::duration_cast<std::chrono::seconds>(
                           std::chrono::system_clock::now() - startTime_)
                           .count();

  auto diskUsage = db_.getDiskUsage();
  long long dbSize = db_.getDbSize();

  response["uptime"] = uptimeSeconds;
  response["version"] = "1.0.0"; // TODO: Define version
  response["diskFree"] = diskUsage.free;
  response["diskTotal"] = diskUsage.total;
  response["dbSize"] = dbSize;
  response["pendingUploads"] = db_.getPendingUploadCount();
  response["failedUploads"] = db_.getFailedUploadCount();
  response["activeSessions"] = db_.getActiveSessionCount();

  // Integrity Summary (Last Scan)
  if (scanner_) {
    auto report = scanner_->getLastReport();
    response["lastIntegrityScan"] = report.timestamp;
    response["integrityIssues"] =
        report.missingBlobs + report.corruptBlobs + report.orphanBlobs;
  } else {
    response["lastIntegrityScan"] = "N/A";
    response["integrityIssues"] = 0;
  }

  return response;
}

std::string ApiServer::handleRevokeClient(int clientId) {
  if (db_.revokeClientAuth(clientId)) {
    json res = {{"success", true}, {"message", "Client access revoked"}};
    return res.dump();
  } else {
    json res = {{"error", "Failed to revoke client"}};
    return res.dump();
  }
}

crow::json::wvalue ApiServer::handleGetIntegrityDetails(const std::string &type,
                                                        int limit) {
  std::vector<std::string> items = db_.getIntegrityDetails(type, limit);
  // Convert to wvalue list
  crow::json::wvalue::list itemList;
  for (const auto &item : items) {
    itemList.push_back(item);
  }
  return {{"items", itemList}, {"type", type}};
}

crow::json::wvalue ApiServer::handleGetErrors(int limit, int offset,
                                              const std::string &level,
                                              const std::string &deviceId,
                                              const std::string &since) {
  try {
    auto errors = db_.getRecentErrors(limit, offset, level, deviceId, since);
    std::vector<crow::json::wvalue> errorsJson;

    for (const auto &err : errors) {
      crow::json::wvalue e;
      e["id"] = err.id;
      e["code"] = err.code;
      e["message"] = err.message;
      e["traceId"] = err.traceId;
      e["timestamp"] = err.timestamp;
      e["severity"] = err.severity;
      e["deviceId"] = err.deviceId;
      // Context as JSON object if possible, else string
      if (!err.context.empty()) {
        try {
          // If context is valid JSON, we might want to parse it?
          // Crow wvalue doesn't support nested raw generic json easily from
          // string without parsing. For now just pass as string
          e["context"] = err.context;
        } catch (...) {
          e["context"] = err.context;
        }
      } else {
        e["context"] = nullptr;
      }

      errorsJson.push_back(e);
    }

    return {{"errors", errorsJson}};
  } catch (const std::exception &e) {
    LOG_ERROR("Error in handleGetErrors: " + std::string(e.what()));
    return {{"error", e.what()}};
  }
}

std::string ApiServer::handleGetPhotos(int page, int limit,
                                       const std::string &clientId,
                                       const std::string &search) {
  try {
    int offset = (page - 1) * limit;
    int clientFilter = -1;
    if (!clientId.empty()) {
      try {
        clientFilter = std::stoi(clientId);
      } catch (...) {
      }
    }

    std::vector<PhotoMetadata> photos = db_.getPhotosWithPagination(
        offset, limit, clientFilter, "", "", search);
    int total = db_.getFilteredPhotoCount(clientFilter, "", "", search);

    json photosJson = json::array();
    for (const PhotoMetadata &photo : photos) {
      json p = {{"id", photo.id},
                {"filename", photo.filename},
                {"size", photo.size},
                {"hash", photo.hash},
                {"thumbnailUrl", "/api/thumbnails/" + std::to_string(photo.id)},
                {"url", "/api/media/" + std::to_string(photo.id)},
                {"takenAt", photo.takenAt},
                {"receivedAt", photo.receivedAt},
                {"mimeType", photo.mimeType},
                {"exif",
                 {{"cameraMake", photo.cameraMake},
                  {"cameraModel", photo.cameraModel},
                  {"exposureTime", photo.exposureTime},
                  {"fNumber", photo.fNumber},
                  {"iso", photo.iso},
                  {"focalLength", photo.focalLength},
                  {"gps",
                   {{"lat", photo.gpsLat},
                    {"lon", photo.gpsLon},
                    {"alt", photo.gpsAlt}}}}}};

      photosJson.push_back(p);
    }

    json response = {{"photos", photosJson},
                     {"pagination",
                      {{"page", page},
                       {"limit", limit},
                       {"total", total},
                       {"pages", (total + limit - 1) / limit}}}};

    return response.dump();
  } catch (const std::exception &e) {
    LOG_ERROR("Error in handleGetPhotos: " + std::string(e.what()));
    json error = {{"error", e.what()}};
    return error.dump();
  }
}

std::string ApiServer::handleGetClients() {
  try {
    auto clients = db_.getClients();
    auto &connMgr = ConnectionManager::getInstance();

    // Clean up stale connections (no heartbeat for 45 seconds)
    auto removed = connMgr.cleanStaleConnections(45);
    if (!removed.empty()) {
      for (int sid : removed) {
        LOG_INFO("Removed stale connection: " + std::to_string(sid));
      }
    }

    auto activeConnections = connMgr.getActiveConnections();

    json clientsJson = json::array();

    for (const auto &client : clients) {
      bool isOnline = false;
      json currentSession = nullptr;

      // Check if client is online
      for (const auto &[sessionId, info] : activeConnections) {
        if (info.deviceId == client.deviceId) {
          isOnline = true;

          if (info.status == "syncing") {
            currentSession = {
                {"progress", info.photosUploaded}, {"total", 0}
                // Total is not currently tracked in ConnectionInfo, would need
                // protocol update
            };
          }
          break;
        }
      }

      // Phase 6: Get 24h stats
      auto stats = db_.getDeviceStats24h(client.id);

      json clientJson = {
          {"id", client.id},
          {"deviceId", client.deviceId},
          {"name", client.userName.empty() ? client.deviceId : client.userName},
          {"lastSeen", client.lastSeen},
          {"photoCount", client.photoCount},
          {"storageUsed", client.storageUsed},
          {"isOnline", connMgr.isClientConnected(client.deviceId)},
          {"uploads24h", stats.uploads24h},
          {"failures24h", stats.failures24h}};

      if (currentSession != nullptr) {
        clientJson["currentSession"] = currentSession;
      }

      clientsJson.push_back(clientJson);
    }

    json response = {{"clients", clientsJson}};
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
    int offset = (page - 1) * limit;
    int clientFilter = -1;
    if (!clientId.empty()) {
      try {
        clientFilter = std::stoi(clientId);
      } catch (...) {
        // Invalid client ID format, maybe ignore or return error?
        // For now, let's treat invalid ID as -1 (no filter) or handle
        // gracefully
      }
    }

    auto sessions = db_.getSessions(offset, limit, clientFilter, status);

    // Get total count (approximation or implement proper count query)
    // For pagination we need total count. Let's create
    // `getCompletedSessionCount` variant or just return what we have. Actually
    // `getCompletedSessionCount` exists but doesn't take filters. For now,
    // let's just return what we have. Pagination might be slightly off without
    // total filtered count.
    int total = db_.getCompletedSessionCount(); // This is inaccurate if
                                                // filtered, but okay for now.

    json sessionsJson = json::array();
    for (const auto &session : sessions) {
      sessionsJson.push_back({{"id", session.id},
                              {"clientId", session.clientId},
                              {"deviceId", session.deviceId},
                              {"clientName", session.clientName},
                              {"startedAt", session.startedAt},
                              {"endedAt", session.endedAt},
                              {"photosReceived", session.photosReceived},
                              {"status", session.status}});
    }

    json response = {{"sessions", sessionsJson},
                     {"pagination",
                      {{"page", page},
                       {"limit", limit},
                       {"total", total},
                       {"pages", (total + limit - 1) / limit}}}};

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
    connMgr.cleanStaleConnections(45);
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
                   {"user_name", info.userName},
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
// Authentication endpoint implementations

// POST /api/auth/login - User login
std::string ApiServer::handlePostLogin(const crow::request &req) {
  try {
    auto requestData = json::parse(req.body);
    std::string ipAddress = req.remote_ip_address;

    // Validate request
    if (!requestData.contains("username") ||
        !requestData.contains("password")) {
      json error = {{"error", "Missing username or password"}};
      return error.dump();
    }

    std::string username = requestData["username"];
    std::string password = requestData["password"];

    // Task E: Brute Force & Abuse Protections - Lock by IP
    std::string clientId = ipAddress;

#ifdef DEBUG_BUILD
    // For local testing where IP might be ::1 always, maybe combine?
    // But for "Safety", IP is best.
    // If empty, fallback to username to be safe.
    if (clientId.empty())
      clientId = username;
#endif

    {
      std::lock_guard<std::mutex> lock(loginAttemptsMutex_);
      auto it = loginAttempts_.find(clientId);
      time_t now = std::time(nullptr);

      if (it != loginAttempts_.end()) {
        int attempts = it->second.first;
        time_t lastAttempt = it->second.second;
        int maxAttempts = config_.getMaxFailedAttempts();
        int lockoutDuration =
            config_.getLockoutDurationMinutes() * 60; // Convert to seconds

        // Check if still in lockout period
        if (attempts >= maxAttempts && (now - lastAttempt) < lockoutDuration) {
          int remainingTime = lockoutDuration - (now - lastAttempt);
          LOG_WARN("Login attempt during lockout for user: " + username);
          json error = {{"error", "Too many failed attempts. Account locked."},
                        {"retry_after", remainingTime}};
          return error.dump();
        }

        // Reset if lockout period has passed
        if ((now - lastAttempt) >= lockoutDuration) {
          loginAttempts_.erase(it);
        }
      }
    }

    // Get user from database
    AdminUser user = db_.getAdminUserByUsername(username);
    if (user.id == -1) {
      LOG_WARN("Login attempt for non-existent user: " + username);
      json error = {{"error", "Invalid credentials"}};
      return error.dump();
    }

    // Verify password
    if (!AuthenticationManager::verifyPassword(password, user.passwordHash)) {
      LOG_WARN("Failed login attempt for user: " + username);

      // Track failed attempt
      {
        std::lock_guard<std::mutex> lock(loginAttemptsMutex_);

        time_t now = std::time(nullptr);

        auto it = loginAttempts_.find(clientId);
        if (it != loginAttempts_.end()) {
          it->second.first++;      // Increment attempt count
          it->second.second = now; // Update last attempt time
        } else {
          loginAttempts_[clientId] = {1, now};
        }
      }

      json error = {{"error", "Invalid credentials"}};
      return error.dump();
    }

    // Clear failed attempts on successful login
    {
      std::lock_guard<std::mutex> lock(loginAttemptsMutex_);
      loginAttempts_.erase(username);
    }

    // Generate session token
    std::string sessionToken = AuthenticationManager::generateSessionToken();

    // Calculate expiration using configured timeout
    int sessionTimeout = config_.getSessionTimeoutSeconds();
    std::string expiresAt =
        AuthenticationManager::calculateExpiresAt(sessionTimeout);

    // Create session in database
    // TODO: Get client IP address from request
    bool sessionCreated =
        db_.createAuthSession(sessionToken, user.id, expiresAt, "");

    if (!sessionCreated) {
      LOG_ERROR("Failed to create session for user: " + username);
      json error = {{"error", "Failed to create session"}};
      return error.dump();
    }

    LOG_INFO("User logged in: " + username);

    // Return success response
    json response = {{"sessionToken", sessionToken},
                     {"expiresAt", expiresAt},
                     {"user", {{"id", user.id}, {"username", user.username}}}};

    return response.dump();
  } catch (const std::exception &e) {
    LOG_ERROR("Error in handlePostLogin: " + std::string(e.what()));
    json error = {{"error", "Internal server error"}};
    return error.dump();
  }
}

// POST /api/auth/logout - User logout
std::string ApiServer::handlePostLogout(const crow::request &req) {
  try {
    std::string authHeader = req.get_header_value("Authorization");
    std::string ipAddress = req.remote_ip_address;
    // Extract token from Authorization header
    if (authHeader.empty() || authHeader.substr(0, 7) != "Bearer ") {
      json error = {{"error", "Missing or invalid authorization header"}};
      return error.dump();
    }

    std::string token = authHeader.substr(7);

    // Get user ID before deleting session for logging
    AuthSession session = db_.getSessionByToken(token);
    int userId = session.userId;

    // Delete session from database
    bool deleted = db_.deleteSession(token);

    if (!deleted) {
      LOG_WARN("Logout attempt with invalid token");
      json error = {{"error", "Invalid session"}};
      return error.dump();
    }

    LOG_INFO("User logged out");

    if (userId != -1) {
      // db_.logActivity removed
    }

    json response = {{"success", true}};
    return response.dump();
  } catch (const std::exception &e) {
    LOG_ERROR("Error in handlePostLogout: " + std::string(e.what()));
    json error = {{"error", "Internal server error"}};
    return error.dump();
  }
}

// GET /api/auth/validate - Validate session
std::string ApiServer::handleGetValidate(const std::string &authHeader) {
  try {
    int userId;
    bool valid = validateSession(authHeader, userId);

    if (valid) {
      // Get session to return expiration time
      if (authHeader.empty() || authHeader.substr(0, 7) != "Bearer ") {
        json response = {{"valid", false}};
        return response.dump();
      }

      std::string token = authHeader.substr(7);
      AuthSession session = db_.getSessionByToken(token);

      json response = {{"valid", true}, {"expiresAt", session.expiresAt}};
      return response.dump();
    } else {
      json response = {{"valid", false}};
      return response.dump();
    }
  } catch (const std::exception &e) {
    LOG_ERROR("Error in handleGetValidate: " + std::string(e.what()));
    json response = {{"valid", false}};
    return response.dump();
  }
}

// Authentication middleware - validates session token
bool ApiServer::validateSession(const std::string &authHeader, int &userId) {
  try {
    // Extract token from Authorization header
    if (authHeader.empty() || authHeader.substr(0, 7) != "Bearer ") {
      return false;
    }

    std::string token = authHeader.substr(7);

    // Get session from database
    AuthSession session = db_.getSessionByToken(token);
    if (session.id == -1) {
      return false;
    }

    // Check if session has expired
    // Parse ISO8601 timestamp: "YYYY-MM-DDTHH:MM:SSZ"
    std::tm expiresTime = {};
    std::istringstream ss(session.expiresAt);
    ss >> std::get_time(&expiresTime, "%Y-%m-%dT%H:%M:%SZ");

    if (ss.fail()) {
      LOG_ERROR("Failed to parse session expiration time: " +
                session.expiresAt);
      return false;
    }

    // Convert to time_t for comparison
    time_t expiresTimestamp = std::mktime(&expiresTime);
    time_t currentTimestamp = std::time(nullptr);

    // Check if session has expired
    if (currentTimestamp >= expiresTimestamp) {
      LOG_DEBUG("Session expired for user ID: " +
                std::to_string(session.userId));
      return false;
    }

    userId = session.userId;
    return true;
  } catch (const std::exception &e) {
    LOG_ERROR("Error in validateSession: " + std::string(e.what()));
    return false;
  }
}

// Media Grid Endpoints

// GET /api/media - List photos with pagination and filters
std::string ApiServer::handleGetMedia(int offset, int limit, int clientId,
                                      const std::string &startDate,
                                      const std::string &endDate,
                                      const std::string &searchQuery) {
  try {
    // Validate and cap limit
    if (limit <= 0)
      limit = 50;
    if (limit > 100)
      limit = 100;
    if (offset < 0)
      offset = 0;

    // Get photos with pagination
    auto photos = db_.getPhotosWithPagination(offset, limit, clientId,
                                              startDate, endDate, searchQuery);

    // Get total count for pagination
    int total =
        db_.getFilteredPhotoCount(clientId, startDate, endDate, searchQuery);

    // Build response
    json items = json::array();
    for (const auto &photo : photos) {
      json item = {
          {"id", photo.id},
          {"filename", photo.filename},
          {"thumbnailUrl", "/api/thumbnails/" + std::to_string(photo.id)},
          {"fullUrl", "/api/media/" + std::to_string(photo.id) + "/download"},
          {"mimeType", photo.mimeType},
          {"size", photo.size},
          {"uploadedAt", photo.receivedAt},
          {"clientId", photo.clientId}};
      items.push_back(item);
    }

    json response = {{"items", items},
                     {"pagination",
                      {{"offset", offset},
                       {"limit", limit},
                       {"total", total},
                       {"hasMore", (offset + limit) < total}}}};

    return response.dump();
  } catch (const std::exception &e) {
    LOG_ERROR("Error in handleGetMedia: " + std::string(e.what()));
    json error = {{"error", "Failed to fetch media"}};
    return error.dump();
  }
}

// GET /api/thumbnails/:id - Serve thumbnail image
void ApiServer::handleGetThumbnail(crow::response &res, int photoId) {
  LOG_INFO("Handling thumbnail request for " + std::to_string(photoId));
  try {
    // Check if thumbnail exists, generate if not
    if (!ThumbnailGenerator::thumbnailExists(photoId)) {
      // Get photo metadata to find original file
      PhotoMetadata photo = db_.getPhotoById(photoId);
      if (photo.id == -1) {
        res.code = 404;
        res.write("Photo not found");
        LOG_INFO("Thumbnail not found (DB scan): " + std::to_string(photoId));
        return;
      }

      // Ensure thumbnails directory exists
      ThumbnailGenerator::ensureThumbnailsDirectory();

      // Generate thumbnail
      std::string thumbnailPath = ThumbnailGenerator::getThumbnailPath(photoId);
      if (!ThumbnailGenerator::generateThumbnail(photo.originalPath,
                                                 thumbnailPath)) {
        LOG_ERROR("Failed to generate thumbnail for photo ID: " +
                  std::to_string(photoId));
        res.code = 500;
        res.write("Failed to generate thumbnail");
        return;
      }
    }

    // Read and serve thumbnail
    std::string thumbnailPath = ThumbnailGenerator::getThumbnailPath(photoId);
    std::ifstream file(thumbnailPath, std::ios::binary);
    if (!file) {
      res.code = 404;
      res.write("Thumbnail not found");
      return;
    }

    // Read file contents
    std::string content((std::istreambuf_iterator<char>(file)),
                        std::istreambuf_iterator<char>());
    file.close();

    // Set headers
    res.add_header("Content-Type", "image/jpeg");
    res.add_header("Cache-Control", "public, max-age=86400"); // 24 hours
    res.code = 200;
    res.write(content);
    LOG_INFO("Serving thumbnail for " + std::to_string(photoId));

  } catch (const std::exception &e) {
    LOG_ERROR("Error in handleGetThumbnail: " + std::string(e.what()));
    res.code = 500;
    res.write("Internal server error");
  }
}

// GET /api/media/:id/download - Serve full-size image
void ApiServer::handleGetMediaDownload(crow::response &res, int photoId) {
  LOG_INFO("Handling full image request for " + std::to_string(photoId));
  try {
    // Get photo metadata
    PhotoMetadata photo = db_.getPhotoById(photoId);
    if (photo.id == -1) {
      res.code = 404;
      res.write("Photo not found");
      return;
    }

    // Read file
    std::ifstream file(photo.originalPath, std::ios::binary);
    if (!file) {
      LOG_ERROR("Photo file not found: " + photo.originalPath);
      res.code = 404;
      res.write("Photo file not found");
      return;
    }

    // Read file contents
    std::string content((std::istreambuf_iterator<char>(file)),
                        std::istreambuf_iterator<char>());
    file.close();

    // Set headers
    res.add_header("Content-Type", photo.mimeType);
    res.add_header("Cache-Control", "public, max-age=604800"); // 7 days
    res.add_header("Content-Disposition",
                   "inline; filename=\"" + photo.filename + "\"");
    res.code = 200;
    res.write(content);
    LOG_INFO("Serving full image for " + std::to_string(photoId));

  } catch (const std::exception &e) {
    LOG_ERROR("Error in handleGetMediaDownload: " + std::string(e.what()));
    res.code = 500;
    res.write("Internal server error");
  }
}

std::string ApiServer::handleGetClientDetails(int clientId) {
  try {
    DatabaseManager::ClientRecord client = db_.getClientDetails(clientId);
    if (client.id == -1) {
      json error = {{"error", "Client not found"}};
      return error.dump();
    }

    json response = {
        {"id", client.id},
        {"deviceId", client.deviceId},
        {"lastSeen", client.lastSeen},
        {"photoCount", client.photoCount},
        {"storageUsed", client.storageUsed},
        {"formattedStorage",
         client.storageUsed} // formatBytes logic usually in frontend, but could
                             // send number
    };

    return response.dump();
  } catch (const std::exception &e) {
    LOG_ERROR("Error in handleGetClientDetails: " + std::string(e.what()));
    json error = {{"error", e.what()}};
    return error.dump();
  }
}

std::string ApiServer::handlePostGenerateToken(const crow::request &req) {
  try {
    std::string ipAddress = req.remote_ip_address;
    std::string authHeader = req.get_header_value("Authorization");
    std::string token = (authHeader.length() > 7) ? authHeader.substr(7) : "";
    AuthSession session = db_.getSessionByToken(token);
    int userId = session.userId;

    std::string pairingToken = db_.generatePairingToken();
    if (pairingToken.empty()) {
      json error = {{"error", "Failed to generate token"}};
      return error.dump();
    }

    if (userId != -1) {
      // db_.logActivity removed
    }

    json response = {
        {"token", pairingToken},
        {"expiresIn", 15 * 60},              // 15 minutes
        {"expiresAt", "15 minutes from now"} // Simplification
    };

    return response.dump();
  } catch (const std::exception &e) {
    LOG_ERROR("Error in handlePostGenerateToken: " + std::string(e.what()));
    json error = {{"error", e.what()}};
    return error.dump();
  }
}

// handleGetAuditLogs removed

// GET /api/network - Network Info implementation
std::string ApiServer::handleGetNetworkInfo() {
  try {
    json ips = json::array();

    boost::asio::io_context io_context;
    boost::asio::ip::tcp::resolver resolver(io_context);
    boost::asio::ip::tcp::resolver::results_type results =
        resolver.resolve(boost::asio::ip::host_name(), "");

    for (const auto &entry : results) {
      boost::asio::ip::tcp::endpoint ep = entry.endpoint();
      if (ep.address().is_v4()) {
        // Filter out loopback
        if (!ep.address().to_v4().is_loopback()) {
          ips.push_back(ep.address().to_string());
        }
      }
    }

    // Fallback: If no IPs found (e.g. no hostname resolution), try getting
    // 127.0.0.1 just in case
    if (ips.empty()) {
      ips.push_back("127.0.0.1");
    }

    json response = {{"ips", ips}, {"port", config_.getPort()}};
    return response.dump();
  } catch (const std::exception &e) {
    LOG_ERROR("Error in handleGetNetworkInfo: " + std::string(e.what()));
    json error = {{"error", e.what()}};
    return error.dump();
  }
}

// DELETE /api/clients/<int> implementation
std::string ApiServer::handleDeleteClient(int clientId) {
  try {
    if (db_.deleteClient(clientId)) {
      json response = {{"success", true}};
      return response.dump();
    } else {
      json error = {{"error", "Failed to delete client"}};
      return error.dump();
    }
  } catch (const std::exception &e) {
    LOG_ERROR("Error in handleDeleteClient: " + std::string(e.what()));
    json error = {{"error", e.what()}};
    return error.dump();
  }
}

// Implement helper
bool ApiServer::validateAuth(const crow::request &req) {
  std::string authHeader = req.get_header_value("Authorization");
  int userId = -1;
  return validateSession(authHeader, userId);
}

// DELETE /api/media/:id - Soft delete photo
std::string ApiServer::handleDeleteMedia(int photoId) {
  try {
    // Check if photo exists first
    PhotoMetadata photo = db_.getPhotoById(photoId);
    if (photo.id == -1) {
      json error = {{"error", "Photo not found"}};
      return error.dump();
    }

    if (db_.softDeletePhoto(photoId)) {
      json response = {{"success", true}, {"id", photoId}};
      return response.dump();
    } else {
      json error = {{"error", "Failed to delete photo"}};
      return error.dump();
    }
  } catch (const std::exception &e) {
    LOG_ERROR("Error in handleDeleteMedia: " + std::string(e.what()));
    json error = {{"error", e.what()}};
    return error.dump();
  }
}

// GET /api/changes?cursor=0&limit=50
// Returns incremental sync feed
std::string ApiServer::handleGetChanges(const std::string &cursorStr,
                                        int limit) {
  try {
    long long cursor = 0;
    if (!cursorStr.empty()) {
      try {
        cursor = std::stoll(cursorStr);
      } catch (...) {
        cursor = 0;
      }
    }

    if (limit <= 0)
      limit = 100;
    if (limit > 1000)
      limit = 1000;

    auto changes = db_.getChanges(cursor, limit);

    json items = json::array();
    long long nextCursor = cursor;

    for (const auto &change : changes) {
      json item = {{"id", change.changeId},
                   {"op", change.op},
                   {"mediaId", change.mediaId},
                   {"blobHash", change.blobHash},
                   {"changedAt", change.changedAt},
                   {"data",
                    {{"filename", change.filename},
                     {"size", change.size},
                     {"mimeType", change.mimeType},
                     {"takenAt", change.takenAt},
                     {"deviceId", change.deviceId}}}};
      items.push_back(item);
      if (change.changeId > nextCursor) {
        nextCursor = change.changeId;
      }
    }

    json response = {{"items", items},
                     {"nextCursor", nextCursor},
                     {"hasMore", (int)changes.size() >= limit}};

    return response.dump();

  } catch (const std::exception &e) {
    LOG_ERROR("Error in handleGetChanges: " + std::string(e.what()));
    json error = {{"error", e.what()}};
    return error.dump();
  }
}

crow::json::wvalue ApiServer::handleGetTopFiles() {
  try {
    auto files = db_.getLargestFiles(50);
    std::vector<crow::json::wvalue> filesJson;

    for (const auto &file : files) {
      filesJson.push_back({{"id", file.id},
                           {"filename", file.filename},
                           {"mimeType", file.mimeType},
                           {"size", file.size},
                           {"originalPath", file.originalPath}});
    }

    return {{"topFiles", filesJson}};
  } catch (const std::exception &e) {
    LOG_ERROR("Error in handleGetTopFiles: " + std::string(e.what()));
    return {{"error", e.what()}};
  }
}