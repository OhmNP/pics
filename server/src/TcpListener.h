#pragma once

#include "DatabaseManager.h"
#include "FileManager.h"
#include "Logger.h"
#include "ProtocolParser.h"
#include <boost/asio.hpp>
#include <boost/asio/ssl.hpp>
#include <memory>
#include <string>
#include <vector>

using boost::asio::ip::tcp;

class Session : public std::enable_shared_from_this<Session> {
public:
  Session(boost::asio::ssl::stream<tcp::socket> socket, DatabaseManager &db,
          FileManager &fileManager);
  ~Session();
  void start();

private:
  void doReadHeader();
  void doReadPayload(PacketHeader header);
  void handlePacket(const Packet &packet);
  void sendPacket(const Packet &packet);

  // Command Handlers
  void handleDiscovery(const json &payload);
  void handlePairingRequest(const json &payload);
  void handleMetadata(const json &payload);
  void handleFileChunk(const std::vector<char> &data);
  void handleTransferComplete(const json &payload);

  // Phase 2: Resumable Upload Handlers
  void handleUploadInit(const json &payload);
  void handleUploadChunk(const std::vector<char> &data,
                         const PacketHeader &header);
  void handleUploadFinish(const json &payload);
  void handleUploadAbort(const json &payload);

  boost::asio::ssl::stream<tcp::socket> socket_;
  DatabaseManager &db_;
  FileManager &fileManager_;

  // Buffers
  std::vector<char> headerBuffer_;
  std::vector<char> payloadBuffer_;

  // State
  int clientId_ = -1;
  int sessionId_ = -1;

  // Current Transfer State
  std::string currentTraceId_;
  std::string currentFileName_;
  long long currentFileSize_ = 0;
  long long currentFileReceived_ = 0;
  std::string currentTempPath_;
  std::string currentFileHash_;

  // Session Stats
  int sessionPhotos_ = 0;
  long long sessionBytes_ = 0;

  // Helper
  void log(const std::string &message, LogLevel level = LogLevel::L_INFO);
};

class TcpListener {
public:
  TcpListener(boost::asio::io_context &io_context,
              boost::asio::ssl::context &context, int port, DatabaseManager &db,
              FileManager &fileManager);

private:
  void doAccept();

  tcp::acceptor acceptor_;
  boost::asio::ssl::context &context_;
  DatabaseManager &db_;
  FileManager &fileManager_;
};
