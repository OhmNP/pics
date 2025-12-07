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
