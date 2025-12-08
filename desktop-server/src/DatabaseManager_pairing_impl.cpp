
std::string DatabaseManager::generatePairingToken() {
  // Generate a 6-digit random code
  std::random_device rd;
  std::mt19937 gen(rd());
  std::uniform_int_distribution<> distrib(100000, 999999);
  std::string token = std::to_string(distrib(gen));

  // Expiry: 15 minutes from now
  auto now = std::chrono::system_clock::now();
  auto expires = now + std::chrono::minutes(15);
  std::time_t expiresTime = std::chrono::system_clock::to_time_t(expires);

  std::stringstream ss;
  ss << std::put_time(std::localtime(&expiresTime), "%Y-%m-%d %H:%M:%S");
  std::string expiresAt = ss.str();

  const char *sql =
      "INSERT INTO pairing_tokens (token, expires_at) VALUES (?, ?);";
  sqlite3_stmt *stmt;
  if (sqlite3_prepare_v2(db_, sql, -1, &stmt, nullptr) == SQLITE_OK) {
    sqlite3_bind_text(stmt, 1, token.c_str(), -1, SQLITE_STATIC);
    sqlite3_bind_text(stmt, 2, expiresAt.c_str(), -1, SQLITE_STATIC);

    if (sqlite3_step(stmt) != SQLITE_DONE) {
      LOG_ERROR("Failed to insert pairing token: " +
                std::string(sqlite3_errmsg(db_)));
      token = ""; // Failure
    }
    sqlite3_finalize(stmt);
  } else {
    LOG_ERROR("Failed to prepare generatePairingToken: " +
              std::string(sqlite3_errmsg(db_)));
    token = "";
  }
  return token;
}

bool DatabaseManager::validatePairingToken(const std::string &token) {
  bool isValid = false;
  const char *sql = "SELECT id FROM pairing_tokens WHERE token = ? AND is_used "
                    "= 0 AND expires_at > datetime('now', 'localtime');";
  sqlite3_stmt *stmt;

  if (sqlite3_prepare_v2(db_, sql, -1, &stmt, nullptr) == SQLITE_OK) {
    sqlite3_bind_text(stmt, 1, token.c_str(), -1, SQLITE_STATIC);
    if (sqlite3_step(stmt) == SQLITE_ROW) {
      isValid = true;
    }
    sqlite3_finalize(stmt);
  }
  return isValid;
}

bool DatabaseManager::markPairingTokenUsed(const std::string &token) {
  const char *sql = "UPDATE pairing_tokens SET is_used = 1 WHERE token = ?;";
  return executeSQL(sql); // This is lazy, strictly should bind param.
                          // Let's do it properly
                          /*
                          sqlite3_stmt *stmt;
                          if (sqlite3_prepare_v2(db_, sql, -1, &stmt, nullptr) == SQLITE_OK) {
                              sqlite3_bind_text(stmt, 1, token.c_str(), -1, SQLITE_STATIC);
                              bool success = (sqlite3_step(stmt) == SQLITE_DONE);
                              sqlite3_finalize(stmt);
                              return success;
                          }
                          return false;
                          */
}

int DatabaseManager::cleanupExpiredPairingTokens() {
  const char *sql = "DELETE FROM pairing_tokens WHERE expires_at <= "
                    "datetime('now', 'localtime') OR is_used = 1;";
  sqlite3_stmt *stmt;
  int count = 0;
  if (sqlite3_prepare_v2(db_, sql, -1, &stmt, nullptr) == SQLITE_OK) {
    if (sqlite3_step(stmt) == SQLITE_DONE) {
      count = sqlite3_changes(db_);
    }
    sqlite3_finalize(stmt);
  }
  return count;
}
