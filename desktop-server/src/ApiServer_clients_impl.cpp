
std::string ApiServer::handleGetClientDetails(int clientId) {
  try {
    DatabaseManager::ClientRecord client = db_.getClientDetails(clientId);
    if (client.id == -1) {
      json error = {{"error", "Client not found"}};
      return error.dump();
    }

    json response = {
        {"id", client.id},
        {"deviceId", client.deviceId},
        {"lastSeen", client.lastSeen},
        {"photoCount", client.photoCount},
        {"storageUsed", client.storageUsed},
        {"formattedStorage",
         client.storageUsed} // formatBytes logic usually in frontend, but could
                             // send number
    };

    return response.dump();
  } catch (const std::exception &e) {
    LOG_ERROR("Error in handleGetClientDetails: " + std::string(e.what()));
    json error = {{"error", e.what()}};
    return error.dump();
  }
}
