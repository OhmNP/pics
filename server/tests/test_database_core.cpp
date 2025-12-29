#include "DatabaseManager.h"
#include <filesystem>
#include <gtest/gtest.h>


// Test fixture for database core feature tests
class DatabaseCoreTest : public ::testing::Test {
protected:
  DatabaseManager db;
  std::string testDbPath = "test_core.db";

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

TEST_F(DatabaseCoreTest, ClientOperations) {
  // Test creation
  int id1 = db.getOrCreateClient("device_123", "User A");
  ASSERT_GT(id1, 0);

  // Test retrieval (idempotency)
  int id2 = db.getOrCreateClient("device_123", "User Changed");
  EXPECT_EQ(id1, id2);

  // Test details
  auto client = db.getClientDetails(id1);
  EXPECT_EQ(client.deviceId, "device_123");
  // Depending on implementation, user_name might update or not.
  // Implementation says: "If client exists, update user_name if provided"
  EXPECT_EQ(client.userName, "User Changed");
}

TEST_F(DatabaseCoreTest, SessionOperations) {
  int clientId = db.getOrCreateClient("device_session", "Session User");

  int sessionId = db.createSession(clientId);
  ASSERT_GT(sessionId, 0);

  db.updateSessionPhotosReceived(sessionId, 5);
  db.finalizeSession(sessionId, "completed");

  auto sessions = db.getSessions(0, 10, clientId);
  ASSERT_FALSE(sessions.empty());
  EXPECT_EQ(sessions[0].photosReceived, 5);
  EXPECT_EQ(sessions[0].status, "completed");
}

TEST_F(DatabaseCoreTest, PhotoInsertionWithExif) {
  int clientId = db.getOrCreateClient("device_exif", "Exif User");

  PhotoMetadata photo;
  photo.filename = "test_exif.jpg";
  photo.hash = "hash_exif_123";
  photo.size = 2048;
  photo.mimeType = "image/jpeg";

  // EXIF Data
  photo.cameraMake = "Canon";
  photo.cameraModel = "EOS R5";
  photo.exposureTime = 0.005; // 1/200
  photo.fNumber = 2.8;
  photo.iso = 800;
  photo.focalLength = 50.0;
  photo.gpsLat = 37.7749;
  photo.gpsLon = -122.4194;
  photo.gpsAlt = 100.5;

  // Insert
  ASSERT_TRUE(db.insertPhoto(clientId, photo));

  // Verify retrieval via pagination
  auto photos = db.getPhotosWithPagination(0, 10, clientId);
  ASSERT_EQ(photos.size(), 1);

  const auto &retrieved = photos[0];
  EXPECT_EQ(retrieved.filename, "test_exif.jpg");
  EXPECT_EQ(retrieved.cameraMake, "Canon");
  EXPECT_EQ(retrieved.cameraModel, "EOS R5");
  EXPECT_DOUBLE_EQ(retrieved.exposureTime, 0.005);
  EXPECT_DOUBLE_EQ(retrieved.fNumber, 2.8);
  EXPECT_EQ(retrieved.iso, 800);
  EXPECT_DOUBLE_EQ(retrieved.focalLength, 50.0);
  EXPECT_DOUBLE_EQ(retrieved.gpsLat, 37.7749);
  EXPECT_DOUBLE_EQ(retrieved.gpsLon, -122.4194);

  // Verify retrieval by ID
  auto photoById = db.getPhotoById(retrieved.id);
  EXPECT_EQ(photoById.id, retrieved.id);
  EXPECT_EQ(photoById.cameraModel, "EOS R5");
}

TEST_F(DatabaseCoreTest, FilteredPhotoCount) {
  int clientId = db.getOrCreateClient("device_count", "Count User");

  PhotoMetadata p1;
  p1.filename = "trip_paris_1.jpg";
  p1.hash = "h1";
  p1.size = 100;
  db.insertPhoto(clientId, p1);

  PhotoMetadata p2;
  p2.filename = "trip_paris_2.jpg";
  p2.hash = "h2";
  p2.size = 100;
  db.insertPhoto(clientId, p2);

  PhotoMetadata p3;
  p3.filename = "home_sunday.jpg";
  p3.hash = "h3";
  p3.size = 100;
  db.insertPhoto(clientId, p3);

  // Search query for "paris"
  int countParis = db.getFilteredPhotoCount(clientId, "", "", "paris");
  EXPECT_EQ(countParis, 2);

  int countAll = db.getFilteredPhotoCount(clientId);
  EXPECT_EQ(countAll, 3);
}
