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
