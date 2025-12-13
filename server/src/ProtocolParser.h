#pragma once

#include "DatabaseManager.h"
#include <cstdint>
#include <nlohmann/json.hpp>
#include <string>
#include <vector>


using json = nlohmann::json;

// Protocol Constants
const uint16_t PROTOCOL_MAGIC = 0x5048; // "PH"
const uint8_t PROTOCOL_VERSION = 1;

enum class PacketType : uint8_t {
  DISCOVERY = 0x01,
  PAIRING_REQUEST = 0x02,
  PAIRING_RESPONSE = 0x03,
  HEARTBEAT = 0x04,
  METADATA = 0x05,
  TRANSFER_READY = 0x06,
  FILE_CHUNK = 0x07,
  TRANSFER_COMPLETE = 0x08,
  PROTOCOL_ERROR = 0x09
};

struct PacketHeader {
  uint16_t magic = PROTOCOL_MAGIC;
  uint8_t version = PROTOCOL_VERSION;
  PacketType type;
  uint32_t payloadLength;
};

struct Packet {
  PacketHeader header;
  std::vector<char> payload;
};

class ProtocolParser {
public:
  // Serialization
  static std::vector<char> serializePacket(const Packet &packet);
  static Packet deserializePacketHeader(const std::vector<char> &headerData);

  // High-level Packet Creators
  static Packet createDiscoveryPacket(int port, const std::string &name);
  static Packet createPairingResponse(int sessionId, bool success,
                                      const std::string &msg = "");
  static Packet createHeartbeatPacket();
  static Packet createTransferReadyPacket(long long offset);
  static Packet createTransferCompletePacket(const std::string &fileHash);
  static Packet createErrorPacket(const std::string &message);

  // JSON Helper
  static json parsePayload(const Packet &packet);

  // Helper to get raw bytes for network sending
  static std::vector<char> pack(const Packet &packet) {
    return serializePacket(packet);
  }
};
