#include "AuthenticationManager.h"
#include "DatabaseManager.h"
#include <filesystem>
#include <gtest/gtest.h>

// Test fixture for database authentication tests
class DatabaseAuthTest : public ::testing::Test {
protected:
  DatabaseManager db;
  std::string testDbPath = "test_auth.db";

  void SetUp() override {
    // Remove test database if it exists
    if (std::filesystem::exists(testDbPath)) {
      std::filesystem::remove(testDbPath);
    }

    // Open and create schema
    ASSERT_TRUE(db.open(testDbPath));
    ASSERT_TRUE(db.createSchema());
  }

  void TearDown() override {
    db.close();

    // Clean up test database
    if (std::filesystem::exists(testDbPath)) {
      std::filesystem::remove(testDbPath);
    }
  }
};

// Placeholder tests - will be implemented after DatabaseManager is updated
TEST_F(DatabaseAuthTest, CreateAdminUser) {
  // Hash for "password123" (simulated)
  std::string hash =
      "ef92b778bafe771e89245b89ecbc08a44a4e166c06659911881f383d4473e94f";
  ASSERT_TRUE(db.createAdminUser("test_admin", hash));

  // Verify user exists and is active
  AdminUser user = db.getAdminUserByUsername("test_admin");
  ASSERT_NE(user.id, -1);
  ASSERT_EQ(user.username, "test_admin");
  ASSERT_EQ(user.passwordHash, hash);
  ASSERT_TRUE(user.isActive);
}

TEST_F(DatabaseAuthTest, GetAdminUserByUsername) {
  std::string hash = "hash";
  db.createAdminUser("user1", hash);

  AdminUser found = db.getAdminUserByUsername("user1");
  ASSERT_EQ(found.username, "user1");

  AdminUser notFound = db.getAdminUserByUsername("non_existent");
  ASSERT_EQ(notFound.id, -1);
}

TEST_F(DatabaseAuthTest, CreateSession) {
  db.createAdminUser("session_user", "hash");
  AdminUser user = db.getAdminUserByUsername("session_user");

  std::string token = "test_token_123";
  std::string expires = "2099-01-01 12:00:00";

  ASSERT_TRUE(db.createAuthSession(token, user.id, expires, "127.0.0.1"));

  AuthSession session = db.getSessionByToken(token);
  ASSERT_NE(session.id, -1);
  ASSERT_EQ(session.sessionToken, token);
  ASSERT_EQ(session.userId, user.id);
  ASSERT_EQ(session.ipAddress, "127.0.0.1");
}

TEST_F(DatabaseAuthTest, GetSessionByToken) {
  db.createAdminUser("token_user", "hash");
  AdminUser user = db.getAdminUserByUsername("token_user");

  db.createAuthSession("valid_token", user.id, "2099-01-01 12:00:00");
  db.createAuthSession("expired_token", user.id, "2000-01-01 12:00:00");

  AuthSession valid = db.getSessionByToken("valid_token");
  ASSERT_NE(valid.id, -1);

  AuthSession expired = db.getSessionByToken("expired_token");
  ASSERT_EQ(expired.id, -1); // Should not return expired session

  AuthSession invalid = db.getSessionByToken("invalid_token");
  ASSERT_EQ(invalid.id, -1);
}

TEST_F(DatabaseAuthTest, DeleteSession) {
  db.createAdminUser("del_user", "hash");
  AdminUser user = db.getAdminUserByUsername("del_user");

  db.createAuthSession("del_token", user.id, "2099-01-01 12:00:00");
  ASSERT_NE(db.getSessionByToken("del_token").id, -1);

  ASSERT_TRUE(db.deleteSession("del_token"));
  ASSERT_EQ(db.getSessionByToken("del_token").id, -1);
}

TEST_F(DatabaseAuthTest, CleanupExpiredSessions) {
  db.createAdminUser("cleanup_user", "hash");
  AdminUser user = db.getAdminUserByUsername("cleanup_user");

  db.createAuthSession("valid_1", user.id, "2099-01-01 12:00:00");
  db.createAuthSession("expired_1", user.id, "2000-01-01 12:00:00");
  db.createAuthSession("expired_2", user.id, "2000-01-01 12:00:00");

  int deleted = db.cleanupExpiredSessions();
  ASSERT_EQ(deleted, 2);

  ASSERT_NE(db.getSessionByToken("valid_1").id, -1);
}
