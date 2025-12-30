#include "FileManager.h"
#include "Logger.h"
#include <chrono>
#include <filesystem>
#include <fstream>
#include <iomanip>
#include <openssl/sha.h>
#include <sstream>
#include <thread>
#include <vector>

FileManager::FileManager(const std::string &photosDir,
                         const std::string &tempDir, long long maxStorageBytes)
    : photosDir_(photosDir), tempDir_(tempDir),
      maxStorageBytes_(maxStorageBytes), currentStorageUsed_(0) {}

FileManager::~FileManager() {}

bool FileManager::initialize() {
  // Create directories if they don't exist
  if (!ensureDirectoryExists(photosDir_)) {
    LOG_ERROR("Failed to create photos directory: " + photosDir_);
    return false;
  }

  if (!ensureDirectoryExists(tempDir_)) {
    LOG_ERROR("Failed to create temp directory: " + tempDir_);
    return false;
  }

  // Calculate current storage usage
  currentStorageUsed_ = calculateDirectorySize(photosDir_);
  LOG_INFO("Storage initialized. Current usage: " +
           std::to_string(currentStorageUsed_) + " bytes");

  return true;
}

std::string FileManager::getTempDir() { return tempDir_; }

std::string FileManager::getPhotosDir() { return photosDir_; }

bool FileManager::ensureDirectoryExists(const std::string &path) {
  try {
    std::filesystem::create_directories(path);
    return true;
  } catch (const std::exception &e) {
    LOG_ERROR("Failed to create directory: " + path + " - " + e.what());
    return false;
  }
}

bool FileManager::photoExists(const std::string &hash) {
  // Check all possible extensions
  std::vector<std::string> extensions = {".jpg", ".jpeg", ".png",
                                         ".gif", ".heic", ".webp"};

  for (const auto &ext : extensions) {
    std::string path = getPhotoPath(hash, ext);
    if (std::filesystem::exists(path)) {
      return true;
    }
  }

  return false;
}

std::string FileManager::getPhotoPath(const std::string &hash,
                                      const std::string &extension,
                                      const std::string &timestamp) {
  std::tm tm = {};
  if (!timestamp.empty()) {
    // Parse ISO8601 or SQLite timestamp (YYYY-MM-DD HH:MM:SS)
    std::istringstream ss(timestamp);
    ss >> std::get_time(
              &tm, "%Y-%m-%d %H:%M:%S"); // Try space first (SQLite default)
    if (ss.fail()) {
      // Reset and try ISO8601 (T)
      ss.clear();
      ss.str(timestamp);
      ss >> std::get_time(&tm, "%Y-%m-%dT%H:%M:%S");

      if (ss.fail()) {
        LOG_WARN("Failed to parse timestamp: " + timestamp +
                 ". Using current time.");
        auto now = std::chrono::system_clock::now();
        auto time = std::chrono::system_clock::to_time_t(now);
        tm = *std::gmtime(&time);
      }
    }
  } else {
    // Use current time
    auto now = std::chrono::system_clock::now();
    auto time = std::chrono::system_clock::to_time_t(now);
    tm = *std::gmtime(&time);
  }

  std::stringstream path;
  path << getPhotosDir() << "/" << std::put_time(&tm, "%Y/%m") << "/" << hash
       << extension;

  return path.str();
}

bool FileManager::hasSpaceAvailable(long long requiredBytes) {
  long long projected = currentStorageUsed_ + requiredBytes;
  bool available = projected <= maxStorageBytes_;

  if (!available) {
    LOG_WARN("Storage quota would be exceeded. Current: " +
             std::to_string(currentStorageUsed_) +
             " Projected: " + std::to_string(projected) +
             " Max: " + std::to_string(maxStorageBytes_));
  }

  return available;
}

bool FileManager::checkDiskSpace(long long requiredBytes) {
  try {
    std::filesystem::space_info si = std::filesystem::space(photosDir_);
    // Keep at least 500MB free
    long long minFree = 500 * 1024 * 1024;
    return si.available > (requiredBytes + minFree);
  } catch (const std::exception &e) {
    LOG_ERROR("Failed to check disk space: " + std::string(e.what()));
    return false; // Fail safe
  }
}

long long FileManager::getTotalStorageUsed() { return currentStorageUsed_; }

bool FileManager::startUpload(const PhotoMetadata &metadata,
                              std::string &outTempPath) {
  std::lock_guard<std::mutex> lock(fileMutex_);

  // Check quota
  if (!hasSpaceAvailable(metadata.size)) {
    LOG_ERROR("Storage quota exceeded. Cannot start upload for: " +
              metadata.filename);
    return false;
  }

  // Check physical disk space
  if (!checkDiskSpace(metadata.size)) {
    LOG_ERROR("Physical disk space low. Cannot start upload for: " +
              metadata.filename);
    return false;
  }

  // Check if photo already exists
  if (photoExists(metadata.hash)) {
    LOG_INFO("Photo already exists: " + metadata.hash);
    return false;
  }

  // Generate temp file path
  outTempPath = getTempDir() + "/" + metadata.hash + ".tmp";

  // Create empty file
  std::ofstream file(outTempPath, std::ios::binary);
  if (!file) {
    LOG_ERROR("Failed to create temp file: " + outTempPath);
    return false;
  }
  file.close();

  LOG_INFO("Started upload: " + metadata.filename + " -> " + outTempPath);
  return true;
}

bool FileManager::resumeUpload(const std::string &hash,
                               UploadProgress &outProgress) {
  std::string tempPath = getTempDir() + "/" + hash + ".tmp";

  if (!std::filesystem::exists(tempPath)) {
    LOG_ERROR("Temp file not found for resume: " + tempPath);
    return false;
  }

  // Get current file size
  long long currentSize = std::filesystem::file_size(tempPath);

  outProgress.tempFilePath = tempPath;
  outProgress.bytesReceived = currentSize;
  outProgress.hash = hash;

  LOG_INFO("Resuming upload: " + hash + " from offset " +
           std::to_string(currentSize));
  return true;
}

bool FileManager::writeChunk(const std::string &tempPath,
                             const std::vector<char> &data, long long offset) {

  std::lock_guard<std::mutex> lock(fileMutex_);

  try {
    std::ofstream file(tempPath,
                       std::ios::binary | std::ios::in | std::ios::out);
    if (!file) {
      LOG_ERROR("Failed to open temp file for writing: " + tempPath);
      return false;
    }

    // Seek to offset
    file.seekp(offset);

    // Write data
    file.write(data.data(), data.size());
    file.close();

    return true;
  } catch (const std::exception &e) {
    LOG_ERROR("Error writing chunk: " + std::string(e.what()));
    return false;
  }
}

bool FileManager::verifyHash(const std::string &filePath,
                             const std::string &expectedHash) {
  std::string actualHash = calculateSHA256(filePath);

  if (actualHash != expectedHash) {
    LOG_ERROR("Hash mismatch! Expected: " + expectedHash +
              " Got: " + actualHash);
    return false;
  }

  return true;
}

std::string FileManager::calculateSHA256(const std::string &filePath) {
  std::ifstream file(filePath, std::ios::binary);
  if (!file) {
    LOG_ERROR("Failed to open file for hashing: " + filePath);
    return "";
  }

  SHA256_CTX sha256;
  SHA256_Init(&sha256);

  constexpr size_t bufferSize = 8192;
  char buffer[bufferSize];

  while (file.read(buffer, bufferSize) || file.gcount() > 0) {
    SHA256_Update(&sha256, buffer, file.gcount());
  }

  unsigned char hash[SHA256_DIGEST_LENGTH];
  SHA256_Final(hash, &sha256);

  std::stringstream ss;
  for (int i = 0; i < SHA256_DIGEST_LENGTH; i++) {
    ss << std::hex << std::setw(2) << std::setfill('0')
       << static_cast<int>(hash[i]);
  }

  file.close(); // Explicit close
  return ss.str();
}

std::string FileManager::calculateSHA256(const std::vector<char> &data) {
  SHA256_CTX sha256;
  SHA256_Init(&sha256);
  SHA256_Update(&sha256, data.data(), data.size());

  unsigned char hash[SHA256_DIGEST_LENGTH];
  SHA256_Final(hash, &sha256);

  std::stringstream ss;
  for (int i = 0; i < SHA256_DIGEST_LENGTH; i++) {
    ss << std::hex << std::setw(2) << std::setfill('0')
       << static_cast<int>(hash[i]);
  }

  return ss.str();
}

bool FileManager::finalizeUpload(const std::string &tempPath,
                                 const PhotoMetadata &metadata,
                                 std::string &outFinalPath) {

  std::lock_guard<std::mutex> lock(fileMutex_);

  // Verify file size
  if (!std::filesystem::exists(tempPath)) {
    LOG_ERROR("Temp file not found: " + tempPath);
    return false;
  }

  long long actualSize = std::filesystem::file_size(tempPath);
  if (actualSize != metadata.size) {
    LOG_ERROR("Size mismatch. Expected: " + std::to_string(metadata.size) +
              " Got: " + std::to_string(actualSize));
    return false;
  }

  // Verify hash
  if (!verifyHash(tempPath, metadata.hash)) {
    return false;
  }

  // Extract file extension from filename
  std::string extension = ".jpg"; // default
  size_t dotPos = metadata.filename.find_last_of('.');
  if (dotPos != std::string::npos) {
    extension = metadata.filename.substr(dotPos);
    // Convert to lowercase
    std::transform(extension.begin(), extension.end(), extension.begin(),
                   ::tolower);
  }

  // Generate final path
  outFinalPath = getPhotoPath(metadata.hash, extension);

  // Ensure directory exists
  std::filesystem::path dir = std::filesystem::path(outFinalPath).parent_path();
  if (!ensureDirectoryExists(dir.string())) {
    return false;
  }

  // Move file atomically
  try {
    std::filesystem::rename(tempPath, outFinalPath);
    updateStorageUsed(metadata.size);
    LOG_INFO("Finalized upload: " + outFinalPath);
    return true;
  } catch (const std::exception &e) {
    LOG_ERROR("Failed to move file: " + std::string(e.what()));
    return false;
  }
}

void FileManager::cancelUpload(const std::string &tempPath) {
  std::lock_guard<std::mutex> lock(fileMutex_);

  try {
    if (std::filesystem::exists(tempPath)) {
      std::filesystem::remove(tempPath);
      LOG_INFO("Cancelled upload and removed temp file: " + tempPath);
    }
  } catch (const std::exception &e) {
    LOG_ERROR("Error removing temp file: " + std::string(e.what()));
  }
}

bool FileManager::deletePhoto(const std::string &hash) {
  std::lock_guard<std::mutex> lock(fileMutex_);

  // Find the photo file
  std::vector<std::string> extensions = {".jpg", ".jpeg", ".png",
                                         ".gif", ".heic", ".webp"};

  for (const auto &ext : extensions) {
    std::string path = getPhotoPath(hash, ext);
    if (std::filesystem::exists(path)) {
      try {
        long long size = std::filesystem::file_size(path);
        std::filesystem::remove(path);
        updateStorageUsed(-size);
        LOG_INFO("Deleted photo: " + path);
        return true;
      } catch (const std::exception &e) {
        LOG_ERROR("Failed to delete photo: " + std::string(e.what()));
        return false;
      }
    }
  }

  LOG_WARN("Photo not found for deletion: " + hash);
  return false;
}

void FileManager::updateStorageUsed(long long delta) {
  currentStorageUsed_ += delta;
}

long long FileManager::calculateDirectorySize(const std::string &path) {
  long long total = 0;

  try {
    for (const auto &entry :
         std::filesystem::recursive_directory_iterator(path)) {
      if (entry.is_regular_file()) {
        total += entry.file_size();
      }
    }
  } catch (const std::exception &e) {
    LOG_ERROR("Error calculating directory size: " + std::string(e.what()));
  }

  return total;
}

std::string FileManager::generatePhotoPath(const PhotoMetadata &metadata) {
  // Extract extension
  std::string extension = ".jpg";
  size_t dotPos = metadata.filename.find_last_of('.');
  if (dotPos != std::string::npos) {
    extension = metadata.filename.substr(dotPos);
  }

  return getPhotoPath(metadata.hash, extension, metadata.receivedAt);
}

// Phase 2: Resumable Uploads

std::string FileManager::getUploadTempPath(const std::string &uploadId) {
  return getTempDir() + "/" + uploadId;
}

bool FileManager::appendChunk(const std::string &uploadId,
                              const std::vector<char> &data) {
  std::lock_guard<std::mutex> lock(fileMutex_);
  std::string path = getUploadTempPath(uploadId);

  try {
    std::ofstream file(path, std::ios::binary | std::ios::app);
    if (!file) {
      LOG_ERROR("Failed to open temp file for appending: " + path);
      return false;
    }
    file.write(data.data(), data.size());
    return true;
  } catch (const std::exception &e) {
    LOG_ERROR("Error appending chunk: " + std::string(e.what()));
    return false;
  }
}

bool FileManager::finalizeFile(const std::string &uploadId,
                               const std::string &finalPath) {
  std::lock_guard<std::mutex> lock(fileMutex_);
  std::string tempPath = getUploadTempPath(uploadId);

  if (!std::filesystem::exists(tempPath)) {
    LOG_ERROR("Temp file missing during finalization: " + tempPath);
    return false;
  }

  // Ensure target directory exists
  std::filesystem::path dir = std::filesystem::path(finalPath).parent_path();
  if (!ensureDirectoryExists(dir.string())) {
    return false;
  }

  // Move file atomically with retry
  int maxRetries = 3;
  for (int i = 0; i < maxRetries; ++i) {
    try {
      std::filesystem::rename(tempPath, finalPath);
      // Update storage usage
      long long size = std::filesystem::file_size(finalPath);
      updateStorageUsed(size);
      LOG_INFO("Finalized upload: " + uploadId + " -> " + finalPath);
      return true;
    } catch (const std::exception &e) {
      if (i == maxRetries - 1) {
        LOG_ERROR("Failed to move finalize file (Attempt " +
                  std::to_string(i + 1) + "): " + std::string(e.what()));
        return false;
      }
      // Small wait before retry
      std::this_thread::sleep_for(std::chrono::milliseconds(100));
    }
  }
  return false;
}

long long FileManager::getFileSize(const std::string &path) {
  try {
    if (std::filesystem::exists(path)) {
      return std::filesystem::file_size(path);
    }
  } catch (...) {
  }
  return 0;
}

bool FileManager::deleteUploadSessionFiles(const std::string &uploadId) {
  std::lock_guard<std::mutex> lock(fileMutex_);
  std::string path = getUploadTempPath(uploadId);
  try {
    if (std::filesystem::exists(path)) {
      std::filesystem::remove(path);
      LOG_INFO("Deleted temp file for session: " + uploadId);
      return true;
    }
  } catch (const std::exception &e) {
    LOG_ERROR("Error deleting upload session files: " + std::string(e.what()));
  }
  return false;
}

void FileManager::cleanupTempFolder(int maxAgeHours) {
  std::lock_guard<std::mutex> lock(fileMutex_);
  LOG_INFO("Starting global temp folder cleanup (Age > " +
           std::to_string(maxAgeHours) + "h)");

  try {
    if (!std::filesystem::exists(tempDir_))
      return;

    auto now = std::filesystem::file_time_type::clock::now();
    int count = 0;

    for (const auto &entry : std::filesystem::directory_iterator(tempDir_)) {
      if (!entry.is_regular_file())
        continue;

      auto ftime = entry.last_write_time();
      auto age =
          std::chrono::duration_cast<std::chrono::hours>(now - ftime).count();

      bool hasExtension = entry.path().has_extension();

      if (!hasExtension) {
        // If it has no extension, we add .tmp so it's recognizable
        // and can be cleaned up normally later if needed.
        try {
          std::filesystem::path newPath = entry.path();
          newPath += ".tmp";
          std::filesystem::rename(entry.path(), newPath);
          LOG_INFO("Added .tmp extension to extensionless temp file: " +
                   entry.path().filename().string());
          continue; // Move to next file
        } catch (...) {
          // Ignore rename errors
        }
      }

      if (age >= maxAgeHours) {
        std::filesystem::remove(entry.path());
        count++;
        LOG_INFO("Cleaned up orphaned temp file: " +
                 entry.path().filename().string());
      }
    }

    if (count > 0) {
      LOG_INFO("Temp cleanup finished. Removed " + std::to_string(count) +
               " orphaned files.");
    }
  } catch (const std::exception &e) {
    LOG_ERROR("Error during temp cleanup: " + std::string(e.what()));
  }
}

std::vector<std::string> FileManager::getAllPhotoHashes(size_t limit) {
  std::vector<std::string> hashes;
  std::string photosDir = getPhotosDir();
  // LOG_INFO("Scanning for integrity in: " + photosDir); // Verbose

  if (!std::filesystem::exists(photosDir)) {
    return hashes;
  }

  try {
    for (const auto &entry :
         std::filesystem::recursive_directory_iterator(photosDir)) {
      if (entry.is_regular_file()) {
        std::string filename = entry.path().stem().string();
        // Simple validation: check if hex string?
        // For now, assume stems are hashes if length is 64 (SHA256)
        if (filename.length() == 64) {
          hashes.push_back(filename);
          if (limit > 0 && hashes.size() >= limit) {
            break;
          }
        }
      }
    }
  } catch (const std::exception &e) {
    LOG_ERROR("Error listing photo hashes: " + std::string(e.what()));
  }

  return hashes;
}
