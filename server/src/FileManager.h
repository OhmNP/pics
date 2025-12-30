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
  FileManager(const std::string &photosDir, const std::string &tempDir,
              long long maxStorageBytes);
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

  // Check physical disk space
  bool checkDiskSpace(long long requiredBytes);

  // Get current storage usage
  long long getTotalStorageUsed();

  // Calculate SHA-256 hash of file
  static std::string calculateSHA256(const std::string &filePath);
  static std::string calculateSHA256(const std::vector<char> &data);

  // Delete photo file
  bool deletePhoto(const std::string &hash);
  bool deleteUploadSessionFiles(const std::string &uploadId);
  void cleanupTempFolder(int maxAgeHours = 24);

  // Get file path for a hash
  std::string getPhotoPath(const std::string &hash,
                           const std::string &extension,
                           const std::string &timestamp = "");

  // Phase 2: Resumable Uploads
  std::string getUploadTempPath(const std::string &uploadId);
  bool appendChunk(const std::string &uploadId, const std::vector<char> &data);
  bool finalizeFile(const std::string &uploadId, const std::string &finalPath);
  long long getFileSize(const std::string &path); // For resume reconciliation
  std::string generatePhotoPath(const PhotoMetadata &metadata);

  // Phase 3: Integrity
  std::vector<std::string> getAllPhotoHashes(size_t limit = 0); // 0 = unlimited

private:
  std::string photosDir_;
  std::string tempDir_;
  long long maxStorageBytes_;
  std::atomic<long long> currentStorageUsed_;
  std::mutex fileMutex_;

  std::string getTempDir();
  std::string getPhotosDir();
  bool ensureDirectoryExists(const std::string &path);
  bool verifyHash(const std::string &filePath, const std::string &expectedHash);
  void updateStorageUsed(long long delta);
  long long calculateDirectorySize(const std::string &path);
};
