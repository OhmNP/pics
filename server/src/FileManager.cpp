#include "FileManager.h"
#include "Logger.h"
#include <fstream>
#include <iomanip>
#include <openssl/sha.h>
#include <sstream>


FileManager::FileManager(const std::string &storageDir,
                         long long maxStorageBytes)
    : storageDir_(storageDir), maxStorageBytes_(maxStorageBytes),
      currentStorageUsed_(0) {}

FileManager::~FileManager() {}

bool FileManager::initialize() {
  // Create main storage directory
  if (!ensureDirectoryExists(storageDir_)) {
    LOG_ERROR("Failed to create storage directory: " + storageDir_);
    return false;
  }

  // Create subdirectories
  if (!ensureDirectoryExists(getPhotosDir())) {
    LOG_ERROR("Failed to create photos directory");
    return false;
  }

  if (!ensureDirectoryExists(getTempDir())) {
    LOG_ERROR("Failed to create temp directory");
    return false;
  }

  // Calculate current storage usage
  currentStorageUsed_ = calculateDirectorySize(getPhotosDir());
  LOG_INFO("Storage initialized. Current usage: " +
           std::to_string(currentStorageUsed_) + " bytes");

  return true;
}

std::string FileManager::getTempDir() { return storageDir_ + "/temp"; }

std::string FileManager::getPhotosDir() { return storageDir_ + "/photos"; }

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
                                      const std::string &extension) {
  // Get current year/month for organization
  auto now = std::chrono::system_clock::now();
  auto time = std::chrono::system_clock::to_time_t(now);
  std::tm *tm = std::gmtime(&time);

  std::stringstream path;
  path << getPhotosDir() << "/" << std::put_time(tm, "%Y/%m") << "/" << hash
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

  // Convert to hex string
  std::stringstream ss;
  for (int i = 0; i < SHA256_DIGEST_LENGTH; i++) {
    ss << std::hex << std::setw(2) << std::setfill('0')
       << static_cast<int>(hash[i]);
  }

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

  return getPhotoPath(metadata.hash, extension);
}
