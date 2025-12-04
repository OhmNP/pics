#include "ConnectionManager.h"
#include "Logger.h"
#include "TcpListener.h"
#include <iostream>


// Undefine Windows macros that conflict with our enums
#ifdef ERROR
#undef ERROR
#endif

// Session implementation
Session::Session(tcp::socket socket, DatabaseManager &db,
                 FileManager &fileManager)
    : socket_(std::move(socket)), db_(db), fileManager_(fileManager),
      state_(SessionState::AWAITING_HELLO), clientId_(-1), sessionId_(-1),
      currentBatchSize_(0), photosInBatch_(0), totalPhotosReceived_(0) {}

void Session::start() {
  auto self(shared_from_this());
  LOG_INFO("New client connected from: " +
           socket_.remote_endpoint().address().to_string());
  doRead();
}

void Session::doRead() {
  auto self(shared_from_this());

  boost::asio::async_read_until(
      socket_, buffer_, '\n',
      [this, self](boost::system::error_code ec, std::size_t length) {
        if (!ec) {
          std::istream is(&buffer_);
          std::string message;
          std::getline(is, message);

          // Remove carriage return if present
          if (!message.empty() && message.back() == '\r') {
            message.pop_back();
          }

          if (!message.empty()) {
            handleCommand(message);
          }

          doRead(); // Continue reading
        } else {
          if (ec != boost::asio::error::eof) {
            LOG_ERROR("Read error: " + ec.message());
          }
          LOG_INFO("Client disconnected");
          ConnectionManager::getInstance().removeConnection(sessionId_);
          // Cleanup any incomplete uploads
          if (!currentTempPath_.empty()) {
            fileManager_.cancelUpload(currentTempPath_);
          }
        }
      });
}

void Session::doReadBinaryData(long long dataSize) {
  auto self(shared_from_this());

  // Create buffer for binary data
  auto dataBuffer = std::make_shared<std::vector<char>>(dataSize);

  boost::asio::async_read(
      socket_, boost::asio::buffer(*dataBuffer),
      [this, self, dataBuffer](boost::system::error_code ec,
                               std::size_t bytes_read) {
        if (!ec) {
          if (bytes_read == dataBuffer->size()) {
            handlePhotoData(*dataBuffer);
          } else {
            LOG_ERROR("Incomplete data received");
            doWrite(ProtocolParser::createError("Incomplete data"));
            fileManager_.cancelUpload(currentTempPath_);
            currentTempPath_.clear();
            state_ = SessionState::AWAITING_PHOTO_METADATA;
          }
        } else {
          LOG_ERROR("Binary read error: " + ec.message());
          fileManager_.cancelUpload(currentTempPath_);
          currentTempPath_.clear();
        }
      });
}

void Session::handlePhotoData(const std::vector<char> &data) {
  // Write data to temp file
  if (!fileManager_.writeChunk(currentTempPath_, data, 0)) {
    LOG_ERROR("Failed to write photo data");
    doWrite(ProtocolParser::createError("Write failed"));
    state_ = SessionState::AWAITING_PHOTO_METADATA;
    return;
  }

  // Finalize upload
  std::string finalPath;
  if (!fileManager_.finalizeUpload(currentTempPath_, currentPhoto_,
                                   finalPath)) {
    LOG_ERROR("Failed to finalize upload for: " + currentPhoto_.filename);
    doWrite(ProtocolParser::createError("Finalization failed"));
    state_ = SessionState::AWAITING_PHOTO_METADATA;
    return;
  }

  // Insert into database
  if (!db_.insertPhoto(clientId_, currentPhoto_, finalPath)) {
    LOG_ERROR("Failed to insert photo into database");
    doWrite(ProtocolParser::createError("Database insert failed"));
    state_ = SessionState::AWAITING_PHOTO_METADATA;
    return;
  }

  // Success!
  LOG_INFO("Photo uploaded successfully: " + finalPath);
  doWrite(ProtocolParser::createAck(""));
  photosInBatch_++;
  totalPhotosReceived_++;

  // Update connection manager with progress
  ConnectionManager::getInstance().updateProgress(
      sessionId_, totalPhotosReceived_, currentPhoto_.size);

  // Clear current photo state
  currentTempPath_.clear();
  state_ = SessionState::AWAITING_PHOTO_METADATA;

  // Update session with current count
  db_.updateSessionPhotoCount(sessionId_, totalPhotosReceived_);
}

void Session::doWrite(const std::string &message) {
  auto self(shared_from_this());

  boost::asio::async_write(
      socket_, boost::asio::buffer(message),
      [this, self](boost::system::error_code ec, std::size_t /*length*/) {
        if (ec) {
          LOG_ERROR("Write error: " + ec.message());
        }
      });
}

void Session::handleCommand(const std::string &message) {
  LOG_DEBUG("Received: " + message);

  ParsedCommand cmd = ProtocolParser::parse(message);

  switch (cmd.type) {
  case CommandType::SESSION_START: {
    if (state_ != SessionState::AWAITING_HELLO) {
      doWrite(ProtocolParser::createError("Invalid state"));
      return;
    }

    clientId_ = db_.getOrCreateClient(cmd.data);
    if (clientId_ < 0) {
      doWrite(ProtocolParser::createError("Failed to create client"));
      return;
    }

    sessionId_ = db_.createSession(clientId_);
    if (sessionId_ < 0) {
      doWrite(ProtocolParser::createError("Failed to create session"));
      return;
    }

    doWrite(ProtocolParser::createSessionAck(sessionId_));
    state_ = SessionState::AWAITING_BATCH_START;

    // Register connection with ConnectionManager
    std::string ipAddress = socket_.remote_endpoint().address().to_string();
    ConnectionManager::getInstance().addConnection(sessionId_, cmd.data,
                                                   ipAddress);
    ConnectionManager::getInstance().updateStatus(sessionId_, "syncing");

    LOG_INFO("Session started: " + std::to_string(sessionId_));
    break;
  }

  case CommandType::BATCH_START: {
    if (state_ != SessionState::AWAITING_BATCH_START) {
      doWrite(ProtocolParser::createError("Invalid state"));
      return;
    }

    currentBatchSize_ = cmd.batchSize;
    photosInBatch_ = 0;
    doWrite(ProtocolParser::createAck(""));
    state_ = SessionState::AWAITING_PHOTO_METADATA;
    LOG_INFO("Batch started: " + std::to_string(currentBatchSize_) + " photos");
    break;
  }

  case CommandType::PHOTO_METADATA: {
    if (state_ != SessionState::AWAITING_PHOTO_METADATA) {
      doWrite(ProtocolParser::createError("Invalid state"));
      return;
    }

    currentPhoto_ = cmd.photo;
    LOG_DEBUG("Photo metadata: " + currentPhoto_.filename + " (" +
              std::to_string(currentPhoto_.size) +
              " bytes, hash: " + currentPhoto_.hash + ")");

    // Check if photo already exists
    if (db_.photoExists(currentPhoto_.hash)) {
      LOG_INFO("Photo already synced (duplicate): " + currentPhoto_.hash);
      doWrite(ProtocolParser::createSkipResponse());
      photosInBatch_++;
      return;
    }

    // Check storage quota
    if (!fileManager_.hasSpaceAvailable(currentPhoto_.size)) {
      LOG_ERROR("Storage quota exceeded");
      doWrite(ProtocolParser::createError("Storage quota exceeded"));
      return;
    }

    // Start upload
    if (!fileManager_.startUpload(currentPhoto_, currentTempPath_)) {
      LOG_ERROR("Failed to start upload");
      doWrite(ProtocolParser::createError("Upload start failed"));
      return;
    }

    // Tell client to send data
    doWrite(ProtocolParser::createSendResponse());
    state_ = SessionState::AWAITING_DATA_COMMAND;
    break;
  }

  case CommandType::DATA_TRANSFER: {
    if (state_ != SessionState::AWAITING_DATA_COMMAND) {
      doWrite(ProtocolParser::createError("Invalid state"));
      return;
    }

    long long dataSize = cmd.dataSize;
    LOG_DEBUG("Receiving photo data: " + std::to_string(dataSize) + " bytes");

    state_ = SessionState::RECEIVING_PHOTO_DATA;
    doReadBinaryData(dataSize);
    break;
  }

  case CommandType::RESUME_UPLOAD: {
    // Resume upload feature
    UploadProgress progress;
    if (!fileManager_.resumeUpload(cmd.hash, progress)) {
      doWrite(ProtocolParser::createError("Cannot resume upload"));
      return;
    }

    currentPhoto_.hash = progress.hash;
    currentTempPath_ = progress.tempFilePath;

    // Tell client where to resume from
    doWrite(ProtocolParser::createSendResponse(progress.bytesReceived));
    state_ = SessionState::AWAITING_DATA_COMMAND;
    LOG_INFO("Resuming upload from offset: " +
             std::to_string(progress.bytesReceived));
    break;
  }

  case CommandType::BATCH_END: {
    if (state_ != SessionState::AWAITING_PHOTO_METADATA) {
      doWrite(ProtocolParser::createError("Invalid state"));
      return;
    }

    doWrite(ProtocolParser::createAck(std::to_string(photosInBatch_)));
    state_ = SessionState::AWAITING_SESSION_END;
    LOG_INFO("Batch completed: " + std::to_string(photosInBatch_) + " photos");
    break;
  }

  case CommandType::SESSION_END: {
    if (state_ != SessionState::AWAITING_SESSION_END) {
      doWrite(ProtocolParser::createError("Invalid state"));
      return;
    }

    db_.finalizeSession(sessionId_, "completed");
    doWrite(ProtocolParser::createAck(""));
    LOG_INFO("Session completed: " + std::to_string(sessionId_) +
             " (Total photos: " + std::to_string(totalPhotosReceived_) + ")");
    break;
  }

  default:
    LOG_WARN("Unknown command received");
    doWrite(ProtocolParser::createError("Unknown command"));
    break;
  }
}

// TcpListener implementation
TcpListener::TcpListener(boost::asio::io_context &io_context, int port,
                         DatabaseManager &db, FileManager &fileManager)
    : acceptor_(io_context, tcp::endpoint(tcp::v4(), port)), db_(db),
      fileManager_(fileManager) {
  LOG_INFO("TCP Listener started on port " + std::to_string(port));
  doAccept();
}

void TcpListener::doAccept() {
  acceptor_.async_accept([this](boost::system::error_code ec,
                                tcp::socket socket) {
    if (!ec) {
      std::make_shared<Session>(std::move(socket), db_, fileManager_)->start();
    } else {
      LOG_ERROR("Accept error: " + ec.message());
    }

    doAccept();
  });
}
