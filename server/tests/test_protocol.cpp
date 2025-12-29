#include "../src/ProtocolParser.h"
#include <gtest/gtest.h>
#include <vector>

class ProtocolParserTest : public ::testing::Test {
protected:
  void SetUp() override {
    // Setup code if needed
  }
};

// Helper to get header size
constexpr size_t HEADER_SIZE = sizeof(PacketHeader);

TEST_F(ProtocolParserTest, CreateAndParseHeartbeat) {
  auto packet = ProtocolParser::createHeartbeatPacket();

  // Serialize
  auto bytes = ProtocolParser::serializePacket(packet);
  EXPECT_GE(bytes.size(), HEADER_SIZE);

  // Deserialize header
  std::vector<char> headerBytes(bytes.begin(), bytes.begin() + HEADER_SIZE);
  auto packetFromHeader = ProtocolParser::deserializePacketHeader(headerBytes);

  EXPECT_EQ(packetFromHeader.header.type, PacketType::HEARTBEAT);
  EXPECT_EQ(packetFromHeader.header.payloadLength, 0);
}

TEST_F(ProtocolParserTest, ManualPacketCreationMetadata) {
  json payload = {
      {"filename", "test.jpg"}, {"size", 1024}, {"hash", "abc123hash"}};
  std::string jsonStr = payload.dump();

  Packet packet;
  packet.header.type = PacketType::METADATA;
  packet.payload.assign(jsonStr.begin(), jsonStr.end());
  packet.header.payloadLength = packet.payload.size();

  auto bytes = ProtocolParser::serializePacket(packet);

  // Deserialize header
  std::vector<char> headerBytes(bytes.begin(), bytes.begin() + HEADER_SIZE);
  auto headerPacket = ProtocolParser::deserializePacketHeader(headerBytes);

  EXPECT_EQ(headerPacket.header.type, PacketType::METADATA);
  EXPECT_EQ(headerPacket.header.payloadLength, jsonStr.size());

  // Test helper to reconstruct full packet for payload parsing
  headerPacket.payload.assign(bytes.begin() + HEADER_SIZE, bytes.end());
  json parsedPayload = ProtocolParser::parsePayload(headerPacket);

  EXPECT_EQ(parsedPayload["filename"], "test.jpg");
  EXPECT_EQ(parsedPayload["size"], 1024);
  EXPECT_EQ(parsedPayload["hash"], "abc123hash");
}

TEST_F(ProtocolParserTest, CreateAndParsePairingResponse) {
  auto packet = ProtocolParser::createPairingResponse(123, true, "Success");
  auto bytes = ProtocolParser::serializePacket(packet);

  std::vector<char> headerBytes(bytes.begin(), bytes.begin() + HEADER_SIZE);
  auto headerPacket = ProtocolParser::deserializePacketHeader(headerBytes);

  EXPECT_EQ(headerPacket.header.type, PacketType::PAIRING_RESPONSE);

  headerPacket.payload.assign(bytes.begin() + HEADER_SIZE, bytes.end());
  json parsedPayload = ProtocolParser::parsePayload(headerPacket);

  EXPECT_TRUE(parsedPayload["success"]);
  EXPECT_EQ(parsedPayload["sessionId"], 123);
}

TEST_F(ProtocolParserTest, FileChunkHeader) {
  size_t payloadSize = 1024;
  Packet packet;
  packet.header.magic = 0x5048; // "PH" as per header
  packet.header.version = 1;
  packet.header.type = PacketType::FILE_CHUNK;
  packet.header.payloadLength = payloadSize;
  packet.payload.resize(payloadSize, 0x0); // Dummy data

  auto bytes = ProtocolParser::serializePacket(packet);
  EXPECT_EQ(bytes.size(), HEADER_SIZE + payloadSize);

  std::vector<char> headerBytes(bytes.begin(), bytes.begin() + HEADER_SIZE);
  auto headerPacket = ProtocolParser::deserializePacketHeader(headerBytes);
  EXPECT_EQ(headerPacket.header.type, PacketType::FILE_CHUNK);
  EXPECT_EQ(headerPacket.header.payloadLength, payloadSize);
}
