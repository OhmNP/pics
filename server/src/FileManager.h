#pragma once

#include <atomic>
#include <filesystem>
#include <mutex>
#include <string>
#include <vector>

#include "DatabaseManager.h"

struct UploadProgress {
  std::string tempFilePath;
  long long bytesReceived;
  long long totalBytes;
  std::string hash;
  std::string filename;
};

class FileManager {
public:
  FileManager(const std::string &storageDir, long long maxStorageBytes);
  ~FileManager();

  // Initialize storage directories
  bool initialize();

  // Check if photo already exists
  bool photoExists(const std::string &hash);

  // Start a new upload (for resumable uploads)
  bool startUpload(const PhotoMetadata &metadata, std::string &outTempPath);

  // Resume an existing upload
  bool resumeUpload(const std::string &hash, UploadProgress &outProgress);

  // Write chunk of data
  bool writeChunk(const std::string &tempPath, const std::vector<char> &data,
                  long long offset);

  // Finalize upload (verify hash and move to final location)
  bool finalizeUpload(const std::string &tempPath,
                      const PhotoMetadata &metadata, std::string &outFinalPath);

  // Cancel upload and cleanup
  void cancelUpload(const std::string &tempPath);

  // Check if storage quota allows this upload
  bool hasSpaceAvailable(long long requiredBytes);

  // Get current storage usage
  long long getTotalStorageUsed();

  // Calculate SHA-256 hash of file
  static std::string calculateSHA256(const std::string &filePath);
  static std::string calculateSHA256(const std::vector<char> &data);

  // Delete photo file
  bool deletePhoto(const std::string &hash);

  // Get file path for a hash
  std::string getPhotoPath(const std::string &hash,
                           const std::string &extension);

private:
  std::string storageDir_;
  long long maxStorageBytes_;
  std::atomic<long long> currentStorageUsed_;
  std::mutex fileMutex_;

  std::string getTempDir();
  std::string getPhotosDir();
  std::string generatePhotoPath(const PhotoMetadata &metadata);
  bool ensureDirectoryExists(const std::string &path);
  bool verifyHash(const std::string &filePath, const std::string &expectedHash);
  void updateStorageUsed(long long delta);
  long long calculateDirectorySize(const std::string &path);
};
