#ifndef AUTHENTICATION_MANAGER_H
#define AUTHENTICATION_MANAGER_H

#include <string>

/**
 * AuthenticationManager handles password hashing and session token generation
 * for the PhotoSync admin dashboard.
 */
class AuthenticationManager {
public:
  /**
   * Hash a password using bcrypt
   * @param password Plain text password
   * @param cost Bcrypt cost factor (default: 12)
   * @return Bcrypt hash string
   */
  static std::string hashPassword(const std::string &password, int cost = 12);

  /**
   * Verify a password against a bcrypt hash
   * @param password Plain text password to verify
   * @param hash Bcrypt hash to compare against
   * @return true if password matches hash
   */
  static bool verifyPassword(const std::string &password,
                             const std::string &hash);

  /**
   * Generate a cryptographically secure session token
   * @return 64-character hex-encoded token (32 random bytes)
   */
  static std::string generateSessionToken();

  /**
   * Calculate session expiration timestamp
   * @param timeoutSeconds Number of seconds until expiration
   * @return ISO8601 formatted timestamp
   */
  static std::string calculateExpiresAt(int timeoutSeconds);
};

#endif // AUTHENTICATION_MANAGER_H
