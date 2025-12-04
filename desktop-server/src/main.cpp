#include "ApiServer.h"
#include "ConfigManager.h"
#include "DatabaseManager.h"
#include "FileManager.h"
#include "Logger.h"
#include "TcpListener.h"
#include <boost/asio.hpp>
#include <csignal>
#include <iostream>

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
    TcpListener listener(io_context, config.getPort(), db, fileManager);

    LOG_INFO("Sync server ready on port " + std::to_string(config.getPort()));
    LOG_INFO("API server ready on port 50506");
    LOG_INFO("Photo transfer enabled with SHA-256 verification");
    LOG_INFO("Press Ctrl+C to shutdown");

    // Run the IO context
    io_context.run();

    LOG_INFO("Server stopped");

    // Stop API server
    apiServer.stop();

  } catch (std::exception &e) {
    LOG_FATAL("Exception: " + std::string(e.what()));
    if (g_apiServer) {
      g_apiServer->stop();
    }
    return 1;
  }

  db.close();
  LOG_INFO("=== PhotoSync Server Shutdown Complete ===");

  return 0;
}
