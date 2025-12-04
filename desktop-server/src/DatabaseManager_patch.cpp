void DatabaseManager::updateSessionPhotosReceived(int sessionId,
                                                  int photosReceived) {
  const char *sql = "UPDATE sync_sessions SET photos_received = ? WHERE id = ?";
  sqlite3_stmt *stmt;

  if (sqlite3_prepare_v2(db_, sql, -1, &stmt, nullptr) == SQLITE_OK) {
    sqlite3_bind_int(stmt, 1, photosReceived);
    sqlite3_bind_int(stmt, 2, sessionId);
    sqlite3_step(stmt);
    sqlite3_finalize(stmt);
  }
}

void DatabaseManager::updateSessionPhotoCount(int sessionId, int photoCount) {
  // Alias for updateSessionPhotosReceived
  updateSessionPhotosReceived(sessionId, photoCount);
}
