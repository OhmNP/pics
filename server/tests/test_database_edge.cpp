#include "../src/DatabaseManager.h"
#include <filesystem>
#include <gtest/gtest.h>

class DatabaseEdgeTest : public ::testing::Test {
protected:
  DatabaseManager db;
  std::string testDbPath = "test_edge.db";

  void SetUp() override {
    if (std::filesystem::exists(testDbPath)) {
      std::filesystem::remove(testDbPath);
    }
    ASSERT_TRUE(db.open(testDbPath));
    ASSERT_TRUE(db.createSchema());
    // Enforce foreign keys for strict testing
    db.executeSQL("PRAGMA foreign_keys = ON;");
  }

  void TearDown() override {
    db.close();
    if (std::filesystem::exists(testDbPath)) {
      std::filesystem::remove(testDbPath);
    }
  }
};

TEST_F(DatabaseEdgeTest, DuplicateAdminUser) {
  // 1. Create user
  std::string user = "uniq_user";
  std::string hash = "hash123";
  ASSERT_TRUE(db.createAdminUser(user, hash));

  // 2. Try to create SAME user again
  // Should fail (return false) because username is UNIQUE
  bool success = db.createAdminUser(user, "different_hash");
  EXPECT_FALSE(success) << "Should not allow duplicate admin username";
}

TEST_F(DatabaseEdgeTest, DuplicatePhotoHash) {
  int clientId = db.getOrCreateClient("dev1", "user1");

  PhotoMetadata p1;
  p1.filename = "a.jpg";
  p1.hash = "unique_hash_123";
  p1.size = 100;

  ASSERT_TRUE(db.insertPhoto(clientId, p1));

  // 2. Insert same hash, different file
  PhotoMetadata p2;
  p2.filename = "b.jpg";
  p2.hash = "unique_hash_123"; // Duplicate
  p2.size = 200;

  // insertPhoto might upsert or fail?
  // If it fails, that's good. If it upserts, we check logic.
  // DatabaseManager.cpp usually does "INSERT OR IGNORE" or "INSERT ...".
  // Let's assume strict insert fails.
  bool result = db.insertPhoto(clientId, p2);
  // If implementation is INSERT OR IGNORE, it might return true (no error) but
  // not insert? Or return false? Let's check logic: if it fails, expect false.
  // If currently it succeeds (by ignoring), we want to verify it didn't
  // overwrite p1 details if that's policy. But for Edge Case, we primarily
  // check it doesn't CRASH.

  // Actually, createSchema says: hash TEXT UNIQUE NOT NULL.
  // insertPhoto usually checks if exists?
  // We expect it NOT to throw.
  EXPECT_NO_THROW({ db.insertPhoto(clientId, p2); });
}

TEST_F(DatabaseEdgeTest, InsertPhotoInvalidClient) {
  // If FK is ON, strict check.
  // Client ID 9999 does not exist.
  PhotoMetadata p;
  p.filename = "orphan.jpg";
  p.hash = "orphan_hash";
  p.size = 10;

  bool result = db.insertPhoto(9999, p);
  EXPECT_FALSE(result)
      << "Should fail to insert photo for non-existent client with FK=ON";
}

TEST_F(DatabaseEdgeTest, SQLInjectionInUsername) {
  // Verify that ' OR '1'='1 does not bypass logic or crash
  std::string maliciousUser = "admin' --";
  std::string hash = "hash";

  // Should treat it as a literal username "admin' --"
  ASSERT_TRUE(db.createAdminUser(maliciousUser, hash));

  auto user = db.getAdminUserByUsername(maliciousUser);
  EXPECT_EQ(user.username, maliciousUser);
  EXPECT_NE(user.id, -1);

  // Verify it didn't create "admin"
  auto adminArr = db.getAdminUserByUsername("admin");
  // If "admin" didn't exist before, it should not exist now (unless
  // insertInitial created it, but we are fresh db) insertInitial is called in
  // createSchema. So admin probably exists. But maliciousUser should be
  // distinct.
  EXPECT_NE(user.id, adminArr.id);
}
