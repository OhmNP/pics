#include "../src/ProtocolParser.h"
#include <gtest/gtest.h>
#include <vector>

class ProtocolEdgeTest : public ::testing::Test {};

TEST_F(ProtocolEdgeTest, InvalidMagicHeader) {
  // 1. Create a header with Wrong Magic (e.g. 0xFFFF)
  std::vector<char> badHeader(sizeof(PacketHeader), 0);
  PacketHeader *hdr = reinterpret_cast<PacketHeader *>(badHeader.data());
  hdr->magic = 0xFFFF; // Invalid
  hdr->version = 1;
  hdr->type = PacketType::HEARTBEAT;
  hdr->payloadLength = 0;

  // 2. Deserialize should THROW or return PROTOCOL_ERROR
  // Assuming implementation throws on magic mismatch
  EXPECT_THROW(
      { auto p = ProtocolParser::deserializePacketHeader(badHeader); },
      std::runtime_error);
}

TEST_F(ProtocolEdgeTest, UnsupportedVersion) {
  std::vector<char> badHeader(sizeof(PacketHeader), 0);
  PacketHeader *hdr = reinterpret_cast<PacketHeader *>(badHeader.data());
  hdr->magic = PROTOCOL_MAGIC;
  hdr->version = 99; // Invalid version

  // Should reject version mismatch
  EXPECT_THROW(
      { auto p = ProtocolParser::deserializePacketHeader(badHeader); },
      std::runtime_error);
}

TEST_F(ProtocolEdgeTest, FuzzData) {
  // Random garbage
  std::vector<char> garbage = {0x00, 0x11, 0x22, 0x33, 0x44}; // Too short
  EXPECT_THROW(
      { auto p = ProtocolParser::deserializePacketHeader(garbage); },
      std::runtime_error);
}

TEST_F(ProtocolEdgeTest, JsonPayloadBadFormat) {
  // Create a valid header saying payload is JSON
  Packet packet;
  packet.header.type = PacketType::METADATA;

  // But payload is NOT valid JSON
  std::string badJson = "{ invalid_json : ";
  packet.payload.assign(badJson.begin(), badJson.end());
  packet.header.payloadLength = packet.payload.size();

  // Trying to parse payload should return empty JSON (caught internally)
  auto j = ProtocolParser::parsePayload(packet);
  EXPECT_TRUE(j.empty());
}

TEST_F(ProtocolEdgeTest, EmptyPayloadParsing) {
  Packet packet;
  packet.header.type = PacketType::METADATA;
  packet.payload.clear();
  packet.header.payloadLength = 0;

  // Parsing empty payload should return empty JSON
  auto j = ProtocolParser::parsePayload(packet);
  EXPECT_TRUE(j.empty());
}
