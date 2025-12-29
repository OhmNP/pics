// Authentication operations
bool DatabaseManager::createAdminUser(const std::string &username,
                                      const std::string &passwordHash) {
  const char *sql =
      "INSERT INTO admin_users (username, password_hash) VALUES (?, ?);";
  sqlite3_stmt *stmt;
  if (sqlite3_prepare_v2(db_, sql, -1, &stmt, nullptr) != SQLITE_OK) {
    LOG_ERROR("Failed to prepare createAdminUser: " +
              std::string(sqlite3_errmsg(db_)));
    return false;
  }

  sqlite3_bind_text(stmt, 1, username.c_str(), -1, SQLITE_STATIC);
  sqlite3_bind_text(stmt, 2, passwordHash.c_str(), -1, SQLITE_STATIC);

  bool success = (sqlite3_step(stmt) == SQLITE_DONE);
  sqlite3_finalize(stmt);
  return success;
}

AdminUser DatabaseManager::getAdminUserByUsername(const std::string &username) {
  AdminUser user;
  user.id = -1;
  user.isActive = false;

  const char *sql =
      "SELECT id, username, password_hash, created_at, last_login, is_active "
      "FROM admin_users WHERE username = ?;";
  sqlite3_stmt *stmt;

  if (sqlite3_prepare_v2(db_, sql, -1, &stmt, nullptr) == SQLITE_OK) {
    sqlite3_bind_text(stmt, 1, username.c_str(), -1, SQLITE_STATIC);

    if (sqlite3_step(stmt) == SQLITE_ROW) {
      user.id = sqlite3_column_int(stmt, 0);
      user.username =
          reinterpret_cast<const char *>(sqlite3_column_text(stmt, 1));
      user.passwordHash =
          reinterpret_cast<const char *>(sqlite3_column_text(stmt, 2));
      user.createdAt =
          reinterpret_cast<const char *>(sqlite3_column_text(stmt, 3));

      const char *lastLogin =
          reinterpret_cast<const char *>(sqlite3_column_text(stmt, 4));
      user.lastLogin = lastLogin ? lastLogin : "";

      user.isActive = sqlite3_column_int(stmt, 5) != 0;
    }
    sqlite3_finalize(stmt);
  }
  return user;
}

bool DatabaseManager::insertInitialAdminUser() {
  // Check if admin user exists
  AdminUser user = getAdminUserByUsername("admin");
  if (user.id != -1) {
    return true; // Already exists
  }

  LOG_INFO("Created initial admin user (username: admin, password: admin123)");
  LOG_INFO("IMPORTANT: Change the default password immediately!");

  // NOTE: In production, use AuthenticationManager::hashPassword("admin123")
  // For now we assume pre-hashed or simple hash for initial setup if
  // AuthManager not linked here But AuthenticationManager uses SHA256. Let's
  // use a hardcoded SHA256 of "admin123" for simplicity or just plain text if
  // dev mode? Ideally we should inject AuthManager dependency or use raw SHA256
  // here. "admin123" SHA256 =
  // 240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9
  std::string hash =
      "240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9";

  return createAdminUser("admin", hash);
}

bool DatabaseManager::createAuthSession(const std::string &sessionToken,
                                        int userId,
                                        const std::string &expiresAt,
                                        const std::string &ipAddress) {
  const char *sql = "INSERT INTO auth_sessions (session_token, user_id, "
                    "expires_at, ip_address) "
                    "VALUES (?, ?, ?, ?);";
  sqlite3_stmt *stmt;
  if (sqlite3_prepare_v2(db_, sql, -1, &stmt, nullptr) != SQLITE_OK) {
    return false;
  }

  sqlite3_bind_text(stmt, 1, sessionToken.c_str(), -1, SQLITE_STATIC);
  sqlite3_bind_int(stmt, 2, userId);
  sqlite3_bind_text(stmt, 3, expiresAt.c_str(), -1, SQLITE_STATIC);
  sqlite3_bind_text(stmt, 4, ipAddress.c_str(), -1, SQLITE_STATIC);

  bool success = (sqlite3_step(stmt) == SQLITE_DONE);
  sqlite3_finalize(stmt);
  return success;
}

AuthSession
DatabaseManager::getSessionByToken(const std::string &sessionToken) {
  AuthSession session;
  session.id = -1;

  const char *sql = "SELECT id, session_token, user_id, created_at, "
                    "expires_at, ip_address "
                    "FROM auth_sessions WHERE session_token = ? AND "
                    "expires_at > datetime('now', 'localtime');";
  sqlite3_stmt *stmt;

  if (sqlite3_prepare_v2(db_, sql, -1, &stmt, nullptr) == SQLITE_OK) {
    sqlite3_bind_text(stmt, 1, sessionToken.c_str(), -1, SQLITE_STATIC);

    if (sqlite3_step(stmt) == SQLITE_ROW) {
      session.id = sqlite3_column_int(stmt, 0);
      session.sessionToken =
          reinterpret_cast<const char *>(sqlite3_column_text(stmt, 1));
      session.userId = sqlite3_column_int(stmt, 2);
      session.createdAt =
          reinterpret_cast<const char *>(sqlite3_column_text(stmt, 3));
      session.expiresAt =
          reinterpret_cast<const char *>(sqlite3_column_text(stmt, 4));

      const char *ip =
          reinterpret_cast<const char *>(sqlite3_column_text(stmt, 5));
      session.ipAddress = ip ? ip : "";
    }
    sqlite3_finalize(stmt);
  }
  return session;
}

bool DatabaseManager::deleteSession(const std::string &sessionToken) {
  const char *sql = "DELETE FROM auth_sessions WHERE session_token = ?;";
  sqlite3_stmt *stmt;
  bool success = false;

  if (sqlite3_prepare_v2(db_, sql, -1, &stmt, nullptr) == SQLITE_OK) {
    sqlite3_bind_text(stmt, 1, sessionToken.c_str(), -1, SQLITE_STATIC);
    success = (sqlite3_step(stmt) == SQLITE_DONE);
    sqlite3_finalize(stmt);
  }
  return success;
}

int DatabaseManager::cleanupExpiredSessions() {
  const char *sql = "DELETE FROM auth_sessions WHERE expires_at <= "
                    "datetime('now', 'localtime');";
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
