#include "AuthenticationManager.h"
#include <gtest/gtest.h>
#include <regex>


// Test password hashing
TEST(AuthenticationManagerTest, HashPasswordGeneratesValidHash) {
  std::string password = "test_password_123";
  std::string hash = AuthenticationManager::hashPassword(password);

  // Hash should start with $pbkdf2$
  EXPECT_EQ(hash.substr(0, 8), "$pbkdf2$");

  // Hash should have 4 parts separated by $
  int dollarCount = 0;
  for (char c : hash) {
    if (c == '$')
      dollarCount++;
  }
  EXPECT_EQ(dollarCount, 4);
}

// Test password verification with correct password
TEST(AuthenticationManagerTest, VerifyPasswordCorrectPassword) {
  std::string password = "my_secure_password";
  std::string hash = AuthenticationManager::hashPassword(password);

  EXPECT_TRUE(AuthenticationManager::verifyPassword(password, hash));
}

// Test password verification with incorrect password
TEST(AuthenticationManagerTest, VerifyPasswordIncorrectPassword) {
  std::string password = "correct_password";
  std::string hash = AuthenticationManager::hashPassword(password);

  EXPECT_FALSE(AuthenticationManager::verifyPassword("wrong_password", hash));
}

// Test that same password generates different hashes (due to random salt)
TEST(AuthenticationManagerTest, SamePasswordDifferentHashes) {
  std::string password = "same_password";
  std::string hash1 = AuthenticationManager::hashPassword(password);
  std::string hash2 = AuthenticationManager::hashPassword(password);

  EXPECT_NE(hash1, hash2); // Different salts = different hashes

  // But both should verify correctly
  EXPECT_TRUE(AuthenticationManager::verifyPassword(password, hash1));
  EXPECT_TRUE(AuthenticationManager::verifyPassword(password, hash2));
}

// Test session token generation
TEST(AuthenticationManagerTest, GenerateSessionTokenFormat) {
  std::string token = AuthenticationManager::generateSessionToken();

  // Token should be 64 characters (32 bytes hex-encoded)
  EXPECT_EQ(token.length(), 64);

  // Token should only contain hex characters
  std::regex hexPattern("^[0-9a-f]{64}$");
  EXPECT_TRUE(std::regex_match(token, hexPattern));
}

// Test that session tokens are unique
TEST(AuthenticationManagerTest, GenerateSessionTokenUnique) {
  std::string token1 = AuthenticationManager::generateSessionToken();
  std::string token2 = AuthenticationManager::generateSessionToken();

  EXPECT_NE(token1, token2);
}

// Test expiration timestamp calculation
TEST(AuthenticationManagerTest, CalculateExpiresAtFormat) {
  std::string expiresAt = AuthenticationManager::calculateExpiresAt(60);

  // Should be ISO8601 format: YYYY-MM-DDTHH:MM:SSZ
  std::regex iso8601Pattern(R"(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z)");
  EXPECT_TRUE(std::regex_match(expiresAt, iso8601Pattern));
}

// Test different cost factors
TEST(AuthenticationManagerTest, DifferentCostFactors) {
  std::string password = "test_password";

  // Test with cost 10 (faster, less secure)
  std::string hash10 = AuthenticationManager::hashPassword(password, 10);
  EXPECT_TRUE(AuthenticationManager::verifyPassword(password, hash10));

  // Test with cost 12 (default)
  std::string hash12 = AuthenticationManager::hashPassword(password, 12);
  EXPECT_TRUE(AuthenticationManager::verifyPassword(password, hash12));

  // Hashes should be different
  EXPECT_NE(hash10, hash12);
}
