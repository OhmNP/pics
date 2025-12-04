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

bool ConfigManager::getConsoleOutput() const {
  auto it = config_.find("logging.console_output");
  if (it != config_.end()) {
    std::string value = it->second;
    std::transform(value.begin(), value.end(), value.begin(), ::tolower);
    return (value == "true" || value == "1" || value == "yes");
  }
  return DEFAULT_CONSOLE_OUTPUT;
}
