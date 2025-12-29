#include "../src/ConfigManager.h"
#include "../src/FileManager.h"
#include <filesystem>
#include <fstream>
#include <gtest/gtest.h>

namespace fs = std::filesystem;

class FileManagerTest : public ::testing::Test {
protected:
  std::string testRoot = "test_files_temp";
  std::string photosDir = testRoot + "/photos";
  std::string tempDir = testRoot + "/temp";
  std::string thumbsDir = testRoot + "/.thumbnails";

  void SetUp() override {
    // Ensure clean state
    if (fs::exists(testRoot)) {
      fs::remove_all(testRoot);
    }

    // Mock Config (Singleton override or manual setup?)
    // FileManager splits dirs based on Config, OR we can manually test its
    // helpers if public. Assuming FileManager uses ConfigManager singleton, we
    // might need to rely on its defaults or set it up. Let's create the
    // directories manually required by FileManager to ensure it works within
    // them, or check if FileManager::initialize creates them.

    fs::create_directories(photosDir);
    fs::create_directories(tempDir);
  }

  void TearDown() override {
    if (fs::exists(testRoot)) {
      fs::remove_all(testRoot);
    }
  }
};

// Helper to compute sha256 of string
std::string computeSHA256String(const std::string &str) {
  std::vector<char> data(str.begin(), str.end());
  return FileManager::calculateSHA256(data);
}

TEST_F(FileManagerTest, DirectoryInitialization) {
  // Instantiate FileManager
  FileManager fm(testRoot, 1024 * 1024 * 100); // 100MB limit
  EXPECT_TRUE(fm.initialize());

  // Check if dirs created
  EXPECT_TRUE(fs::exists(photosDir));
  EXPECT_TRUE(fs::exists(tempDir));
}

TEST_F(FileManagerTest, ComputeSHA256) {
  std::string content = "hello world";
  std::string filename = "hash_test.txt";
  std::string path = tempDir + "/" + filename;

  fs::create_directories(tempDir);
  std::ofstream out(path, std::ios::binary);
  out << content;
  out.close();

  // "hello world" sha256
  std::string expectedHash =
      "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9";

  std::string calculatedHash = FileManager::calculateSHA256(path);
  EXPECT_EQ(calculatedHash, expectedHash);
}

TEST_F(FileManagerTest, ChunkWriting) {
  FileManager fm(testRoot, 1024 * 1024);
  fm.initialize();

  std::string tempFile = "temp_upload.part";
  // Check startUpload logic if possible, or test writeChunk directly on a known
  // path writeChunk takes a tempPath which is usually absolute or relative to
  // run dir? FileManager implementation likely appends tempPath to storageDir_
  // or assumes absolute. Let's look at FileManager::getTempDir private method
  // implies it manages paths.

  // For writeChunk, let's pass a path inside our test temp dir
  std::string fullTempPath = tempDir + "/" + tempFile;

  // Create empty file first (required by writeChunk's in|out mode)
  {
    std::ofstream create(fullTempPath);
  }

  std::vector<char> data = {'H', 'e', 'l', 'l', 'o'};
  EXPECT_TRUE(fm.writeChunk(fullTempPath, data, 0));

  EXPECT_TRUE(fs::exists(fullTempPath));
  EXPECT_EQ(fs::file_size(fullTempPath), 5);
}

TEST_F(FileManagerTest, QuotaCheck) {
  FileManager fm(testRoot, 1000); // 1000 bytes limit
  fm.initialize();

  EXPECT_TRUE(fm.hasSpaceAvailable(500));
  EXPECT_FALSE(fm.hasSpaceAvailable(2000));
}
