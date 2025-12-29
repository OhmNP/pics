#include "ApiServer.h"
#include "ConfigManager.h"
#include "DatabaseManager.h"
#include "FileManager.h"
#include "IntegrityScanner.h"
#include "Logger.h"
#include "TcpListener.h"
#include "UdpBroadcaster.h"
#include <atomic>
#include <boost/asio.hpp>
#include <boost/asio/ssl.hpp>
#include <csignal>
#include <iostream>
#include <thread>

// Undefine Windows macros that conflict with our enums
#ifdef ERROR
#undef ERROR
#endif

boost::asio::io_context *g_io_context = nullptr;
ApiServer *g_apiServer = nullptr;

void signalHandler(int signal) {
  if (signal == SIGINT || signal == SIGTERM) {
    LOG_INFO("Shutdown signal received");
    if (g_apiServer) {
      g_apiServer->stop();
    }
    if (g_io_context) {
      g_io_context->stop();
    }
  }
}

int main(int argc, char *argv[]) {
  std::string configFile = "server.conf";

  // Parse command line arguments
  if (argc > 1) {
    configFile = argv[1];
  }

  // Initialize Config
  ConfigManager &config = ConfigManager::getInstance();

  // Initialize SSL Context
  boost::asio::ssl::context ssl_context(boost::asio::ssl::context::tlsv12);
  try {
    ssl_context.set_options(boost::asio::ssl::context::default_workarounds |
                            boost::asio::ssl::context::no_sslv2 |
                            boost::asio::ssl::context::single_dh_use);
    ssl_context.use_certificate_chain_file("server.crt");
    ssl_context.use_private_key_file("server.key",
                                     boost::asio::ssl::context::pem);
  } catch (const std::exception &e) {
    LOG_ERROR("Failed to load SSL certificates: " + std::string(e.what()));
    std::cerr << "CRITICAL: Failed to load SSL certificates (server.crt, "
                 "server.key). "
                 "Run generate_cert.py first."
              << std::endl;
    return 1;
  }

  // Load configuration
  if (!config.loadFromFile(configFile)) {
    std::cerr << "Warning: Could not load config file '" << configFile
              << "', using defaults" << std::endl;
  }

  // Initialize logger
  LogLevel logLevel = LogLevel::L_INFO;
  std::string logLevelStr = config.getLogLevel();
  if (logLevelStr == "DEBUG")
    logLevel = LogLevel::L_DEBUG;
  else if (logLevelStr == "WARN")
    logLevel = LogLevel::L_WARN;
  else if (logLevelStr == "ERROR")
    logLevel = LogLevel::L_ERROR;

  Logger::getInstance().init(config.getLogFile(), logLevel,
                             config.getConsoleOutput());

  LOG_INFO("=== PhotoSync Server Starting ===");
  LOG_INFO("Configuration loaded from: " + configFile);

  // Initialize database
  DatabaseManager db;
  if (!db.open(config.getDbPath())) {
    LOG_FATAL("Failed to open database");
    return 1;
  }

  if (!db.createSchema()) {
    LOG_FATAL("Failed to create database schema");
    return 1;
  }

  // Setup signal handlers
  std::signal(SIGINT, signalHandler);
  std::signal(SIGTERM, signalHandler);

  try {
    // Initialize file storage for photo transfer
    long long maxStorageBytes = config.getMaxStorageGB() * 1073741824LL;
    FileManager fileManager(config.getPhotosDir(), maxStorageBytes);
    if (!fileManager.initialize()) {
      LOG_FATAL("Failed to initialize file storage");
      return 1;
    }
    LOG_INFO("File storage initialized with " +
             std::to_string(config.getMaxStorageGB()) + " GB quota");

    // Initialize Integrity Scanner (Phase 3) - Created early for API access
    IntegrityScanner integrityScanner(db, fileManager);

    // Start REST API server for UI
    ApiServer apiServer(db, config, &integrityScanner);
    g_apiServer = &apiServer;
    apiServer.start(50506); // API on port 50506

    // Create IO context
    boost::asio::io_context io_context;
    g_io_context = &io_context;

    // Start TCP Listener (Sync Protocol)
    LOG_INFO("Initializing TCP Listener...");
    try {
      int tcpPort = config.getPort(); // Default 50505
      TcpListener tcpListener(io_context, ssl_context, tcpPort, db,
                              fileManager);
      LOG_INFO("TCP Sync Server listening on port " + std::to_string(tcpPort));

      // Start UDP Broadcaster for service discovery
      UdpBroadcaster broadcaster(io_context, config.getPort());
      broadcaster.start();

      LOG_INFO("Sync server ready on port " + std::to_string(config.getPort()));
      LOG_INFO("API server ready on port 50506");
      LOG_INFO("Photo transfer enabled with SHA-256 verification");
      LOG_INFO("Press Ctrl+C to shutdown");

      LOG_INFO("Press Ctrl+C to shutdown");

      // Start Integrity Scanner
      // IntegrityScanner already initialized above
      IntegrityScanner::Config integrityConfig;
      integrityConfig.scanIntervalSeconds = config.getIntegrityScanInterval();
      integrityConfig.verifyHash = config.getIntegrityVerifyHash();
      integrityConfig.missingCheckInterval =
          config.getIntegrityMissingCheckInterval();
      integrityConfig.orphanSampleInterval =
          config.getIntegrityOrphanSampleInterval();
      integrityConfig.fullScanInterval = config.getIntegrityFullScanInterval();
      integrityConfig.orphanSampleSize = config.getIntegrityOrphanSampleSize();
      integrityScanner.start(integrityConfig);

      // Start background session cleanup thread
      std::atomic<bool> cleanupRunning(true);
      std::thread cleanupThread([&db, &fileManager, &cleanupRunning,
                                 &config]() {
        int interval = config.getCleanupIntervalSeconds();
        while (cleanupRunning) {
          // Sleep in chunks to allow fast stop
          for (int i = 0; i < interval && cleanupRunning; ++i) {
            std::this_thread::sleep_for(std::chrono::seconds(1));
          }
          if (!cleanupRunning)
            break;

          try {
            // 1. Auth Sessions
            int cleaned = db.cleanupExpiredSessions();
            if (cleaned > 0) {
              LOG_INFO("Cleaned up " + std::to_string(cleaned) +
                       " expired sessions");
            }

            // 2. Upload Sessions & Temp Files
            std::vector<std::string> expiredUploads =
                db.getExpiredUploadSessionIds();
            for (const auto &uploadId : expiredUploads) {
              // Delete temp file
              std::string tempPath = fileManager.getUploadTempPath(uploadId);
              // We don't have direct check logic in FileManager exposed simply,
              // but we can use filesystem directly or add deleteTempFile.
              // FileManager has deletePhoto(hash) but not deleteTemp.
              // We'll use std::filesystem here as main.cpp includes
              // <filesystem> indirectly? No, it doesn't. BUT FileManager has
              // internal logic. I should add deleteTempFile to FileManager
              // public API for cleanliness. For now, I'll assume I can just
              // invoke `remove` if I include filesystem. Wait, main.cpp:10
              // includes boost/asio... Not standard filesystem. I will add
              // `fileManager.deleteUploadTemp(uploadId)` to FileManager. And
              // calling it here.
              fileManager.deleteUploadSessionFiles(uploadId);
              db.deleteUploadSession(uploadId);
            }
            if (!expiredUploads.empty()) {
              LOG_INFO("Cleaned up " + std::to_string(expiredUploads.size()) +
                       " expired upload sessions");
            }

            // 3. Purge Soft Deleted Photos
            int retentionDays = config.getDeletedRetentionDays();
            int purged = db.purgeDeletedPhotos(retentionDays);
            if (purged > 0) {
              LOG_INFO("Purged " + std::to_string(purged) +
                       " soft-deleted photos older than " +
                       std::to_string(retentionDays) + " days");
            }

          } catch (const std::exception &e) {
            LOG_ERROR("Session cleanup error: " + std::string(e.what()));
          }
        }
      });

      // Run the IO context
      io_context.run();

      // Stop cleanup thread
      cleanupRunning = false;
      if (cleanupThread.joinable()) {
        cleanupThread.join();
      }

      integrityScanner.stop();

    } catch (const std::exception &e) {
      LOG_FATAL("Failed to start or run server components: " +
                std::string(e.what()));
      throw; // Re-throw to be caught by outer handler
    }

    LOG_INFO("Server stopped");

    // Stop API server
    apiServer.stop();

  } catch (const std::exception &e) {
    LOG_FATAL("Exception: " + std::string(e.what()));
    if (g_apiServer) {
      g_apiServer->stop();
    }
    return 1;
  } catch (...) {
    LOG_FATAL("Unknown exception occurred");
    if (g_apiServer) {
      g_apiServer->stop();
    }
    return 1;
  }

  db.close();
  LOG_INFO("=== PhotoSync Server Shutdown Complete ===");

  return 0;
}
