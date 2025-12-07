#include "ApiServer.h"
#include "ConfigManager.h"
#include "DatabaseManager.h"
#include "FileManager.h"
#include "Logger.h"
#include "TcpListener.h"
#include "UdpBroadcaster.h"
#include <atomic>
#include <boost/asio.hpp>
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

  // Load configuration
  ConfigManager &config = ConfigManager::getInstance();
  if (!config.loadFromFile(configFile)) {
    std::cerr << "Warning: Could not load config file '" << configFile
              << "', using defaults" << std::endl;
  }

  // Initialize logger
  LogLevel logLevel = LogLevel::INFO;
  std::string logLevelStr = config.getLogLevel();
  if (logLevelStr == "DEBUG")
    logLevel = LogLevel::DEBUG;
  else if (logLevelStr == "WARN")
    logLevel = LogLevel::WARN;
  else if (logLevelStr == "ERROR")
    logLevel = LogLevel::ERROR;

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

    // Start REST API server for UI
    ApiServer apiServer(db, config);
    g_apiServer = &apiServer;
    apiServer.start(50506); // API on port 50506

    // Create IO context
    boost::asio::io_context io_context;
    g_io_context = &io_context;

    // Start TCP listener for sync protocol with FileManager
    LOG_INFO("Initializing TCP Listener...");
    try {
      TcpListener listener(io_context, config.getPort(), db, fileManager);
      LOG_INFO("TCP Listener initialized.");

      // Start UDP Broadcaster for service discovery
      UdpBroadcaster broadcaster(io_context, config.getPort());
      broadcaster.start();

      LOG_INFO("Sync server ready on port " + std::to_string(config.getPort()));
      LOG_INFO("API server ready on port 50506");
      LOG_INFO("Photo transfer enabled with SHA-256 verification");
      LOG_INFO("Press Ctrl+C to shutdown");

      // Start background session cleanup thread
      std::atomic<bool> cleanupRunning(true);
      std::thread cleanupThread([&db, &cleanupRunning]() {
        while (cleanupRunning) {
          std::this_thread::sleep_for(std::chrono::minutes(5));
          if (!cleanupRunning)
            break;

          try {
            int cleaned = db.cleanupExpiredSessions();
            if (cleaned > 0) {
              LOG_INFO("Cleaned up " + std::to_string(cleaned) +
                       " expired sessions");
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
