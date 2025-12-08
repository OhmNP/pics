
std::string ApiServer::handlePostGenerateToken() {
  try {
    std::string token = db_.generatePairingToken();
    if (token.empty()) {
      json error = {{"error", "Failed to generate token"}};
      return error.dump();
    }

    json response = {
        {"token", token},
        {"expiresIn", 15 * 60},              // 15 minutes
        {"expiresAt", "15 minutes from now"} // Simplification
    };

    return response.dump();
  } catch (const std::exception &e) {
    LOG_ERROR("Error in handlePostGenerateToken: " + std::string(e.what()));
    json error = {{"error", e.what()}};
    return error.dump();
  }
}
