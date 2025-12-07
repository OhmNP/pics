std::string ApiServer::handleGetHealth() {
  try {
    long long storageUsed = db_.getTotalStorageUsed();
    long long storageLimit = config_.getMaxStorageGB() * 1073741824LL;
    double utilization =
        (storageLimit > 0) ? (double)storageUsed / storageLimit * 100.0 : 0.0;

    // TODO: Get real uptime if possible, for now 0 or static
    long uptime = 0;

    json response = {
        {"uptime", uptime},
        {"storage",
         {{"used", storageUsed},
          {"limit", storageLimit},
          {"utilizationPercent", utilization},
          {"trend", json::array()}}},
        {"queue",
         {{"thumbnailGeneration", 0},
          {"sessionCleanup", 0},
          {"logCleanup", 0}}},
        {"system",
         {{"cpu", nullptr},
          {"ram", nullptr},
          {"note",
           "CPU/RAM metrics are placeholders for future implementation"}}},
        {"lastUpdated", db_.getCurrentTimestamp()}};
    return response.dump();
  } catch (const std::exception &e) {
    LOG_ERROR("Error in handleGetHealth: " + std::string(e.what()));
    json error = {{"error", e.what()}};
    return error.dump();
  }
}

std::string ApiServer::handleGetStorageOverview() {
  try {
    DatabaseManager::StorageStats stats = db_.getStorageStats();

    json clientStats = json::array();
    for (auto const &[clientId, size] : stats.clientStorage) {
      // Need to get client details to map ID to deviceId
      // This is inefficient but functional for now.
      // Better approach: getStorageStats returns vector of structs with
      // deviceId.
      clientStats.push_back({{"clientId", clientId}, {"storageUsed", size}});
    }

    // Convert map to object for JSON
    json mimeStats = json::object();
    for (auto const &[mime, size] : stats.mimeTypeStorage) {
      mimeStats[mime] = size;
    }

    json response = {{"totalStorageUsed", stats.totalStorageUsed},
                     {"totalFiles", stats.totalFiles},
                     {"storageLimit", config_.getMaxStorageGB() * 1073741824LL},
                     {"utilizationPercent",
                      (config_.getMaxStorageGB() > 0)
                          ? (double)stats.totalStorageUsed /
                                (config_.getMaxStorageGB() * 1073741824LL) *
                                100.0
                          : 0.0},
                     {"byClient", clientStats},
                     {"byFileType", mimeStats}};

    return response.dump();
  } catch (const std::exception &e) {
    LOG_ERROR("Error in handleGetStorageOverview: " + std::string(e.what()));
    json error = {{"error", e.what()}};
    return error.dump();
  }
}

std::string ApiServer::handleGetLogs() {
  try {
    // Basic implementation: get last 50 unread logs or just last 50
    // For now, let's get last 50 logs of any type
    std::vector<DatabaseManager::LogEntry> logs = db_.getLogs(50);
    json response = json::array();

    for (const auto &log : logs) {
      response.push_back({{"id", log.id},
                          {"level", log.level},
                          {"message", log.message},
                          {"timestamp", log.timestamp},
                          {"context", log.context},
                          {"read", log.read}});
    }

    return response.dump();
  } catch (const std::exception &e) {
    LOG_ERROR("Error in handleGetLogs: " + std::string(e.what()));
    json error = {{"error", e.what()}};
    return error.dump();
  }
}
