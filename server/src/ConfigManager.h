#pragma once

#include <map>
#include <string>

class ConfigManager {
public:
  static ConfigManager &getInstance();

  bool loadFromFile(const std::string &filename);

  // Getters
  int getPort() const;
  int getMaxConnections() const;
  int getTimeoutSeconds() const;
  std::string getPhotosDir() const;
  std::string getTempDir() const;
  int getMaxStorageGB() const;
  std::string getDbPath() const;
  std::string getLogLevel() const;
  std::string getLogFile() const;
  std::string getServerName() const;
  bool getConsoleOutput() const;

  // Authentication settings
  int getSessionTimeoutSeconds() const;
  int getBcryptCost() const;
  int getMaxFailedAttempts() const;
  int getLockoutDurationMinutes() const;

  // Maintenance
  int getCleanupIntervalSeconds() const;

  // Phase 3: Integrity & Retention
  int getIntegrityScanInterval() const;
  bool getIntegrityVerifyHash() const;
  int getIntegrityMissingCheckInterval() const;
  int getIntegrityOrphanSampleInterval() const;
  int getIntegrityFullScanInterval() const;
  int getIntegrityOrphanSampleSize() const;
  int getDeletedRetentionDays() const;

private:
  ConfigManager() = default;
  ConfigManager(const ConfigManager &) = delete;
  ConfigManager &operator=(const ConfigManager &) = delete;

  std::string trim(const std::string &str);
  void parseLine(const std::string &line, std::string &currentSection);

  std::map<std::string, std::string> config_;

  // Default values
  const int DEFAULT_PORT = 50505;
  const int DEFAULT_MAX_CONNECTIONS = 10;
  const int DEFAULT_TIMEOUT = 300;
  const std::string DEFAULT_PHOTOS_DIR = "./storage/photos";
  const std::string DEFAULT_TEMP_DIR = "./storage/temp";
  const int DEFAULT_MAX_STORAGE_GB = 100;
  const std::string DEFAULT_DB_PATH = "./photosync.db";
  const std::string DEFAULT_LOG_LEVEL = "INFO";
  const std::string DEFAULT_LOG_FILE = "./server.log";
  const bool DEFAULT_CONSOLE_OUTPUT = true;
  const std::string DEFAULT_SERVER_NAME = "PhotoSync Server";

  // Authentication defaults
  const int DEFAULT_SESSION_TIMEOUT = 3600; // 1 hour
  const int DEFAULT_BCRYPT_COST = 12;       // 4096 iterations
  const int DEFAULT_MAX_FAILED_ATTEMPTS = 5;
  const int DEFAULT_LOCKOUT_DURATION = 15; // minutes
};
