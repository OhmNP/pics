#include "DatabaseManager.h"
#include "AuthenticationManager.h"
#include "Logger.h"
#include <chrono>
#include <filesystem>
#include <iomanip>
#include <iostream>
#include <map> // Added for the new implementation
#include <random>
#include <set>
#include <sstream>

namespace fs = std::filesystem;

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

  if (!executeSQL(createClientTable))
    return false;
  // createPhotosTable removed
  if (!executeSQL(createSessionsTable))
    return false;
  // createHashIndex removed

  // Authentication tables
  const char *createAdminUsersTable = R"(
        CREATE TABLE IF NOT EXISTS admin_users (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            username TEXT UNIQUE NOT NULL,
            password_hash TEXT NOT NULL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            last_login TIMESTAMP,
            is_active BOOLEAN DEFAULT 1
        );
    )";

  const char *createAuthSessionsTable = R"(
        CREATE TABLE IF NOT EXISTS sessions (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            session_token TEXT UNIQUE NOT NULL,
            user_id INTEGER NOT NULL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            expires_at TIMESTAMP NOT NULL,
            ip_address TEXT,
            FOREIGN KEY(user_id) REFERENCES admin_users(id)
        );
    )";

  const char *createSessionTokenIndex = R"(
        CREATE INDEX IF NOT EXISTS idx_sessions_token ON sessions(session_token);
    )";

  const char *createSessionExpiresIndex = R"(
        CREATE INDEX IF NOT EXISTS idx_sessions_expires ON sessions(expires_at);
    )";

  const char *createPasswordResetTokensTable = R"(
        CREATE TABLE IF NOT EXISTS password_reset_tokens (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            username TEXT NOT NULL,
            token TEXT UNIQUE NOT NULL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            expires_at TIMESTAMP NOT NULL,
            used BOOLEAN DEFAULT 0,
            FOREIGN KEY(username) REFERENCES admin_users(username)
        );
    )";

  const char *createResetTokenIndex = R"(
        CREATE INDEX IF NOT EXISTS idx_reset_token ON password_reset_tokens(token);
    )";

  // Metadata table for media grid
  const char *createMetadataTable = R"(
        CREATE TABLE IF NOT EXISTS metadata (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            client_id INTEGER,
            filename TEXT NOT NULL,
            hash TEXT UNIQUE NOT NULL,
            size INTEGER NOT NULL,
            width INTEGER DEFAULT 0,
            height INTEGER DEFAULT 0,
            mime_type TEXT,
            taken_at TIMESTAMP,
            received_at TIMESTAMP,
            original_path TEXT,
            FOREIGN KEY(client_id) REFERENCES clients(id)
        );
    )";

  const char *createMetadataHashIndex = R"(
        CREATE INDEX IF NOT EXISTS idx_metadata_hash ON metadata(hash);
    )";

  const char *createMetadataClientIndex = R"(
        CREATE INDEX IF NOT EXISTS idx_metadata_client ON metadata(client_id);
    )";

  // Create metadata table first
  if (!executeSQL(createMetadataTable))
    return false;

  const char *createPairingTokensTable = R"(
        CREATE TABLE IF NOT EXISTS pairing_tokens (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            token TEXT UNIQUE NOT NULL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            expires_at TIMESTAMP NOT NULL,
            is_used BOOLEAN DEFAULT 0
        );
    )";

  if (!executeSQL(createPairingTokensTable))
    return false;
  if (!executeSQL(createMetadataHashIndex))
    return false;
  if (!executeSQL(createMetadataClientIndex))
    return false;

  if (!executeSQL(createAdminUsersTable))
    return false;
  if (!executeSQL(createAuthSessionsTable))
    return false;
  if (!executeSQL(createSessionTokenIndex))
    return false;
  if (!executeSQL(createSessionExpiresIndex))
    return false;
  if (!executeSQL(createPasswordResetTokensTable))
    return false;
  if (!executeSQL(createResetTokenIndex))
    return false;

  // Migrate existing photos if necessary
  if (!migratePhotosToMetadata()) {
    LOG_ERROR("Failed to migrate photos to metadata table");
  }

  // Create initial admin user if none exists
  insertInitialAdminUser();

  // Run migrations
  if (!migrateSchema()) {
    LOG_ERROR("Failed to migrate schema");
  }

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

bool DatabaseManager::migrateSchema() {
  // Add user_name column to clients table if it doesn't exist
  const char *checkSql = "SELECT user_name FROM clients LIMIT 1";
  sqlite3_stmt *stmt;
  if (sqlite3_prepare_v2(db_, checkSql, -1, &stmt, nullptr) != SQLITE_OK) {
    // Column likely doesn't exist
    const char *alterSql = "ALTER TABLE clients ADD COLUMN user_name TEXT";
    if (!executeSQL(alterSql)) {
      LOG_ERROR("Failed to add user_name column to clients table");
      return false;
    }
    LOG_INFO("Added user_name column to clients table");
  } else {
    sqlite3_finalize(stmt);
  }
  return true;
}

int DatabaseManager::getOrCreateClient(const std::string &deviceId,
                                       const std::string &userName) {
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

  // If client exists, update user_name if provided
  if (clientId != -1 && !userName.empty()) {
    const char *updateSql = "UPDATE clients SET user_name = ? WHERE id = ?";
    if (sqlite3_prepare_v2(db_, updateSql, -1, &stmt, nullptr) == SQLITE_OK) {
      sqlite3_bind_text(stmt, 1, userName.c_str(), -1, SQLITE_TRANSIENT);
      sqlite3_bind_int(stmt, 2, clientId);
      sqlite3_step(stmt);
      sqlite3_finalize(stmt);
    }
  }

  // If client doesn't exist, create it
  if (clientId == -1) {
    const char *insertSql = "INSERT INTO clients (device_id, last_seen, "
                            "total_photos, user_name) VALUES (?, ?, 0, ?)";
    if (sqlite3_prepare_v2(db_, insertSql, -1, &stmt, nullptr) != SQLITE_OK) {
      LOG_ERROR("Failed to prepare insert statement: " +
                std::string(sqlite3_errmsg(db_)));
      return -1;
    }

    std::string timestamp = getCurrentTimestamp();
    sqlite3_bind_text(stmt, 1, deviceId.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 2, timestamp.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 3, userName.c_str(), -1, SQLITE_TRANSIENT);

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

std::vector<DatabaseManager::ClientRecord> DatabaseManager::getClients() {
  std::vector<ClientRecord> clients;
  sqlite3_stmt *stmt;

  // Join clients with photos to calculate total storage used
  const char *sql = R"(
        SELECT 
            c.id, 
            c.device_id, 
            c.last_seen, 
            c.total_photos,
            COALESCE(SUM(m.size), 0) as storage_used,
            c.user_name
        FROM clients c
        LEFT JOIN metadata m ON c.id = m.client_id
        GROUP BY c.id
        ORDER BY c.last_seen DESC
    )";

  if (sqlite3_prepare_v2(db_, sql, -1, &stmt, nullptr) != SQLITE_OK) {
    LOG_ERROR("Failed to prepare getClients statement: " +
              std::string(sqlite3_errmsg(db_)));
    return clients;
  }

  while (sqlite3_step(stmt) == SQLITE_ROW) {
    ClientRecord client;
    client.id = sqlite3_column_int(stmt, 0);
    client.deviceId =
        reinterpret_cast<const char *>(sqlite3_column_text(stmt, 1));
    client.lastSeen =
        reinterpret_cast<const char *>(sqlite3_column_text(stmt, 2));
    client.photoCount = sqlite3_column_int(stmt, 3);
    client.storageUsed = sqlite3_column_int64(stmt, 4);

    const char *userName =
        reinterpret_cast<const char *>(sqlite3_column_text(stmt, 5));
    client.userName = userName ? userName : "";

    clients.push_back(client);
  }

  sqlite3_finalize(stmt);
  return clients;
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
      "INSERT INTO metadata (client_id, filename, size, hash, original_path, "
      "received_at, mime_type) VALUES (?, ?, ?, ?, ?, ?, 'image/jpeg')";

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
  const char *sql = "SELECT COUNT(*) FROM metadata WHERE hash = ?";

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
  const char *sql = "SELECT COUNT(*) FROM metadata WHERE client_id = ?";

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

std::vector<std::string>
DatabaseManager::batchCheckHashes(const std::vector<std::string> &hashes) {
  std::vector<std::string> foundHashes;
  if (hashes.empty()) {
    return foundHashes;
  }

  // Build query: SELECT hash FROM metadata WHERE hash IN (?, ?, ?)
  std::string sql = "SELECT hash FROM metadata WHERE hash IN (";
  for (size_t i = 0; i < hashes.size(); ++i) {
    sql += (i == 0 ? "?" : ", ?");
  }
  sql += ")";

  sqlite3_stmt *stmt;
  if (sqlite3_prepare_v2(db_, sql.c_str(), -1, &stmt, nullptr) != SQLITE_OK) {
    LOG_ERROR("Failed to prepare batchCheckHashes: " +
              std::string(sqlite3_errmsg(db_)));
    return foundHashes;
  }

  for (size_t i = 0; i < hashes.size(); ++i) {
    sqlite3_bind_text(stmt, static_cast<int>(i + 1), hashes[i].c_str(), -1,
                      SQLITE_TRANSIENT);
  }

  while (sqlite3_step(stmt) == SQLITE_ROW) {
    foundHashes.push_back(
        reinterpret_cast<const char *>(sqlite3_column_text(stmt, 0)));
  }

  sqlite3_finalize(stmt);
  return foundHashes;
}

// API Statistics Methods
int DatabaseManager::getTotalPhotoCount() {
  const char *sql = "SELECT COUNT(*) FROM metadata;";
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
  const char *sql = "SELECT SUM(size) FROM metadata;";
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
// Authentication Methods Implementation
// Append this to the end of DatabaseManager.cpp

// Authentication operations
bool DatabaseManager::createAdminUser(const std::string &username,
                                      const std::string &passwordHash) {
  sqlite3_stmt *stmt;
  const char *sql =
      "INSERT INTO admin_users (username, password_hash) VALUES (?, ?)";

  if (sqlite3_prepare_v2(db_, sql, -1, &stmt, nullptr) != SQLITE_OK) {
    LOG_ERROR("Failed to prepare createAdminUser: " +
              std::string(sqlite3_errmsg(db_)));
    return false;
  }

  sqlite3_bind_text(stmt, 1, username.c_str(), -1, SQLITE_TRANSIENT);
  sqlite3_bind_text(stmt, 2, passwordHash.c_str(), -1, SQLITE_TRANSIENT);

  bool success = (sqlite3_step(stmt) == SQLITE_DONE);
  sqlite3_finalize(stmt);

  if (success) {
    LOG_INFO("Created admin user: " + username);
  } else {
    LOG_ERROR("Failed to create admin user: " +
              std::string(sqlite3_errmsg(db_)));
  }

  return success;
}

AdminUser DatabaseManager::getAdminUserByUsername(const std::string &username) {
  AdminUser user;
  user.id = -1; // Indicates not found

  sqlite3_stmt *stmt;
  const char *sql = "SELECT id, username, password_hash, created_at, "
                    "last_login, is_active FROM admin_users WHERE username = ?";

  if (sqlite3_prepare_v2(db_, sql, -1, &stmt, nullptr) != SQLITE_OK) {
    LOG_ERROR("Failed to prepare getAdminUserByUsername: " +
              std::string(sqlite3_errmsg(db_)));
    return user;
  }

  sqlite3_bind_text(stmt, 1, username.c_str(), -1, SQLITE_TRANSIENT);

  if (sqlite3_step(stmt) == SQLITE_ROW) {
    user.id = sqlite3_column_int(stmt, 0);
    user.username =
        reinterpret_cast<const char *>(sqlite3_column_text(stmt, 1));
    user.passwordHash =
        reinterpret_cast<const char *>(sqlite3_column_text(stmt, 2));

    const char *createdAt =
        reinterpret_cast<const char *>(sqlite3_column_text(stmt, 3));
    user.createdAt = createdAt ? createdAt : "";

    const char *lastLogin =
        reinterpret_cast<const char *>(sqlite3_column_text(stmt, 4));
    user.lastLogin = lastLogin ? lastLogin : "";

    user.isActive = sqlite3_column_int(stmt, 5) != 0;
  }

  sqlite3_finalize(stmt);
  return user;
}

bool DatabaseManager::createAuthSession(const std::string &sessionToken,
                                        int userId,
                                        const std::string &expiresAt,
                                        const std::string &ipAddress) {
  sqlite3_stmt *stmt;
  const char *sql = "INSERT INTO sessions (session_token, user_id, expires_at, "
                    "ip_address) VALUES (?, ?, ?, ?)";

  if (sqlite3_prepare_v2(db_, sql, -1, &stmt, nullptr) != SQLITE_OK) {
    LOG_ERROR("Failed to prepare createAuthSession: " +
              std::string(sqlite3_errmsg(db_)));
    return false;
  }

  sqlite3_bind_text(stmt, 1, sessionToken.c_str(), -1, SQLITE_TRANSIENT);
  sqlite3_bind_int(stmt, 2, userId);
  sqlite3_bind_text(stmt, 3, expiresAt.c_str(), -1, SQLITE_TRANSIENT);
  sqlite3_bind_text(stmt, 4, ipAddress.c_str(), -1, SQLITE_TRANSIENT);

  bool success = (sqlite3_step(stmt) == SQLITE_DONE);
  sqlite3_finalize(stmt);

  if (success) {
    LOG_INFO("Created auth session for user ID: " + std::to_string(userId));

    // Update last_login timestamp
    const char *updateSql =
        "UPDATE admin_users SET last_login = CURRENT_TIMESTAMP WHERE id = ?";
    if (sqlite3_prepare_v2(db_, updateSql, -1, &stmt, nullptr) == SQLITE_OK) {
      sqlite3_bind_int(stmt, 1, userId);
      sqlite3_step(stmt);
      sqlite3_finalize(stmt);
    }
  } else {
    LOG_ERROR("Failed to create auth session: " +
              std::string(sqlite3_errmsg(db_)));
  }

  return success;
}

AuthSession
DatabaseManager::getSessionByToken(const std::string &sessionToken) {
  AuthSession session;
  session.id = -1; // Indicates not found

  sqlite3_stmt *stmt;
  const char *sql =
      "SELECT id, session_token, user_id, created_at, expires_at, ip_address "
      "FROM sessions WHERE session_token = ?";

  if (sqlite3_prepare_v2(db_, sql, -1, &stmt, nullptr) != SQLITE_OK) {
    LOG_ERROR("Failed to prepare getSessionByToken: " +
              std::string(sqlite3_errmsg(db_)));
    return session;
  }

  sqlite3_bind_text(stmt, 1, sessionToken.c_str(), -1, SQLITE_TRANSIENT);

  if (sqlite3_step(stmt) == SQLITE_ROW) {
    session.id = sqlite3_column_int(stmt, 0);
    session.sessionToken =
        reinterpret_cast<const char *>(sqlite3_column_text(stmt, 1));
    session.userId = sqlite3_column_int(stmt, 2);

    const char *createdAt =
        reinterpret_cast<const char *>(sqlite3_column_text(stmt, 3));
    session.createdAt = createdAt ? createdAt : "";

    const char *expiresAt =
        reinterpret_cast<const char *>(sqlite3_column_text(stmt, 4));
    session.expiresAt = expiresAt ? expiresAt : "";

    const char *ipAddress =
        reinterpret_cast<const char *>(sqlite3_column_text(stmt, 5));
    session.ipAddress = ipAddress ? ipAddress : "";
  }

  sqlite3_finalize(stmt);
  return session;
}

bool DatabaseManager::deleteSession(const std::string &sessionToken) {
  sqlite3_stmt *stmt;
  const char *sql = "DELETE FROM sessions WHERE session_token = ?";

  if (sqlite3_prepare_v2(db_, sql, -1, &stmt, nullptr) != SQLITE_OK) {
    LOG_ERROR("Failed to prepare deleteSession: " +
              std::string(sqlite3_errmsg(db_)));
    return false;
  }

  sqlite3_bind_text(stmt, 1, sessionToken.c_str(), -1, SQLITE_TRANSIENT);

  bool success = (sqlite3_step(stmt) == SQLITE_DONE);
  sqlite3_finalize(stmt);

  if (success) {
    LOG_INFO("Deleted session: " + sessionToken.substr(0, 16) + "...");
  }

  return success;
}

int DatabaseManager::cleanupExpiredSessions() {
  sqlite3_stmt *stmt;
  const char *sql = "DELETE FROM sessions WHERE expires_at < datetime('now')";

  if (sqlite3_prepare_v2(db_, sql, -1, &stmt, nullptr) != SQLITE_OK) {
    LOG_ERROR("Failed to prepare cleanupExpiredSessions: " +
              std::string(sqlite3_errmsg(db_)));
    return 0;
  }

  sqlite3_step(stmt);
  int deletedCount = sqlite3_changes(db_);
  sqlite3_finalize(stmt);

  if (deletedCount > 0) {
    LOG_INFO("Cleaned up " + std::to_string(deletedCount) +
             " expired sessions");
  }

  return deletedCount;
}

bool DatabaseManager::insertInitialAdminUser() {
  // Check if any admin users exist
  sqlite3_stmt *stmt;
  const char *checkSql = "SELECT COUNT(*) FROM admin_users";

  if (sqlite3_prepare_v2(db_, checkSql, -1, &stmt, nullptr) != SQLITE_OK) {
    return false;
  }

  int count = 0;
  if (sqlite3_step(stmt) == SQLITE_ROW) {
    count = sqlite3_column_int(stmt, 0);
  }
  sqlite3_finalize(stmt);

  // If admin users already exist, don't create another
  if (count > 0) {
    return true;
  }

// Create default admin user with password "admin123"
// Hash generated with PBKDF2, cost=12
// This is just a placeholder - in production, this should be changed
// immediately
#include "AuthenticationManager.h"
  std::string defaultPasswordHash =
      AuthenticationManager::hashPassword("admin123", 12);

  bool success = createAdminUser("admin", defaultPasswordHash);

  if (success) {
    LOG_INFO(
        "Created initial admin user (username: admin, password: admin123)");
    LOG_INFO("IMPORTANT: Change the default password immediately!");
  }

  return success;
}

// Password reset operations

bool DatabaseManager::createPasswordResetToken(const std::string &username,
                                               const std::string &token,
                                               const std::string &expiresAt) {
  const char *sql = R"(
        INSERT INTO password_reset_tokens (username, token, expires_at)
        VALUES (?, ?, ?);
    )";

  sqlite3_stmt *stmt;
  if (sqlite3_prepare_v2(db_, sql, -1, &stmt, nullptr) != SQLITE_OK) {
    LOG_ERROR("Failed to prepare createPasswordResetToken statement");
    return false;
  }

  sqlite3_bind_text(stmt, 1, username.c_str(), -1, SQLITE_TRANSIENT);
  sqlite3_bind_text(stmt, 2, token.c_str(), -1, SQLITE_TRANSIENT);
  sqlite3_bind_text(stmt, 3, expiresAt.c_str(), -1, SQLITE_TRANSIENT);

  bool success = (sqlite3_step(stmt) == SQLITE_DONE);
  sqlite3_finalize(stmt);

  if (success) {
    LOG_INFO("Created password reset token for user: " + username);
  }

  return success;
}

bool DatabaseManager::validatePasswordResetToken(const std::string &token) {
  const char *sql = R"(
        SELECT id FROM password_reset_tokens
        WHERE token = ? AND used = 0 AND expires_at > datetime('now');
    )";

  sqlite3_stmt *stmt;
  if (sqlite3_prepare_v2(db_, sql, -1, &stmt, nullptr) != SQLITE_OK) {
    return false;
  }

  sqlite3_bind_text(stmt, 1, token.c_str(), -1, SQLITE_TRANSIENT);

  bool valid = (sqlite3_step(stmt) == SQLITE_ROW);
  sqlite3_finalize(stmt);

  return valid;
}

std::string
DatabaseManager::getUsernameFromResetToken(const std::string &token) {
  const char *sql = R"(
        SELECT username FROM password_reset_tokens
        WHERE token = ? AND used = 0 AND expires_at > datetime('now');
    )";

  sqlite3_stmt *stmt;
  if (sqlite3_prepare_v2(db_, sql, -1, &stmt, nullptr) != SQLITE_OK) {
    return "";
  }

  sqlite3_bind_text(stmt, 1, token.c_str(), -1, SQLITE_TRANSIENT);

  std::string username;
  if (sqlite3_step(stmt) == SQLITE_ROW) {
    const char *usernameStr =
        reinterpret_cast<const char *>(sqlite3_column_text(stmt, 0));
    if (usernameStr) {
      username = usernameStr;
    }
  }

  sqlite3_finalize(stmt);
  return username;
}

bool DatabaseManager::resetPassword(const std::string &token,
                                    const std::string &newPasswordHash) {
  // Get username from token
  std::string username = getUsernameFromResetToken(token);
  if (username.empty()) {
    return false;
  }

  // Start transaction
  executeSQL("BEGIN TRANSACTION;");

  // Update password
  const char *updatePasswordSql = R"(
        UPDATE admin_users SET password_hash = ? WHERE username = ?;
    )";

  sqlite3_stmt *stmt;
  if (sqlite3_prepare_v2(db_, updatePasswordSql, -1, &stmt, nullptr) !=
      SQLITE_OK) {
    executeSQL("ROLLBACK;");
    return false;
  }

  sqlite3_bind_text(stmt, 1, newPasswordHash.c_str(), -1, SQLITE_TRANSIENT);
  sqlite3_bind_text(stmt, 2, username.c_str(), -1, SQLITE_TRANSIENT);

  bool passwordUpdated = (sqlite3_step(stmt) == SQLITE_DONE);
  sqlite3_finalize(stmt);

  if (!passwordUpdated) {
    executeSQL("ROLLBACK;");
    return false;
  }

  // Mark token as used
  const char *markUsedSql = R"(
        UPDATE password_reset_tokens SET used = 1 WHERE token = ?;
    )";

  if (sqlite3_prepare_v2(db_, markUsedSql, -1, &stmt, nullptr) != SQLITE_OK) {
    executeSQL("ROLLBACK;");
    return false;
  }

  sqlite3_bind_text(stmt, 1, token.c_str(), -1, SQLITE_TRANSIENT);

  bool tokenMarked = (sqlite3_step(stmt) == SQLITE_DONE);
  sqlite3_finalize(stmt);

  if (!tokenMarked) {
    executeSQL("ROLLBACK;");
    return false;
  }

  // Commit transaction
  executeSQL("COMMIT;");

  LOG_INFO("Password reset successful for user: " + username);
  return true;
}

int DatabaseManager::cleanupExpiredResetTokens() {
  const char *sql = R"(
        DELETE FROM password_reset_tokens WHERE expires_at < datetime('now');
    )";

  if (!executeSQL(sql)) {
    return 0;
  }

  return sqlite3_changes(db_);
}

// Media grid operations

std::vector<PhotoMetadata> DatabaseManager::getPhotosWithPagination(
    int offset, int limit, int clientId, const std::string &startDate,
    const std::string &endDate, const std::string &searchQuery) {

  std::vector<PhotoMetadata> photos;

  // Build query with optional filters
  std::string sql = R"(
    SELECT id, filename, hash, size, original_path
    FROM metadata
    WHERE 1=1
  )";

  if (clientId >= 0) {
    sql += " AND client_id = " + std::to_string(clientId);
  }

  if (!startDate.empty()) {
    sql += " AND received_at >= '" + startDate + "'";
  }

  if (!endDate.empty()) {
    sql += " AND received_at <= '" + endDate + "'";
  }

  if (!searchQuery.empty()) {
    sql += " AND filename LIKE ?";
  }

  sql += " ORDER BY id DESC LIMIT " + std::to_string(limit) + " OFFSET " +
         std::to_string(offset);

  sqlite3_stmt *stmt;
  if (sqlite3_prepare_v2(db_, sql.c_str(), -1, &stmt, nullptr) != SQLITE_OK) {
    LOG_ERROR("Failed to prepare getPhotosWithPagination statement: " +
              std::string(sqlite3_errmsg(db_)));
    return photos;
  }

  if (!searchQuery.empty()) {
    std::string likeQuery = "%" + searchQuery + "%";
    sqlite3_bind_text(stmt, 1, likeQuery.c_str(), -1, SQLITE_TRANSIENT);
  }

  while (sqlite3_step(stmt) == SQLITE_ROW) {
    PhotoMetadata photo;
    photo.id = sqlite3_column_int(stmt, 0);

    const char *filename =
        reinterpret_cast<const char *>(sqlite3_column_text(stmt, 1));
    if (filename)
      photo.filename = filename;

    const char *hash =
        reinterpret_cast<const char *>(sqlite3_column_text(stmt, 2));
    if (hash)
      photo.hash = hash;

    photo.size = sqlite3_column_int64(stmt, 3);

    const char *originalPath =
        reinterpret_cast<const char *>(sqlite3_column_text(stmt, 4));
    if (originalPath)
      photo.originalPath = originalPath;
    else
      photo.originalPath = "./storage/photos/" + photo.filename; // Fallback
    photo.mimeType = "image/jpeg"; // Default, could be enhanced
    photo.width = 0;
    photo.height = 0;
    photo.takenAt = "";
    photo.receivedAt = "";
    photo.clientId = clientId >= 0 ? clientId : 0;

    photos.push_back(photo);
  }

  sqlite3_finalize(stmt);
  return photos;
}

int DatabaseManager::getFilteredPhotoCount(int clientId,
                                           const std::string &startDate,
                                           const std::string &endDate,
                                           const std::string &searchQuery) {
  std::string sql = "SELECT COUNT(*) FROM metadata WHERE 1=1";

  if (clientId >= 0) {
    sql += " AND client_id = " + std::to_string(clientId);
  }

  if (!startDate.empty()) {
    sql += " AND received_at >= '" + startDate + "'";
  }

  if (!endDate.empty()) {
    sql += " AND received_at <= '" + endDate + "'";
  }

  if (!searchQuery.empty()) {
    sql += " AND filename LIKE ?";
  }

  sqlite3_stmt *stmt;
  if (sqlite3_prepare_v2(db_, sql.c_str(), -1, &stmt, nullptr) != SQLITE_OK) {
    LOG_ERROR("Failed to prepare getFilteredPhotoCount statement: " +
              std::string(sqlite3_errmsg(db_)));
    return 0;
  }

  if (!searchQuery.empty()) {
    std::string likeQuery = "%" + searchQuery + "%";
    sqlite3_bind_text(stmt, 1, likeQuery.c_str(), -1, SQLITE_TRANSIENT);
  }

  int count = 0;
  if (sqlite3_step(stmt) == SQLITE_ROW) {
    count = sqlite3_column_int(stmt, 0);
  }

  sqlite3_finalize(stmt);
  return count;
}

PhotoMetadata DatabaseManager::getPhotoById(int photoId) {
  PhotoMetadata photo;
  photo.id = -1; // Indicate not found

  const char *sql = R"(
    SELECT id, filename, hash, size, original_path
    FROM metadata
    WHERE id = ?
  )";

  sqlite3_stmt *stmt;
  if (sqlite3_prepare_v2(db_, sql, -1, &stmt, nullptr) != SQLITE_OK) {
    LOG_ERROR("Failed to prepare getPhotoById statement: " +
              std::string(sqlite3_errmsg(db_)));
    return photo;
  }

  sqlite3_bind_int(stmt, 1, photoId);

  if (sqlite3_step(stmt) == SQLITE_ROW) {
    photo.id = sqlite3_column_int(stmt, 0);

    const char *filename =
        reinterpret_cast<const char *>(sqlite3_column_text(stmt, 1));
    if (filename)
      photo.filename = filename;

    const char *hash =
        reinterpret_cast<const char *>(sqlite3_column_text(stmt, 2));
    if (hash)
      photo.hash = hash;

    photo.size = sqlite3_column_int64(stmt, 3);

    const char *originalPath =
        reinterpret_cast<const char *>(sqlite3_column_text(stmt, 4));
    if (originalPath)
      photo.originalPath = originalPath;
    else
      photo.originalPath = "./storage/photos/" + photo.filename; // Fallback
    photo.mimeType = "image/jpeg";
    photo.width = 0;
    photo.height = 0;
    photo.takenAt = "";
    photo.receivedAt = "";
    photo.clientId = 0;
  }

  sqlite3_finalize(stmt);
  return photo;
}

bool DatabaseManager::migratePhotosToMetadata() {
  // Check if photos table exists
  sqlite3_stmt *stmt;
  const char *checkSql =
      "SELECT name FROM sqlite_master WHERE type='table' AND name='photos'";
  if (sqlite3_prepare_v2(db_, checkSql, -1, &stmt, nullptr) != SQLITE_OK) {
    return false;
  }

  bool exists = false;
  if (sqlite3_step(stmt) == SQLITE_ROW) {
    exists = true;
  }
  sqlite3_finalize(stmt);

  if (!exists) {
    return true; // Nothing to migrate
  }

  LOG_INFO("Migrating photos from 'photos' table to 'metadata' table...");

  // Begin transaction
  if (!executeSQL("BEGIN TRANSACTION;")) {
    return false;
  }

  // Copy data
  // Map columns:
  // photos: client_id, filename, size, hash, file_path, received_at
  // metadata: client_id, filename, size, hash, original_path, received_at,
  // mime_type
  const char *copySql = R"(
    INSERT OR IGNORE INTO metadata (client_id, filename, size, hash, original_path, received_at, mime_type)
    SELECT client_id, filename, size, hash, file_path, received_at, 'image/jpeg'
    FROM photos;
  )";

  if (!executeSQL(copySql)) {
    LOG_ERROR("Failed to migrate photos data");
    executeSQL("ROLLBACK;");
    return false;
  }

  // Drop old table
  if (!executeSQL("DROP TABLE photos;")) {
    LOG_ERROR("Failed to drop old photos table");
    executeSQL("ROLLBACK;");
    return false;
  }

  executeSQL("COMMIT;");
  LOG_INFO("Migration complete. 'photos' table removed.");
  return true;
}

// Log operations implementation

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

  // Get storage usage specially (Simplified, removed StorageStats usage)
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

std::string DatabaseManager::generatePairingToken() {
  std::random_device rd;
  std::mt19937 gen(rd());
  std::uniform_int_distribution<> distrib(100000, 999999);
  std::string token = std::to_string(distrib(gen));

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
      token = "";
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
  sqlite3_stmt *stmt;
  if (sqlite3_prepare_v2(db_, sql, -1, &stmt, nullptr) == SQLITE_OK) {
    sqlite3_bind_text(stmt, 1, token.c_str(), -1, SQLITE_STATIC);
    bool success = (sqlite3_step(stmt) == SQLITE_DONE);
    sqlite3_finalize(stmt);
    return success;
  }
  return false;
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

bool DatabaseManager::deleteClient(int clientId) {
  const char *sql = "DELETE FROM clients WHERE id = ?;";
  sqlite3_stmt *stmt;
  bool success = false;

  if (sqlite3_prepare_v2(db_, sql, -1, &stmt, nullptr) == SQLITE_OK) {
    sqlite3_bind_int(stmt, 1, clientId);
    if (sqlite3_step(stmt) == SQLITE_DONE) {
      success = true;
    }
    sqlite3_finalize(stmt);
  } else {
    LOG_ERROR("Failed to prepare deleteClient: " +
              std::string(sqlite3_errmsg(db_)));
  }

  // Also clean up any metadata/sessions if not handled by FK cascade?
  // Build schema usually has ON DELETE CASCADE if setup right, but if not:
  // "DELETE FROM sessions WHERE client_id = ?"
  // "DELETE FROM metadata WHERE client_id = ?"
  // Let's rely on basic delete for now or add them if requested.
  // The user requirement is just "remove... from UI and Database".

  // Let's add explicit cleanup for safety
  if (success) {
    const char *sqls[] = {"DELETE FROM sessions WHERE client_id = ?;",
                          "DELETE FROM metadata WHERE client_id = ?;"};

    for (const char *s : sqls) {
      sqlite3_stmt *subStmt;
      if (sqlite3_prepare_v2(db_, s, -1, &subStmt, nullptr) == SQLITE_OK) {
        sqlite3_bind_int(subStmt, 1, clientId);
        sqlite3_step(subStmt);
        sqlite3_finalize(subStmt);
      }
    }
  }

  return success;
}
