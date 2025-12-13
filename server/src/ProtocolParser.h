#pragma once

#include "DatabaseManager.h"
#include <string>
#include <vector>

enum class CommandType {
  SESSION_START,
  BATCH_START,
  PHOTO_METADATA,
  DATA_TRANSFER, // NEW
  RESUME_UPLOAD, // NEW
  BATCH_CHECK,   // NEW for Reconciliation
  BATCH_END,
  SESSION_END,
  UNKNOWN
};

struct ParsedCommand {
  CommandType type;
  std::string data;
  int batchSize;       // For BATCH_START
  PhotoMetadata photo; // For PHOTO_METADATA
  long long dataSize;  // For DATA_TRANSFER
  std::string hash;    // For RESUME_UPLOAD
  int batchCheckCount; // For BATCH_CHECK
  std::string token;   // For SESSION_START (Pairing)
};

class ProtocolParser {
public:
  static ParsedCommand parse(const std::string &message);
  static PhotoMetadata parsePhotoMetadata(const std::string &line);

  // Response generators
  static std::string createAck(const std::string &message);
  static std::string createSessionAck(int sessionId);
  static std::string createSendResponse(long long offset = 0); // NEW
  static std::string createSkipResponse();                     // NEW
  static std::string createBatchResultResponse(int count);     // NEW
  static std::string createError(const std::string &message);

private:
  static std::vector<std::string> split(const std::string &str, char delimiter);
  static std::string trim(const std::string &str);
};
