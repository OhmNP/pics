#pragma once

#include "DatabaseManager.h"
#include "FileManager.h"
#include <atomic>
#include <functional>
#include <mutex>
#include <thread>

class IntegrityScanner {
public:
  struct Config {
    int scanIntervalSeconds = 3600; // Legacy: Default 1 hour
    bool verifyHash = false;        // If true, recompute SHA256 of blobs
    int batchSize = 100;            // Metadata rows per batch

    // Tiered Scheduling
    int missingCheckInterval = 3600;  // Default 1h
    int orphanSampleInterval = 86400; // Default 24h
    int fullScanInterval = 604800;    // Default 7d
    int orphanSampleSize = 1000;      // Default 1000 files
  };

  struct Report {
    int totalPhotos = 0;
    int missingBlobs = 0;
    int corruptBlobs = 0;
    int orphanBlobs = 0;
    int tombstones = 0;
    std::string timestamp;
    std::string status = "idle";
    std::string message;
  };

  IntegrityScanner(DatabaseManager &db, FileManager &fileManager);
  ~IntegrityScanner();

  void start(const Config &config);
  void stop();

  // Manual Trigger
  Report runScan();
  Report getLastReport() const; // Added

private:
  DatabaseManager &db_;
  FileManager &fileManager_;
  Config config_;

  mutable std::mutex reportMutex_; // Added
  Report lastReport_;              // Added

  std::atomic<bool> running_;
  std::thread scanThread_;

  // Scheduling State
  std::chrono::system_clock::time_point lastMissingCheck_;
  std::chrono::system_clock::time_point lastOrphanSample_;
  std::chrono::system_clock::time_point lastFullScan_;

  void scanLoop();

  // Specific Checks
  void runMissingCheck(Report &report);
  void runOrphanCheck(Report &report, size_t limit); // limit=0 for full

  // Combined (Legacy/Manual)
  void checkIntegrity(Report &report);

  void logReport(const Report &report);
};
