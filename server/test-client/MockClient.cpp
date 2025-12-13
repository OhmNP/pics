#include <boost/asio.hpp>
#include <boost/program_options.hpp>
#include <iomanip>
#include <iostream>
#include <openssl/sha.h>
#include <random>
#include <sstream>
#include <string>
#include <vector>

using boost::asio::ip::tcp;
namespace po = boost::program_options;

class MockClient {
public:
  MockClient(const std::string &host, int port)
      : host_(host), port_(port), socket_(io_context_) {}

  bool connect() {
    try {
      tcp::resolver resolver(io_context_);
      auto endpoints = resolver.resolve(host_, std::to_string(port_));
      boost::asio::connect(socket_, endpoints);
      std::cout << "Connected to " << host_ << ":" << port_ << std::endl;
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
    std::cout << "\n=== Starting Sync Session ===" << std::endl;

    // Handshake
    sendMessage("HELLO " + deviceId);
    std::string response = receiveMessage();
    if (response.find("SESSION_START") == std::string::npos) {
      std::cerr << "Handshake failed" << std::endl;
      return;
    }

    int photosSent = 0;
    while (photosSent < numPhotos) {
      int currentBatchSize = std::min(batchSize, numPhotos - photosSent);

      sendMessage("BEGIN_BATCH " + std::to_string(currentBatchSize));
      response = receiveMessage();
      if (response.find("ACK") == std::string::npos) {
        std::cerr << "Batch start failed" << std::endl;
        return;
      }

      for (int i = 0; i < currentBatchSize; i++) {
        // Generate dummy photo data
        long long size = 1024 * (10 + (i % 10)); // 10KB - 20KB
        std::vector<char> data(size);
        // Fill with random pattern but deterministic for hash
        for (size_t j = 0; j < data.size(); j++) {
          data[j] = (char)((i + j) % 256);
        }

        std::string hash = calculateSHA256(data);
        std::string filename =
            "mock_photo_" + std::to_string(photosSent + i) + ".jpg";

        // Send Metadata
        sendMessage("PHOTO " + filename + " " + std::to_string(size) + " " +
                    hash);
        response = receiveMessage();

        if (response.find("SEND") != std::string::npos) {
          // Check for offset (SEND <offset>)
          long long offset = 0;
          if (response.length() > 5) {
            offset = std::stoll(response.substr(5));
          }

          // Send DATA command
          long long uploadSize = size - offset;
          sendMessage("DATA_TRANSFER " + std::to_string(uploadSize));

          // Send binary data (from offset)
          std::vector<char> chunk(data.begin() + offset, data.end());
          sendBinaryData(chunk);

          // Wait for ACK
          response = receiveMessage();
          if (response != "ACK") {
            std::cerr << "Upload failed for " << filename << std::endl;
          }
        } else if (response == "SKIP") {
          std::cout << "Skipping " << filename << " (Duplicate)" << std::endl;
        } else {
          std::cerr << "Unexpected response: " << response << std::endl;
        }
      }

      sendMessage("BATCH_END");
      receiveMessage(); // ACK

      photosSent += currentBatchSize;
    }

    sendMessage("END_SESSION");
    receiveMessage(); // ACK

    std::cout << "\n=== Session Complete ===" << std::endl;
  }

  void disconnect() { socket_.close(); }

private:
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

  std::string host_;
  int port_;
  boost::asio::io_context io_context_;
  tcp::socket socket_;
};

int main(int argc, char *argv[]) {
  try {
    po::options_description desc("Mock Client Options");
    desc.add_options()("help,h", "Help")(
        "host", po::value<std::string>()->default_value("localhost"),
        "Host")("port,p", po::value<int>()->default_value(50505), "Port")(
        "photos,n", po::value<int>()->default_value(5), "Num Photos")(
        "device-id,d", po::value<std::string>()->default_value("mock_client"),
        "Device ID");

    po::variables_map vm;
    po::store(po::parse_command_line(argc, argv, desc), vm);
    po::notify(vm);

    if (vm.count("help")) {
      std::cout << desc << std::endl;
      return 0;
    }

    MockClient client(vm["host"].as<std::string>(), vm["port"].as<int>());
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
