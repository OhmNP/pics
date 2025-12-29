#include "ConfigManager.h"
#include "Logger.h"
#include <algorithm>
#include <fstream>
#include <sstream>

ConfigManager &ConfigManager::getInstance() {
  static ConfigManager instance;
  return instance;
}

bool ConfigManager::loadFromFile(const std::string &filename) {
  std::ifstream file(filename);
  if (!file.is_open()) {
    return false;
  }

  std::string line;
  std::string currentSection;

  while (std::getline(file, line)) {
    parseLine(line, currentSection);
  }

  file.close();
  return true;
}

void ConfigManager::parseLine(const std::string &line,
                              std::string &currentSection) {
  std::string trimmedLine = trim(line);

  // Skip empty lines and comments
  if (trimmedLine.empty() || trimmedLine[0] == '#' || trimmedLine[0] == ';') {
    return;
  }

  // Check for section header
  if (trimmedLine[0] == '[' && trimmedLine.back() == ']') {
    currentSection = trimmedLine.substr(1, trimmedLine.length() - 2);
    return;
  }

  // Parse key-value pair
  size_t pos = trimmedLine.find('=');
  if (pos != std::string::npos) {
    std::string key = trim(trimmedLine.substr(0, pos));
    std::string value = trim(trimmedLine.substr(pos + 1));

    if (!currentSection.empty()) {
      key = currentSection + "." + key;
    }

    config_[key] = value;
  }
}

std::string ConfigManager::trim(const std::string &str) {
  size_t first = str.find_first_not_of(" \t\r\n");
  if (first == std::string::npos) {
    return "";
  }
  size_t last = str.find_last_not_of(" \t\r\n");
  return str.substr(first, last - first + 1);
}

int ConfigManager::getPort() const {
  auto it = config_.find("network.port");
  return (it != config_.end()) ? std::stoi(it->second) : DEFAULT_PORT;
}

int ConfigManager::getMaxConnections() const {
  auto it = config_.find("network.max_connections");
  return (it != config_.end()) ? std::stoi(it->second)
                               : DEFAULT_MAX_CONNECTIONS;
}

int ConfigManager::getTimeoutSeconds() const {
  auto it = config_.find("network.timeout_seconds");
  return (it != config_.end()) ? std::stoi(it->second) : DEFAULT_TIMEOUT;
}

std::string ConfigManager::getPhotosDir() const {
  auto it = config_.find("storage.photos_dir");
  return (it != config_.end()) ? it->second : DEFAULT_PHOTOS_DIR;
}

std::string ConfigManager::getTempDir() const {
  auto it = config_.find("storage.temp_dir");
  return (it != config_.end()) ? it->second : DEFAULT_TEMP_DIR;
}

int ConfigManager::getMaxStorageGB() const {
  auto it = config_.find("storage.max_storage_gb");
  return (it != config_.end()) ? std::stoi(it->second) : DEFAULT_MAX_STORAGE_GB;
}

std::string ConfigManager::getDbPath() const {
  auto it = config_.find("database.db_path");
  return (it != config_.end()) ? it->second : DEFAULT_DB_PATH;
}

std::string ConfigManager::getLogLevel() const {
  auto it = config_.find("logging.log_level");
  return (it != config_.end()) ? it->second : DEFAULT_LOG_LEVEL;
}

std::string ConfigManager::getLogFile() const {
  auto it = config_.find("logging.log_file");
  return (it != config_.end()) ? it->second : DEFAULT_LOG_FILE;
}

std::string ConfigManager::getServerName() const {
  auto it = config_.find("network.server_name");
  return (it != config_.end()) ? it->second : DEFAULT_SERVER_NAME;
}

bool ConfigManager::getConsoleOutput() const {
  auto it = config_.find("logging.console_output");
  if (it != config_.end()) {
    std::string value = it->second;
    std::transform(value.begin(), value.end(), value.begin(), ::tolower);
    return (value == "true" || value == "1" || value == "yes");
  }
  return DEFAULT_CONSOLE_OUTPUT;
}

// Authentication configuration getters
int ConfigManager::getSessionTimeoutSeconds() const {
  auto it = config_.find("auth.session_timeout_seconds");
  return (it != config_.end()) ? std::stoi(it->second)
                               : DEFAULT_SESSION_TIMEOUT;
}

int ConfigManager::getBcryptCost() const {
  auto it = config_.find("auth.bcrypt_cost");
  return (it != config_.end()) ? std::stoi(it->second) : DEFAULT_BCRYPT_COST;
}

int ConfigManager::getMaxFailedAttempts() const {
  auto it = config_.find("auth.max_failed_attempts");
  return (it != config_.end()) ? std::stoi(it->second)
                               : DEFAULT_MAX_FAILED_ATTEMPTS;
}

int ConfigManager::getLockoutDurationMinutes() const {
  auto it = config_.find("auth.lockout_duration_minutes");
  return (it != config_.end()) ? std::stoi(it->second)
                               : DEFAULT_LOCKOUT_DURATION;
}

int ConfigManager::getCleanupIntervalSeconds() const {
  auto it = config_.find("maintenance.cleanup_interval_seconds");
  return (it != config_.end()) ? std::stoi(it->second) : 300; // Default 5 mins
}

// Phase 3: Integrity & Retention
int ConfigManager::getIntegrityScanInterval() const {
  auto it = config_.find("integrity.scan_interval");
  return (it != config_.end()) ? std::stoi(it->second) : 3600;
}

bool ConfigManager::getIntegrityVerifyHash() const {
  auto it = config_.find("integrity.verify_hash");
  if (it != config_.end()) {
    std::string value = it->second;
    std::transform(value.begin(), value.end(), value.begin(), ::tolower);
    return (value == "true" || value == "1" || value == "yes");
  }
  return false;
}

int ConfigManager::getIntegrityMissingCheckInterval() const {
  auto it = config_.find("integrity.missing_check_interval");
  return (it != config_.end()) ? std::stoi(it->second) : 3600; // 1 hr
}

int ConfigManager::getIntegrityOrphanSampleInterval() const {
  auto it = config_.find("integrity.orphan_sample_interval");
  return (it != config_.end()) ? std::stoi(it->second) : 86400; // 24 hrs
}

int ConfigManager::getIntegrityFullScanInterval() const {
  auto it = config_.find("integrity.full_scan_interval");
  return (it != config_.end()) ? std::stoi(it->second) : 604800; // 7 days
}

int ConfigManager::getIntegrityOrphanSampleSize() const {
  auto it = config_.find("integrity.orphan_sample_size");
  return (it != config_.end()) ? std::stoi(it->second) : 1000;
}

int ConfigManager::getDeletedRetentionDays() const {
  auto it = config_.find("retention.deleted_retention_days");
  return (it != config_.end()) ? std::stoi(it->second) : 30;
}
