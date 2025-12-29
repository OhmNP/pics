#include "IntegrityScanner.h"
#include "FileManager.h"
#include "Logger.h"
#include <chrono>
#include <iostream>
#include <numeric>
#include <set>

IntegrityScanner::IntegrityScanner(DatabaseManager &db,
                                   FileManager &fileManager)
    : db_(db), fileManager_(fileManager), running_(false) {}

IntegrityScanner::~IntegrityScanner() { stop(); }

void IntegrityScanner::start(const Config &config) {
  if (running_) {
    return;
  }
  config_ = config;
  running_ = true;
  scanThread_ = std::thread(&IntegrityScanner::scanLoop, this);
  std::cout << "[IntegrityScanner] Started with interval "
            << config_.scanIntervalSeconds << "s" << std::endl;
}

void IntegrityScanner::stop() {
  running_ = false;
  if (scanThread_.joinable()) {
    scanThread_.join();
  }
}

IntegrityScanner::Report IntegrityScanner::runScan() {
  Report report;
  checkIntegrity(report);
  return report;
}

void IntegrityScanner::scanLoop() {
  while (running_) {
    // 1. Scheduler Check (Run every 1s default or sleep longer?)
    // Sleep 1s to allow fast exit
    std::this_thread::sleep_for(std::chrono::seconds(1));

    if (!running_)
      break;

    auto now = std::chrono::system_clock::now();
    Report report;
    report.timestamp = "SCHEDULED"; // Placeholder
    bool ranSomething = false;

    // Check Missing Blobs (Fast)
    auto missingElapsed = std::chrono::duration_cast<std::chrono::seconds>(
                              now - lastMissingCheck_)
                              .count();
    if (missingElapsed >= config_.missingCheckInterval) {
      runMissingCheck(report);
      lastMissingCheck_ = now;
      ranSomething = true;
    }

    // Check Orphans - Sampled (Medium)
    auto orphanSampleElapsed = std::chrono::duration_cast<std::chrono::seconds>(
                                   now - lastOrphanSample_)
                                   .count();
    if (orphanSampleElapsed >= config_.orphanSampleInterval) {
      runOrphanCheck(report, config_.orphanSampleSize);
      lastOrphanSample_ = now;
      ranSomething = true;
    }

    // Check Orphans - Full (Slow)
    auto fullScanElapsed =
        std::chrono::duration_cast<std::chrono::seconds>(now - lastFullScan_)
            .count();
    if (fullScanElapsed >= config_.fullScanInterval) {
      runOrphanCheck(report, 0); // 0 = Unlimited
      lastFullScan_ = now;
      ranSomething = true;
    }

    if (ranSomething) {
      std::lock_guard<std::mutex> lock(reportMutex_);
      lastReport_ = report;
    }
  }
}

IntegrityScanner::Report IntegrityScanner::getLastReport() const {
  std::lock_guard<std::mutex> lock(reportMutex_);
  return lastReport_;
}

void IntegrityScanner::runMissingCheck(Report &report) {
  // 1. Get All Photos from DB
  std::vector<PhotoMetadata> photos = db_.getAllPhotos();
  report.totalPhotos = photos.size();

  int missing = 0;
  int corrupt = 0;
  int tombstones = 0;

  for (const auto &photo : photos) {
    if (!photo.deletedAt.empty()) {
      tombstones++;
      continue;
    }

    std::string fullPath = fileManager_.generatePhotoPath(photo);
    if (!std::filesystem::exists(fullPath)) {
      missing++;
      LOG_WARN("[Integrity] MISSING BLOB: " + photo.hash + " (" +
               photo.filename + ")");
    } else if (config_.verifyHash) { // Optional deep verify in missing check?
                                     // Usually separated.
      // Requirement says "hourly: missing_only (+ optional hash verify if
      // enabled)"
      std::string diskHash = FileManager::calculateSHA256(fullPath);
      if (diskHash != photo.hash) {
        corrupt++;
        LOG_WARN("[Integrity] CORRUPT BLOB: " + photo.hash +
                 " (Disk: " + diskHash + ")");
      }
    }
  }
  report.missingBlobs += missing;
  report.corruptBlobs += corrupt;
  report.tombstones += tombstones;

  // Log Summary
  if (missing > 0 || corrupt > 0) {
    std::cout << "[Integrity] Missing Check Complete. Missing: " << missing
              << ", Corrupt: " << corrupt << std::endl;
  }
}

void IntegrityScanner::runOrphanCheck(Report &report, size_t limit) {
  std::string type = (limit > 0) ? "SAMPLED" : "FULL";
  // std::cout << "[Integrity] Starting " << type << " Orphan Check..." <<
  // std::endl; // Verbose

  std::vector<std::string> diskHashes = fileManager_.getAllPhotoHashes(limit);

  // Build DB Lookup (Optimization: Bloom filter or simply set? Set is fine for
  // 100k items)
  std::vector<PhotoMetadata> photos = db_.getAllPhotos();
  std::set<std::string> dbHashes;
  for (const auto &p : photos)
    dbHashes.insert(p.hash);

  int orphans = 0;
  for (const auto &diskHash : diskHashes) {
    if (dbHashes.find(diskHash) == dbHashes.end()) {
      orphans++;
      // Reconstruct path for log (best effort)
      std::string path = fileManager_.getPhotoPath(diskHash, ".jpg");
      LOG_WARN("[Integrity] ORPHAN BLOB: " + diskHash + " (" + path + ")");
    }
  }
  report.orphanBlobs += orphans;

  if (orphans > 0) {
    std::cout << "[Integrity] " << type
              << " Orphan Check Complete. Orphans: " << orphans << std::endl;
  }
}

// Legacy/Manual wrapper
void IntegrityScanner::checkIntegrity(Report &report) {
  runMissingCheck(report);
  runOrphanCheck(report, 0); // Full scan
}

void IntegrityScanner::logReport(const Report &report) {
  // Only used for manual runs or aggregated legacy logging
  std::cout << "[IntegrityScanner] Manual Report " << report.timestamp << "\n"
            << "  Total:   " << report.totalPhotos << "\n"
            << "  Missing: " << report.missingBlobs << "\n"
            << "  Corrupt: " << report.corruptBlobs << "\n"
            << "  Orphan:  " << report.orphanBlobs << "\n"
            << "  Tombstones: " << report.tombstones << std::endl;
}
