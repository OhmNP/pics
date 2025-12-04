#include "DatabaseManager.h"
#include "Logger.h"
#include <chrono>
#include <iomanip>
#include <sstream>

DatabaseManager::DatabaseManager() : db_(nullptr) {}

DatabaseManager::~DatabaseManager() { close(); }

bool DatabaseManager::open(const std::string &dbPath) {
  int rc = sqlite3_open(dbPath.c_str(), &db_);
  if (rc != SQLITE_OK) {
    LOG_ERROR("Failed to open database: " + std::string(sqlite3_errmsg(db_)));
    return false;
  }
  LOG_INFO("Database opened: " + dbPath);
  return true;
}

void DatabaseManager::close() {
  if (db_) {
    sqlite3_close(db_);
    db_ = nullptr;
    LOG_INFO("Database closed");
  }
}

bool DatabaseManager::createSchema() {
  const char *createClientTable = R"(
        CREATE TABLE IF NOT EXISTS clients (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            device_id TEXT UNIQUE,
            last_seen TIMESTAMP,
            total_photos INTEGER DEFAULT 0
        );
    )";

  const char *createPhotosTable = R"(
        CREATE TABLE IF NOT EXISTS photos (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            client_id INTEGER,
            filename TEXT,
            size INTEGER,
            hash TEXT UNIQUE,
            file_path TEXT,
            received_at TIMESTAMP,
            FOREIGN KEY(client_id) REFERENCES clients(id)
        );
    )";

  const char *createSessionsTable = R"(
        CREATE TABLE IF NOT EXISTS sync_sessions (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            client_id INTEGER,
            started_at TIMESTAMP,
            ended_at TIMESTAMP,
            photos_received INTEGER DEFAULT 0,
            status TEXT,
            FOREIGN KEY(client_id) REFERENCES clients(id)
        );
    )";

  const char *createHashIndex = R"(
        CREATE INDEX IF NOT EXISTS idx_photos_hash ON photos(hash);
    )";

  if (!executeSQL(createClientTable))
    return false;
  if (!executeSQL(createPhotosTable))
    return false;
  if (!executeSQL(createSessionsTable))
    return false;
  if (!executeSQL(createHashIndex))
    return false;

  LOG_INFO("Database schema created successfully");
  return true;
}

bool DatabaseManager::executeSQL(const std::string &sql) {
  char *errMsg = nullptr;
  int rc = sqlite3_exec(db_, sql.c_str(), nullptr, nullptr, &errMsg);

  if (rc != SQLITE_OK) {
    LOG_ERROR("SQL error: " + std::string(errMsg));
    sqlite3_free(errMsg);
    return false;
  }
  return true;
}

int DatabaseManager::getOrCreateClient(const std::string &deviceId) {
  // Try to find existing client
  sqlite3_stmt *stmt;
  const char *sql = "SELECT id FROM clients WHERE device_id = ?";

  if (sqlite3_prepare_v2(db_, sql, -1, &stmt, nullptr) != SQLITE_OK) {
    LOG_ERROR("Failed to prepare statement: " +
              std::string(sqlite3_errmsg(db_)));
    return -1;
  }

  sqlite3_bind_text(stmt, 1, deviceId.c_str(), -1, SQLITE_TRANSIENT);

  int clientId = -1;
  if (sqlite3_step(stmt) == SQLITE_ROW) {
    clientId = sqlite3_column_int(stmt, 0);
  }

  sqlite3_finalize(stmt);

  // If client doesn't exist, create it
  if (clientId == -1) {
    const char *insertSql = "INSERT INTO clients (device_id, last_seen, "
                            "total_photos) VALUES (?, ?, 0)";
    if (sqlite3_prepare_v2(db_, insertSql, -1, &stmt, nullptr) != SQLITE_OK) {
      LOG_ERROR("Failed to prepare insert statement: " +
                std::string(sqlite3_errmsg(db_)));
      return -1;
    }

    std::string timestamp = getCurrentTimestamp();
    sqlite3_bind_text(stmt, 1, deviceId.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 2, timestamp.c_str(), -1, SQLITE_TRANSIENT);

    if (sqlite3_step(stmt) != SQLITE_DONE) {
      LOG_ERROR("Failed to insert client: " + std::string(sqlite3_errmsg(db_)));
      sqlite3_finalize(stmt);
      return -1;
    }

    clientId = sqlite3_last_insert_rowid(db_);
    sqlite3_finalize(stmt);
    LOG_INFO("Created new client: " + deviceId +
             " (ID: " + std::to_string(clientId) + ")");
  }

  return clientId;
}

bool DatabaseManager::updateClientLastSeen(int clientId) {
  sqlite3_stmt *stmt;
  const char *sql = "UPDATE clients SET last_seen = ? WHERE id = ?";

  if (sqlite3_prepare_v2(db_, sql, -1, &stmt, nullptr) != SQLITE_OK) {
    LOG_ERROR("Failed to prepare update statement: " +
              std::string(sqlite3_errmsg(db_)));
    return false;
  }

  std::string timestamp = getCurrentTimestamp();
  sqlite3_bind_text(stmt, 1, timestamp.c_str(), -1, SQLITE_TRANSIENT);
  sqlite3_bind_int(stmt, 2, clientId);

  sqlite3_step(stmt);
  sqlite3_finalize(stmt);
  return true;
}

int DatabaseManager::createSession(int clientId) {
  sqlite3_stmt *stmt;
  const char *sql = "INSERT INTO sync_sessions (client_id, started_at, status) "
                    "VALUES (?, ?, 'active')";

  if (sqlite3_prepare_v2(db_, sql, -1, &stmt, nullptr) != SQLITE_OK) {
    LOG_ERROR("Failed to prepare session insert: " +
              std::string(sqlite3_errmsg(db_)));
    return -1;
  }

  std::string timestamp = getCurrentTimestamp();
  sqlite3_bind_int(stmt, 1, clientId);
  sqlite3_bind_text(stmt, 2, timestamp.c_str(), -1, SQLITE_TRANSIENT);

  if (sqlite3_step(stmt) != SQLITE_DONE) {
    LOG_ERROR("Failed to create session: " + std::string(sqlite3_errmsg(db_)));
    sqlite3_finalize(stmt);
    return -1;
  }

  int sessionId = sqlite3_last_insert_rowid(db_);
  sqlite3_finalize(stmt);
  LOG_INFO("Created session ID: " + std::to_string(sessionId));
  return sessionId;
}

void DatabaseManager::updateSessionPhotosReceived(int sessionId, int count) {
  sqlite3_stmt *stmt;
  const char *sql = "UPDATE sync_sessions SET photos_received = ? WHERE id = ?";

  if (sqlite3_prepare_v2(db_, sql, -1, &stmt, nullptr) != SQLITE_OK) {
    LOG_ERROR("Failed to prepare session update: " +
              std::string(sqlite3_errmsg(db_)));
    return;
  }

  sqlite3_bind_int(stmt, 1, count);
  sqlite3_bind_int(stmt, 2, sessionId);

  sqlite3_step(stmt);
  sqlite3_finalize(stmt);
}

void DatabaseManager::finalizeSession(int sessionId,
                                      const std::string &status) {
  sqlite3_stmt *stmt;
  const char *sql =
      "UPDATE sync_sessions SET ended_at = ?, status = ? WHERE id = ?";

  if (sqlite3_prepare_v2(db_, sql, -1, &stmt, nullptr) != SQLITE_OK) {
    LOG_ERROR("Failed to prepare session finalize: " +
              std::string(sqlite3_errmsg(db_)));
    return;
  }

  std::string timestamp = getCurrentTimestamp();
  sqlite3_bind_text(stmt, 1, timestamp.c_str(), -1, SQLITE_TRANSIENT);
  sqlite3_bind_text(stmt, 2, status.c_str(), -1, SQLITE_TRANSIENT);
  sqlite3_bind_int(stmt, 3, sessionId);

  sqlite3_step(stmt);
  sqlite3_finalize(stmt);
  LOG_INFO("Finalized session " + std::to_string(sessionId) +
           " with status: " + status);
}

void DatabaseManager::updateSessionPhotoCount(int sessionId, int photoCount) {
  updateSessionPhotosReceived(sessionId, photoCount);
}

bool DatabaseManager::insertPhoto(int clientId, const PhotoMetadata &photo,
                                  const std::string &filePath) {
  // Check if photo already exists
  if (photoExists(photo.hash)) {
    LOG_DEBUG("Photo already exists: " + photo.hash);
    return true; // Not an error, just skip duplicate
  }

  sqlite3_stmt *stmt;
  const char *sql =
      "INSERT INTO photos (client_id, filename, size, hash, file_path, "
      "received_at) VALUES (?, ?, ?, ?, ?, ?)";

  if (sqlite3_prepare_v2(db_, sql, -1, &stmt, nullptr) != SQLITE_OK) {
    LOG_ERROR("Failed to prepare photo insert: " +
              std::string(sqlite3_errmsg(db_)));
    return false;
  }

  std::string timestamp = getCurrentTimestamp();
  sqlite3_bind_int(stmt, 1, clientId);
  sqlite3_bind_text(stmt, 2, photo.filename.c_str(), -1, SQLITE_TRANSIENT);
  sqlite3_bind_int64(stmt, 3, photo.size);
  sqlite3_bind_text(stmt, 4, photo.hash.c_str(), -1, SQLITE_TRANSIENT);
  sqlite3_bind_text(stmt, 5, filePath.c_str(), -1, SQLITE_TRANSIENT);
  sqlite3_bind_text(stmt, 6, timestamp.c_str(), -1, SQLITE_TRANSIENT);

  if (sqlite3_step(stmt) != SQLITE_DONE) {
    LOG_ERROR("Failed to insert photo: " + std::string(sqlite3_errmsg(db_)));
    sqlite3_finalize(stmt);
    return false;
  }

  sqlite3_finalize(stmt);

  // Update client's total photo count
  const char *updateSql =
      "UPDATE clients SET total_photos = total_photos + 1 WHERE id = ?";
  if (sqlite3_prepare_v2(db_, updateSql, -1, &stmt, nullptr) == SQLITE_OK) {
    sqlite3_bind_int(stmt, 1, clientId);
    sqlite3_step(stmt);
    sqlite3_finalize(stmt);
  }

  return true;
}

bool DatabaseManager::photoExists(const std::string &hash) {
  sqlite3_stmt *stmt;
  const char *sql = "SELECT COUNT(*) FROM photos WHERE hash = ?";

  if (sqlite3_prepare_v2(db_, sql, -1, &stmt, nullptr) != SQLITE_OK) {
    return false;
  }

  sqlite3_bind_text(stmt, 1, hash.c_str(), -1, SQLITE_TRANSIENT);

  bool exists = false;
  if (sqlite3_step(stmt) == SQLITE_ROW) {
    exists = (sqlite3_column_int(stmt, 0) > 0);
  }

  sqlite3_finalize(stmt);
  return exists;
}

int DatabaseManager::getPhotoCount(int clientId) {
  sqlite3_stmt *stmt;
  const char *sql = "SELECT COUNT(*) FROM photos WHERE client_id = ?";

  if (sqlite3_prepare_v2(db_, sql, -1, &stmt, nullptr) != SQLITE_OK) {
    return 0;
  }

  sqlite3_bind_int(stmt, 1, clientId);

  int count = 0;
  if (sqlite3_step(stmt) == SQLITE_ROW) {
    count = sqlite3_column_int(stmt, 0);
  }

  sqlite3_finalize(stmt);
  return count;
}

// API Statistics Methods
int DatabaseManager::getTotalPhotoCount() {
  const char *sql = "SELECT COUNT(*) FROM photos;";
  sqlite3_stmt *stmt;

  if (sqlite3_prepare_v2(db_, sql, -1, &stmt, nullptr) != SQLITE_OK) {
    LOG_ERROR("Failed to prepare getTotalPhotoCount: " +
              std::string(sqlite3_errmsg(db_)));
    return 0;
  }

  int count = 0;
  if (sqlite3_step(stmt) == SQLITE_ROW) {
    count = sqlite3_column_int(stmt, 0);
  }

  sqlite3_finalize(stmt);
  return count;
}

int DatabaseManager::getTotalClientCount() {
  const char *sql = "SELECT COUNT(*) FROM clients;";
  sqlite3_stmt *stmt;

  if (sqlite3_prepare_v2(db_, sql, -1, &stmt, nullptr) != SQLITE_OK) {
    LOG_ERROR("Failed to prepare getTotalClientCount: " +
              std::string(sqlite3_errmsg(db_)));
    return 0;
  }

  int count = 0;
  if (sqlite3_step(stmt) == SQLITE_ROW) {
    count = sqlite3_column_int(stmt, 0);
  }

  sqlite3_finalize(stmt);
  return count;
}

int DatabaseManager::getCompletedSessionCount() {
  const char *sql =
      "SELECT COUNT(*) FROM sync_sessions WHERE status = 'completed';";
  sqlite3_stmt *stmt;

  if (sqlite3_prepare_v2(db_, sql, -1, &stmt, nullptr) != SQLITE_OK) {
    LOG_ERROR("Failed to prepare getCompletedSessionCount: " +
              std::string(sqlite3_errmsg(db_)));
    return 0;
  }

  int count = 0;
  if (sqlite3_step(stmt) == SQLITE_ROW) {
    count = sqlite3_column_int(stmt, 0);
  }

  sqlite3_finalize(stmt);
  return count;
}

long long DatabaseManager::getTotalStorageUsed() {
  const char *sql = "SELECT SUM(size) FROM photos;";
  sqlite3_stmt *stmt;

  if (sqlite3_prepare_v2(db_, sql, -1, &stmt, nullptr) != SQLITE_OK) {
    LOG_ERROR("Failed to prepare getTotalStorageUsed: " +
              std::string(sqlite3_errmsg(db_)));
    return 0;
  }

  long long totalSize = 0;
  if (sqlite3_step(stmt) == SQLITE_ROW) {
    totalSize = sqlite3_column_int64(stmt, 0);
  }

  sqlite3_finalize(stmt);
  return totalSize;
}

std::string DatabaseManager::getCurrentTimestamp() {
  auto now = std::chrono::system_clock::now();
  auto time = std::chrono::system_clock::to_time_t(now);

  std::stringstream ss;
  ss << std::put_time(std::gmtime(&time), "%Y-%m-%d %H:%M:%S");
  return ss.str();
}
