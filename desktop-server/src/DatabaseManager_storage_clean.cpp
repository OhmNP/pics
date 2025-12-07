
DatabaseManager::StorageStats DatabaseManager::getStorageStats() {
  StorageStats stats;
  sqlite3_stmt *stmt;

  // 1. Total storage and files
  stats.totalStorageUsed = getTotalStorageUsed();
  stats.totalFiles = getTotalPhotoCount();

  // 2. Client storage
  const char *clientSql =
      "SELECT client_id, SUM(size) FROM metadata GROUP BY client_id";
  if (sqlite3_prepare_v2(db_, clientSql, -1, &stmt, nullptr) == SQLITE_OK) {
    while (sqlite3_step(stmt) == SQLITE_ROW) {
      int clientId = sqlite3_column_int(stmt, 0);
      long long size = sqlite3_column_int64(stmt, 1);
      stats.clientStorage[clientId] = size;
    }
    sqlite3_finalize(stmt);
  } else {
    LOG_ERROR("Failed to prepare client storage stats: " +
              std::string(sqlite3_errmsg(db_)));
  }

  // 3. Mime type storage
  const char *mimeSql =
      "SELECT mime_type, SUM(size) FROM metadata GROUP BY mime_type";
  if (sqlite3_prepare_v2(db_, mimeSql, -1, &stmt, nullptr) == SQLITE_OK) {
    while (sqlite3_step(stmt) == SQLITE_ROW) {
      const char *mimeType =
          reinterpret_cast<const char *>(sqlite3_column_text(stmt, 0));
      long long size = sqlite3_column_int64(stmt, 1);

      std::string mimeStr = mimeType ? mimeType : "unknown";
      stats.mimeTypeStorage[mimeStr] = size;
    }
    sqlite3_finalize(stmt);
  } else {
    LOG_ERROR("Failed to prepare mime storage stats: " +
              std::string(sqlite3_errmsg(db_)));
  }

  return stats;
}
