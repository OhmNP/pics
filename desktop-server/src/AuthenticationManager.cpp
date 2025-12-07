#include "AuthenticationManager.h"
#include <chrono>
#include <iomanip>
#include <openssl/evp.h>
#include <openssl/rand.h>
#include <openssl/sha.h>
#include <sstream>
#include <stdexcept>


// Simple bcrypt-like implementation using PBKDF2
// Note: For production, consider using a dedicated bcrypt library
std::string AuthenticationManager::hashPassword(const std::string &password,
                                                int cost) {
  // Generate random salt (16 bytes)
  unsigned char salt[16];
  if (RAND_bytes(salt, sizeof(salt)) != 1) {
    throw std::runtime_error("Failed to generate random salt");
  }

  // Convert cost to iterations (2^cost)
  int iterations = 1 << cost; // 2^12 = 4096 iterations for cost=12

  // Derive key using PBKDF2-HMAC-SHA256
  unsigned char hash[32]; // 256-bit hash
  if (PKCS5_PBKDF2_HMAC(password.c_str(), password.length(), salt, sizeof(salt),
                        iterations, EVP_sha256(), sizeof(hash), hash) != 1) {
    throw std::runtime_error("Failed to hash password");
  }

  // Format: $pbkdf2$cost$salt$hash (all hex-encoded)
  std::stringstream ss;
  ss << "$pbkdf2$" << cost << "$";

  // Append salt (hex)
  for (size_t i = 0; i < sizeof(salt); i++) {
    ss << std::hex << std::setw(2) << std::setfill('0') << (int)salt[i];
  }
  ss << "$";

  // Append hash (hex)
  for (size_t i = 0; i < sizeof(hash); i++) {
    ss << std::hex << std::setw(2) << std::setfill('0') << (int)hash[i];
  }

  return ss.str();
}

bool AuthenticationManager::verifyPassword(const std::string &password,
                                           const std::string &hash) {
  // Parse hash format: $pbkdf2$cost$salt$hash
  if (hash.substr(0, 8) != "$pbkdf2$") {
    return false;
  }

  size_t pos1 = 8;
  size_t pos2 = hash.find('$', pos1);
  if (pos2 == std::string::npos)
    return false;

  int cost = std::stoi(hash.substr(pos1, pos2 - pos1));

  pos1 = pos2 + 1;
  pos2 = hash.find('$', pos1);
  if (pos2 == std::string::npos)
    return false;

  std::string saltHex = hash.substr(pos1, pos2 - pos1);
  std::string hashHex = hash.substr(pos2 + 1);

  // Convert salt from hex
  unsigned char salt[16];
  for (size_t i = 0; i < 16; i++) {
    std::string byteStr = saltHex.substr(i * 2, 2);
    salt[i] = (unsigned char)std::stoi(byteStr, nullptr, 16);
  }

  // Derive key with same parameters
  int iterations = 1 << cost;
  unsigned char derivedHash[32];
  if (PKCS5_PBKDF2_HMAC(password.c_str(), password.length(), salt, sizeof(salt),
                        iterations, EVP_sha256(), sizeof(derivedHash),
                        derivedHash) != 1) {
    return false;
  }

  // Convert derived hash to hex
  std::stringstream ss;
  for (size_t i = 0; i < sizeof(derivedHash); i++) {
    ss << std::hex << std::setw(2) << std::setfill('0') << (int)derivedHash[i];
  }

  // Compare hashes (constant-time comparison would be better for production)
  return ss.str() == hashHex;
}

std::string AuthenticationManager::generateSessionToken() {
  // Generate 32 random bytes
  unsigned char randomBytes[32];
  if (RAND_bytes(randomBytes, sizeof(randomBytes)) != 1) {
    throw std::runtime_error("Failed to generate random token");
  }

  // Convert to hex string (64 characters)
  std::stringstream ss;
  for (size_t i = 0; i < sizeof(randomBytes); i++) {
    ss << std::hex << std::setw(2) << std::setfill('0') << (int)randomBytes[i];
  }

  return ss.str();
}

std::string AuthenticationManager::calculateExpiresAt(int timeoutSeconds) {
  // Get current time
  auto now = std::chrono::system_clock::now();

  // Add timeout
  auto expiresAt = now + std::chrono::seconds(timeoutSeconds);

  // Convert to time_t
  std::time_t expiresTime = std::chrono::system_clock::to_time_t(expiresAt);

  // Format as ISO8601: YYYY-MM-DDTHH:MM:SSZ
  std::tm tm;
  gmtime_s(&tm, &expiresTime); // Windows-specific, use gmtime_r on Linux

  std::stringstream ss;
  ss << std::put_time(&tm, "%Y-%m-%dT%H:%M:%SZ");

  return ss.str();
}
