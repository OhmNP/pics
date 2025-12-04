#pragma once

#include "DatabaseManager.h"
#include "FileManager.h"
#include "ProtocolParser.h"
#include <boost/asio.hpp>
#include <memory>
#include <string>

using boost::asio::ip::tcp;

enum class SessionState {
  AWAITING_HELLO,
  AWAITING_BATCH_START,
  AWAITING_PHOTO_METADATA,
  AWAITING_DATA_COMMAND, // NEW
  RECEIVING_PHOTO_DATA,  // NEW
  AWAITING_BATCH_END,
  AWAITING_SESSION_END
};

class Session : public std::enable_shared_from_this<Session> {
public:
  Session(tcp::socket socket, DatabaseManager &db, FileManager &fileManager);
  void start();

private:
  void doRead();
  void doReadBinaryData(long long dataSize); // NEW
  void doWrite(const std::string &message);
  void handleCommand(const std::string &message);
  void handlePhotoData(const std::vector<char> &data); // NEW

  tcp::socket socket_;
  DatabaseManager &db_;
  FileManager &fileManager_; // NEW
  boost::asio::streambuf buffer_;

  SessionState state_; // NEW
  int clientId_;
  int sessionId_;
  int currentBatchSize_;
  int photosInBatch_;
  int totalPhotosReceived_;

  PhotoMetadata currentPhoto_;  // NEW
  std::string currentTempPath_; // NEW
};

class TcpListener {
public:
  TcpListener(boost::asio::io_context &io_context, int port,
              DatabaseManager &db, FileManager &fileManager);

private:
  void doAccept();

  tcp::acceptor acceptor_;
  DatabaseManager &db_;
  FileManager &fileManager_; // NEW
};
