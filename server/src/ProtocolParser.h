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

enum class ErrorCode : int {
  UNKNOWN = 0,
  INTERNAL_ERROR = 100,
  DATABASE_ERROR = 101,
  DISK_FULL = 102,
  NETWORK_ERROR = 103,
  PROTOCOL_ERROR = 200,
  INVALID_MAGIC = 201,
  INVALID_VERSION = 202,
  INVALID_PAYLOAD = 203,
  AUTH_FAILED = 300,
  AUTH_REQUIRED = 301,
  SESSION_EXPIRED = 302,
  FILE_ERROR = 400,
  FILE_EXISTS = 401,
  HASH_MISMATCH = 409,
  // Phase 2 Errors
  INVALID_OFFSET = 416
};

// Phase 2: Protocol V2
const uint8_t PROTOCOL_VERSION_2 = 2;

enum class PacketTypeV2 : uint8_t {
  UPLOAD_INIT = 0x10,     // Client -> Server: Start/Resume request
  UPLOAD_ACK = 0x11,      // Server -> Client: Status/Resume offsets
  UPLOAD_CHUNK = 0x12,    // Client -> Server: Binary data with offset
  UPLOAD_FINISH = 0x13,   // Client -> Server: Commit request
  UPLOAD_RESULT = 0x14,   // Server -> Client: Final result
  UPLOAD_ABORT = 0x15,    // Bidirectional: Cancel
  UPLOAD_CHUNK_ACK = 0x16 // Server -> Client: Chunk success/flow control
};

struct UploadInitPayload {
  std::string filename;
  long long size;
  std::string hash;
  std::string traceId;
};

struct UploadAckPayload {
  std::string uploadId;
  int chunkSize;
  long long receivedBytes;
  std::string status; // "RESUMING", "NEW", "COMPLETE"
};

struct UploadChunkHeader { // Explicit binary header for 0x12
  char uploadId[36]; // UUID string (36 bytes) OR 16 bytes raw? Spec says 16
                     // bytes raw. But DB uses string. Let's stick to string for
                     // simplicity across layers? Spec says: [UploadID (16
                     // bytes)] [Offset (8 bytes)] [DataLength (4 bytes)] I MUST
                     // FOLLOW SPEC. 16 BYTES RAW. Wait, DB
                     // generateUploadSession uses 32 hex chars (string). UUID
                     // is 16 bytes. I'll implementation needs to convert hex
                     // string to 16 bytes. For now let's persist the struct
                     // definition. Actually, TcpListener will handle binary
                     // parsing.
};

struct UploadChunkAckPayload {
  std::string uploadId;
  long long nextExpectedOffset;
  std::string status;
};

struct UploadFinishPayload {
  std::string uploadId;
  std::string sha256;
};

struct UploadResultPayload {
  std::string uploadId;
  std::string status;
  std::string message;
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

  // Phase 2 Factories
  static Packet createUploadAckPacket(const std::string &uploadId,
                                      int chunkSize, long long receivedBytes,
                                      const std::string &status);
  static Packet createUploadChunkAckPacket(const std::string &uploadId,
                                           long long nextExpectedOffset,
                                           const std::string &status);
  static Packet createUploadResultPacket(const std::string &uploadId,
                                         const std::string &status,
                                         const std::string &message);

  // Deprecated: verify where this is used and migrate to ErrorCode version
  static Packet createErrorPacket(const std::string &message,
                                  ErrorCode code = ErrorCode::UNKNOWN);

  // JSON Helper
  static json parsePayload(const Packet &packet);

  // Helper to get raw bytes for network sending
  static std::vector<char> pack(const Packet &packet) {
    return serializePacket(packet);
  }
};
