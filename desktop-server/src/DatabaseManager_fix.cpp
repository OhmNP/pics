
// Log operations implementation

bool DatabaseManager::createLog(const std::string &level,
                                const std::string &message,
                                const std::string &context) {
  const char *sql =
      "INSERT INTO logs (level, message, context) VALUES (?, ?, ?);";
  sqlite3_stmt *stmt;

  if (sqlite3_prepare_v2(db_, sql, -1, &stmt, nullptr) != SQLITE_OK) {
    LOG_ERROR("Failed to prepare createLog: " +
              std::string(sqlite3_errmsg(db_)));
    return false;
  }

  sqlite3_bind_text(stmt, 1, level.c_str(), -1, SQLITE_STATIC);
  sqlite3_bind_text(stmt, 2, message.c_str(), -1, SQLITE_STATIC);
  sqlite3_bind_text(stmt, 3, context.c_str(), -1, SQLITE_STATIC);

  bool success = (sqlite3_step(stmt) == SQLITE_DONE);
  sqlite3_finalize(stmt);
  return success;
}

std::vector<DatabaseManager::LogEntry>
DatabaseManager::getLogs(int limit, int offset, const std::string &level,
                         bool onlyUnread) {
  std::vector<LogEntry> logs;
  std::string sql =
      "SELECT id, level, message, timestamp, context, read FROM logs";
  std::vector<std::string> conditions;

  if (!level.empty()) {
    conditions.push_back("level = ?");
  }
  if (onlyUnread) {
    conditions.push_back("read = 0");
  }

  if (!conditions.empty()) {
    sql += " WHERE ";
    for (size_t i = 0; i < conditions.size(); ++i) {
      if (i > 0)
        sql += " AND ";
      sql += conditions[i];
    }
  }

  sql += " ORDER BY timestamp DESC LIMIT ? OFFSET ?";

  sqlite3_stmt *stmt;
  if (sqlite3_prepare_v2(db_, sql.c_str(), -1, &stmt, nullptr) != SQLITE_OK) {
    LOG_ERROR("Failed to prepare getLogs: " + std::string(sqlite3_errmsg(db_)));
    return logs;
  }

  int bindIndex = 1;
  if (!level.empty()) {
    sqlite3_bind_text(stmt, bindIndex++, level.c_str(), -1, SQLITE_STATIC);
  }
  sqlite3_bind_int(stmt, bindIndex++, limit);
  sqlite3_bind_int(stmt, bindIndex++, offset);

  while (sqlite3_step(stmt) == SQLITE_ROW) {
    LogEntry log;
    log.id = sqlite3_column_int(stmt, 0);
    log.level = reinterpret_cast<const char *>(sqlite3_column_text(stmt, 1));
    log.message = reinterpret_cast<const char *>(sqlite3_column_text(stmt, 2));
    log.timestamp =
        reinterpret_cast<const char *>(sqlite3_column_text(stmt, 3));
    const char *ctx =
        reinterpret_cast<const char *>(sqlite3_column_text(stmt, 4));
    log.context = ctx ? ctx : "";
    log.read = sqlite3_column_int(stmt, 5);
    logs.push_back(log);
  }

  sqlite3_finalize(stmt);
  return logs;
}

int DatabaseManager::getUnreadLogCount() {
  const char *sql = "SELECT COUNT(*) FROM logs WHERE read = 0;";
  sqlite3_stmt *stmt;
  int count = 0;

  if (sqlite3_prepare_v2(db_, sql, -1, &stmt, nullptr) == SQLITE_OK) {
    if (sqlite3_step(stmt) == SQLITE_ROW) {
      count = sqlite3_column_int(stmt, 0);
    }
    sqlite3_finalize(stmt);
  }
  return count;
}

bool DatabaseManager::markLogAsRead(int logId) {
  const char *sql = "UPDATE logs SET read = 1 WHERE id = ?;";
  sqlite3_stmt *stmt;

  if (sqlite3_prepare_v2(db_, sql, -1, &stmt, nullptr) != SQLITE_OK) {
    return false;
  }

  sqlite3_bind_int(stmt, 1, logId);
  bool success = (sqlite3_step(stmt) == SQLITE_DONE);
  sqlite3_finalize(stmt);
  return success;
}

bool DatabaseManager::markAllLogsAsRead() {
  return executeSQL("UPDATE logs SET read = 1;");
}

int DatabaseManager::cleanupOldLogs(int daysToKeep) {
  std::string sql = "DELETE FROM logs WHERE timestamp < date('now', '-" +
                    std::to_string(daysToKeep) + " days');";
  // Using executeSQL for simple query, but need count of changes.
  // executeSQL returns bool.
  // Let's just use it.
  if (executeSQL(sql)) {
    return sqlite3_changes(db_);
  }
  return 0;
}
