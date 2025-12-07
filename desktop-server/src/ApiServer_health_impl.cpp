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
