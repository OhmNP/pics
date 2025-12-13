#include "ProtocolParser.h"
#include "Logger.h"
#include <algorithm>
#include <sstream>

ParsedCommand ProtocolParser::parse(const std::string &message) {
  ParsedCommand cmd;
  cmd.type = CommandType::UNKNOWN;

  auto parts = split(message, ' ');
  if (parts.empty()) {
    return cmd;
  }

  std::string command = parts[0];

  if (command == "HELLO") {
    cmd.type = CommandType::SESSION_START;
    if (parts.size() > 1) {
      cmd.data = parts[1]; // deviceId
    }
    if (parts.size() > 2) {
      cmd.token = parts[2]; // pairing token
    }
  } else if (command == "BEGIN_BATCH") {
    cmd.type = CommandType::BATCH_START;
    if (parts.size() > 1) {
      try {
        cmd.batchSize = std::stoi(parts[1]);
      } catch (const std::exception &) {
        LOG_ERROR("Invalid batch size: " + parts[1]);
        cmd.type = CommandType::UNKNOWN;
      }
    }
  } else if (command == "PHOTO") {
    cmd.type = CommandType::PHOTO_METADATA;
    cmd.photo = parsePhotoMetadata(message);
    if (cmd.photo.filename.empty()) {
      cmd.type = CommandType::UNKNOWN;
    }
  } else if (command == "DATA_TRANSFER") {
    cmd.type = CommandType::DATA_TRANSFER;
    if (parts.size() > 1) {
      try {
        cmd.dataSize = std::stoll(parts[1]);
      } catch (const std::exception &) {
        LOG_ERROR("Invalid data size: " + parts[1]);
        cmd.type = CommandType::UNKNOWN;
      }
    }
  } else if (command == "RESUME_UPLOAD") {
    cmd.type = CommandType::RESUME_UPLOAD;
    if (parts.size() > 1) {
      cmd.hash = parts[1];
    }
  } else if (command == "BATCH_CHECK") { // NEW
    cmd.type = CommandType::BATCH_CHECK;
    if (parts.size() > 1) {
      try {
        cmd.batchCheckCount = std::stoi(parts[1]);
      } catch (const std::exception &) {
        LOG_ERROR("Invalid batch check count: " + parts[1]);
        cmd.type = CommandType::UNKNOWN;
      }
    }
  } else if (command == "BATCH_END") {
    cmd.type = CommandType::BATCH_END;
  } else if (command == "END_SESSION") {
    cmd.type = CommandType::SESSION_END;
  }

  return cmd;
}

PhotoMetadata ProtocolParser::parsePhotoMetadata(const std::string &line) {
  PhotoMetadata photo;
  auto parts = split(line, ' ');

  if (parts.size() >= 4) {
    photo.filename = parts[1];
    try {
      photo.size = std::stoll(parts[2]);
      photo.hash = parts[3];
    } catch (const std::exception &) {
      LOG_ERROR("Invalid photo size: " + parts[2]);
      // Return empty filename to indicate error
      photo.filename = "";
    }
  }

  return photo;
}

std::string ProtocolParser::createAck(const std::string &message) {
  if (message.empty()) {
    return "ACK\n";
  }
  return "ACK " + message + "\n";
}

std::string ProtocolParser::createSessionAck(int sessionId) {
  return "SESSION_START " + std::to_string(sessionId) + "\n";
}

std::string ProtocolParser::createSendResponse(long long offset) {
  if (offset > 0) {
    return "SEND " + std::to_string(offset) + "\n";
  }
  return "SEND\n";
}

std::string ProtocolParser::createSkipResponse() { return "SKIP\n"; }

std::string ProtocolParser::createBatchResultResponse(int count) { // NEW
  return "BATCH_RESULT " + std::to_string(count) + "\n";
}

std::string ProtocolParser::createError(const std::string &message) {
  return "ERROR " + message + "\n";
}

std::vector<std::string> ProtocolParser::split(const std::string &str,
                                               char delimiter) {
  std::vector<std::string> tokens;
  std::string token;
  std::istringstream tokenStream(str);

  while (std::getline(tokenStream, token, delimiter)) {
    std::string trimmed = trim(token);
    if (!trimmed.empty()) {
      tokens.push_back(trimmed);
    }
  }

  return tokens;
}

std::string ProtocolParser::trim(const std::string &str) {
  size_t first = str.find_first_not_of(" \t\r\n");
  if (first == std::string::npos) {
    return "";
  }
  size_t last = str.find_last_not_of(" \t\r\n");
  return str.substr(first, (last - first + 1));
}
