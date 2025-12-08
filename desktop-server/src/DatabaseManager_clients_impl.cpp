
DatabaseManager::ClientRecord DatabaseManager::getClientDetails(int clientId) {
  ClientRecord client = {-1, "", "", 0, 0};
  const char *sql = "SELECT id, device_id, last_seen, total_photos FROM "
                    "clients WHERE id = ?;";
  sqlite3_stmt *stmt;

  if (sqlite3_prepare_v2(db_, sql, -1, &stmt, nullptr) == SQLITE_OK) {
    sqlite3_bind_int(stmt, 1, clientId);
    if (sqlite3_step(stmt) == SQLITE_ROW) {
      client.id = sqlite3_column_int(stmt, 0);
      client.deviceId =
          reinterpret_cast<const char *>(sqlite3_column_text(stmt, 1));

      const char *lastSeen =
          reinterpret_cast<const char *>(sqlite3_column_text(stmt, 2));
      client.lastSeen = lastSeen ? lastSeen : "";

      client.photoCount = sqlite3_column_int(stmt, 3);
    }
    sqlite3_finalize(stmt);
  } else {
    LOG_ERROR("Failed to prepare getClientDetails: " +
              std::string(sqlite3_errmsg(db_)));
  }

  // Get storage usage specially
  const char *storageSql =
      "SELECT SUM(size) FROM metadata WHERE client_id = ?;";
  if (sqlite3_prepare_v2(db_, storageSql, -1, &stmt, nullptr) == SQLITE_OK) {
    sqlite3_bind_int(stmt, 1, clientId);
    if (sqlite3_step(stmt) == SQLITE_ROW) {
      client.storageUsed = sqlite3_column_int64(stmt, 0);
    }
    sqlite3_finalize(stmt);
  }

  return client;
}
