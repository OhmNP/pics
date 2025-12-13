#pragma once

#include "DatabaseManager.h"
#include "FileManager.h"
#include "ProtocolParser.h"
#include <boost/asio.hpp>
#include <memory>
#include <string>
#include <vector>

using boost::asio::ip::tcp;

class Session : public std::enable_shared_from_this<Session> {
public:
  Session(tcp::socket socket, DatabaseManager &db, FileManager &fileManager);
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

  tcp::socket socket_;
  DatabaseManager &db_;
  FileManager &fileManager_;

  // Buffers
  std::vector<char> headerBuffer_;
  std::vector<char> payloadBuffer_;

  // State
  int clientId_ = -1;
  int sessionId_ = -1;

  // Current Transfer State
  std::string currentFileName_;
  long long currentFileSize_ = 0;
  long long currentFileReceived_ = 0;
  std::string currentTempPath_;
  std::string currentFileHash_;
};

class TcpListener {
public:
  TcpListener(boost::asio::io_context &io_context, int port,
              DatabaseManager &db, FileManager &fileManager);

private:
  void doAccept();

  tcp::acceptor acceptor_;
  DatabaseManager &db_;
  FileManager &fileManager_;
};
