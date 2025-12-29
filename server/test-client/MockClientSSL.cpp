#define NOMINMAX
#include <boost/asio.hpp>
#include <boost/asio/ssl.hpp>
#include <boost/program_options.hpp>
#include <iomanip>
#include <iostream>
#include <openssl/sha.h>
#include <random>
#include <sstream>
#include <string>
#include <vector>

#include "../src/ProtocolParser.h"
#include <nlohmann/json.hpp>
#include <string>
#include <vector>

using boost::asio::ip::tcp;
using json = nlohmann::json;
namespace po = boost::program_options;
namespace ssl = boost::asio::ssl;

class MockClientSSL {
public:
  MockClientSSL(const std::string &host, int port)
      : host_(host), port_(port), socket_(io_context_, ctx_) {

    // Allow self-signed certificates
    ctx_.set_verify_mode(ssl::verify_none);
  }

  bool connect() {
    try {
      tcp::resolver resolver(io_context_);
      auto endpoints = resolver.resolve(host_, std::to_string(port_));
      boost::asio::connect(socket_.lowest_layer(), endpoints);

      std::cout << "Connected to " << host_ << ":" << port_ << " (TCP)"
                << std::endl;

      // Perform SSL handshake
      socket_.handshake(ssl::stream_base::client);
      std::cout << "SSL Handshake successful" << std::endl;

      return true;
    } catch (std::exception &e) {
      std::cerr << "Connection failed: " << e.what() << std::endl;
      return false;
    }
  }

  void sendMessage(const std::string &message) {
    std::string msg = message + "\n";
    boost::asio::write(socket_, boost::asio::buffer(msg));
    std::cout << "SENT: " << message << std::endl;
  }

  std::string receiveMessage() {
    boost::asio::streambuf buffer;
    boost::asio::read_until(socket_, buffer, '\n');

    std::istream is(&buffer);
    std::string message;
    std::getline(is, message);

    if (!message.empty() && message.back() == '\r') {
      message.pop_back();
    }

    std::cout << "RECV: " << message << std::endl;
    return message;
  }

  void sendBinaryData(const std::vector<char> &data) {
    boost::asio::write(socket_, boost::asio::buffer(data));
    std::cout << "SENT: [Binary Data " << data.size() << " bytes]" << std::endl;
  }

  void runSyncSession(int numPhotos, int batchSize,
                      const std::string &deviceId = "mock_client") {
    std::cout << "\n=== Starting SSL Sync Session ===" << std::endl;

    // 1. Send PAIRING_REQUEST
    json pairingPayload = {{"deviceId", deviceId},
                           {"token", "mock_token"},
                           {"userName", "MockUser"}};
    std::string pairingStr = pairingPayload.dump();
    Packet pairingPacket;
    pairingPacket.header.magic = PROTOCOL_MAGIC;
    pairingPacket.header.version = PROTOCOL_VERSION;
    pairingPacket.header.type = PacketType::PAIRING_REQUEST;
    pairingPacket.payload.assign(pairingStr.begin(), pairingStr.end());
    pairingPacket.header.payloadLength = (uint32_t)pairingPacket.payload.size();

    sendPacket(pairingPacket);

    // 2. Expect PAIRING_RESPONSE
    Packet response = receivePacket();
    if (response.header.type != PacketType::PAIRING_RESPONSE) {
      std::cerr << "Expected PAIRING_RESPONSE, got "
                << (int)response.header.type << std::endl;
      return;
    }
    json msgProps = ProtocolParser::parsePayload(response);
    if (!msgProps.value("success", false)) {
      std::cerr << "Pairing failed: " << msgProps.value("message", "unknown")
                << std::endl;
      return;
    }
    int sessionId = msgProps["sessionId"];
    std::cout << "Session Established: " << sessionId << std::endl;

    int photosSent = 0;
    while (photosSent < numPhotos) {
      int currentBatchSize = std::min(batchSize, numPhotos - photosSent);

      // No explicit BEGIN_BATCH command in ProtocolParser?
      // TcpListener just handles Metadata packets one by one.
      // The "BEGIN_BATCH" in older MockClient might be legacy.
      // Let's just send Photos directly.

      for (int i = 0; i < currentBatchSize; i++) {
        long long size = 1024 * (10 + (i % 10)); // 10KB - 20KB
        std::vector<char> data(size);
        for (size_t j = 0; j < data.size(); j++) {
          data[j] = (char)((i + j) % 256);
        }

        std::string hash = calculateSHA256(data);
        std::string filename =
            "mock_photo_" + std::to_string(photosSent + i) + ".jpg";

        // 3. Send METADATA
        json metaPayload = {
            {"filename", filename}, {"size", size}, {"hash", hash}};
        std::string metaStr = metaPayload.dump();
        Packet metaPacket;
        metaPacket.header.magic = PROTOCOL_MAGIC;
        metaPacket.header.version = PROTOCOL_VERSION;
        metaPacket.header.type = PacketType::METADATA;
        metaPacket.payload.assign(metaStr.begin(), metaStr.end());
        metaPacket.header.payloadLength = (uint32_t)metaPacket.payload.size();

        sendPacket(metaPacket);

        // 4. Expect TRANSFER_READY (or SKIP if duplicate)
        Packet ready = receivePacket();
        if (ready.header.type == PacketType::TRANSFER_READY) {
          json readyPayload = ProtocolParser::parsePayload(ready);
          long long offset = readyPayload.value("offset", 0LL);

          // 5. Send FILE_CHUNK(s)
          // For simplicity, sending one big chunk if size permits, or split?
          // ProtocolParser doesn't have createFileChunk? It might.
          // If not, manual create.
          // TcpListener expects raw bytes for FLIE_CHUNK? No, it uses
          // handlePacket which uses Header. So we send a packet with type
          // FILE_CHUNK.

          std::vector<char> chunkData(data.begin() + offset, data.end());

          // Construct chunk packet manually if helper missing, or use helper.
          // ProtocolParser::createPacket handles JSON.
          // For binary, we might need manual.
          Packet chunkPacket;
          chunkPacket.header.magic = PROTOCOL_MAGIC;
          chunkPacket.header.version = PROTOCOL_VERSION;
          chunkPacket.header.type = PacketType::FILE_CHUNK;
          chunkPacket.payload = chunkData;
          chunkPacket.header.payloadLength =
              (uint32_t)chunkPacket.payload.size();

          sendPacket(chunkPacket);

          // 6. Send TRANSFER_COMPLETE
          // Server V1 does not send ACK for Chunk, so we proceed directly.
          Packet completePacket;
          completePacket.header.magic = PROTOCOL_MAGIC;
          completePacket.header.version = PROTOCOL_VERSION;
          completePacket.header.type = PacketType::TRANSFER_COMPLETE;

          json completePayload = {{"hash", hash}};
          std::string completeStr = completePayload.dump();
          completePacket.payload.assign(completeStr.begin(), completeStr.end());
          completePacket.header.payloadLength =
              (uint32_t)completePacket.payload.size();

          sendPacket(completePacket);
          std::cout << "Sent TRANSFER_COMPLETE" << std::endl;

          // Server does NOT send Success packet for V1 (only Error on failure).
          // We wait briefly to see if Error comes back, else assume success.
          // Or we can just proceed.

        } else if (ready.header.type == PacketType::PROTOCOL_ERROR) {
          std::cerr << "Server Error: "
                    << ProtocolParser::parsePayload(ready).value("message", "")
                    << std::endl;
        } else {
          std::cout << "Did not receive READY for " << filename
                    << " type=" << (int)ready.header.type << std::endl;
        }
      }
      photosSent += currentBatchSize;
    }

    std::cout << "\n=== Session Complete ===" << std::endl;
  }

  // Helper to send packet
  void sendPacket(const Packet &p) {
    std::vector<char> bytes = ProtocolParser::serializePacket(p);
    boost::asio::write(socket_, boost::asio::buffer(bytes));
    // std::cout << "Sent Packet: " << (int)p.header.type << " Size: " <<
    // p.header.payloadLength << std::endl;
  }

  // Helper to receive packet
  Packet receivePacket() {
    // 1. Read Header
    std::vector<char> headerBuf(sizeof(PacketHeader));
    boost::asio::read(socket_, boost::asio::buffer(headerBuf));

    Packet p = ProtocolParser::deserializePacketHeader(headerBuf);

    // 2. Read Payload if any
    if (p.header.payloadLength > 0) {
      p.payload.resize(p.header.payloadLength);
      boost::asio::read(socket_, boost::asio::buffer(p.payload));
    }
    return p;
  }

  void disconnect() {
    // socket_.shutdown();
    socket_.lowest_layer().close();
  }

private:
  ssl::context ctx_{ssl::context::sslv23};
  boost::asio::io_context io_context_;
  ssl::stream<tcp::socket> socket_;
  std::string host_;
  int port_;

  std::string calculateSHA256(const std::vector<char> &data) {
    unsigned char hash[SHA256_DIGEST_LENGTH];
    SHA256_CTX sha256;
    SHA256_Init(&sha256);
    SHA256_Update(&sha256, data.data(), data.size());
    SHA256_Final(hash, &sha256);

    std::stringstream ss;
    for (int i = 0; i < SHA256_DIGEST_LENGTH; i++) {
      ss << std::hex << std::setw(2) << std::setfill('0') << (int)hash[i];
    }
    return ss.str();
  }
};

int main(int argc, char *argv[]) {
  try {
    po::options_description desc("Mock Client SSL Options");
    desc.add_options()("help,h", "Help")(
        "host", po::value<std::string>()->default_value("localhost"),
        "Host")("port,p", po::value<int>()->default_value(50505), "Port")(
        "photos,n", po::value<int>()->default_value(5), "Num Photos")(
        "device-id,d",
        po::value<std::string>()->default_value("mock_client_ssl"),
        "Device ID");

    po::variables_map vm;
    po::store(po::parse_command_line(argc, argv, desc), vm);
    po::notify(vm);

    if (vm.count("help")) {
      std::cout << desc << std::endl;
      return 0;
    }

    MockClientSSL client(vm["host"].as<std::string>(), vm["port"].as<int>());
    if (client.connect()) {
      client.runSyncSession(vm["photos"].as<int>(), 5,
                            vm["device-id"].as<std::string>());
      client.disconnect();
    }
  } catch (std::exception &e) {
    std::cerr << "Error: " << e.what() << std::endl;
    return 1;
  }
  return 0;
}
