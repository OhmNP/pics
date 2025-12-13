#include "ProtocolParser.h"
#include "ConfigManager.h"
#include "Logger.h"
#include <cstring>
#include <iostream>

#ifdef _WIN32
#include <winsock2.h>
#else
#include <arpa/inet.h>
#endif

// Helper for endianness
static uint32_t toNetworkOrder(uint32_t val) { return htonl(val); }
static uint32_t fromNetworkOrder(uint32_t val) { return ntohl(val); }

std::vector<char> ProtocolParser::serializePacket(const Packet &packet) {
  std::vector<char> buffer;
  size_t totalSize = sizeof(uint16_t) + sizeof(uint8_t) + sizeof(uint8_t) +
                     sizeof(uint32_t) + packet.payload.size();
  buffer.resize(totalSize);

  char *ptr = buffer.data();

  // manually pack to avoid struct padding implementation differences
  uint16_t magic = htons(packet.header.magic);
  std::memcpy(ptr, &magic, sizeof(magic));
  ptr += sizeof(magic);

  std::memcpy(ptr, &packet.header.version, sizeof(uint8_t));
  ptr += sizeof(uint8_t);

  uint8_t type = static_cast<uint8_t>(packet.header.type);
  std::memcpy(ptr, &type, sizeof(uint8_t));
  ptr += sizeof(uint8_t);

  uint32_t len = toNetworkOrder(packet.header.payloadLength);
  std::memcpy(ptr, &len, sizeof(len));
  ptr += sizeof(len);

  if (!packet.payload.empty()) {
    std::memcpy(ptr, packet.payload.data(), packet.payload.size());
  }

  return buffer;
}

Packet
ProtocolParser::deserializePacketHeader(const std::vector<char> &headerData) {
  Packet packet;
  if (headerData.size() < 8) { // 2+1+1+4
    throw std::runtime_error("Header too short");
  }

  const char *ptr = headerData.data();

  uint16_t magic;
  std::memcpy(&magic, ptr, sizeof(magic));
  ptr += sizeof(magic);
  packet.header.magic = ntohs(magic);

  std::memcpy(&packet.header.version, ptr, sizeof(uint8_t));
  ptr += sizeof(uint8_t);

  uint8_t typeVal;
  std::memcpy(&typeVal, ptr, sizeof(uint8_t));
  ptr += sizeof(uint8_t);
  packet.header.type = static_cast<PacketType>(typeVal);

  uint32_t len;
  std::memcpy(&len, ptr, sizeof(len));
  ptr += sizeof(len);
  packet.header.payloadLength = fromNetworkOrder(len);

  return packet;
}

// Helpers
static Packet createJsonPacket(PacketType type, const json &j) {
  Packet p;
  p.header.magic = PROTOCOL_MAGIC;
  p.header.version = PROTOCOL_VERSION;
  p.header.type = type;

  std::string s = j.dump();
  p.payload.assign(s.begin(), s.end());
  p.header.payloadLength = (uint32_t)p.payload.size();
  return p;
}

Packet ProtocolParser::createDiscoveryPacket(int port,
                                             const std::string &name) {
  json j;
  j["service"] = "photosync";
  j["port"] = port;
  j["serverName"] = name;
  return createJsonPacket(PacketType::DISCOVERY, j);
}

Packet ProtocolParser::createPairingResponse(int sessionId, bool success,
                                             const std::string &msg) {
  json j;
  j["sessionId"] = sessionId;
  j["success"] = success;
  if (!msg.empty())
    j["message"] = msg;
  return createJsonPacket(PacketType::PAIRING_RESPONSE, j);
}

Packet ProtocolParser::createHeartbeatPacket() {
  Packet p;
  p.header.magic = PROTOCOL_MAGIC;
  p.header.version = PROTOCOL_VERSION;
  p.header.type = PacketType::HEARTBEAT;
  p.header.payloadLength = 0;
  return p;
}

Packet ProtocolParser::createTransferReadyPacket(long long offset) {
  json j;
  j["status"] = "READY";
  j["offset"] = offset;
  return createJsonPacket(PacketType::TRANSFER_READY, j);
}

Packet
ProtocolParser::createTransferCompletePacket(const std::string &fileHash) {
  json j;
  j["status"] = "COMPLETE";
  j["hash"] = fileHash;
  return createJsonPacket(PacketType::TRANSFER_COMPLETE, j);
}

Packet ProtocolParser::createErrorPacket(const std::string &message) {
  json j;
  j["error"] = message;
  return createJsonPacket(PacketType::PROTOCOL_ERROR, j);
}

json ProtocolParser::parsePayload(const Packet &packet) {
  if (packet.payload.empty())
    return json({});
  try {
    std::string s(packet.payload.begin(), packet.payload.end());
    return json::parse(s);
  } catch (...) {
    return json({});
  }
}
