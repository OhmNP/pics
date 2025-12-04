#pragma once

#include <fstream>
#include <memory>
#include <mutex>
#include <string>


// Undefine Windows macros that conflict with our enum
#ifdef ERROR
#undef ERROR
#endif

enum class LogLevel { DEBUG, INFO, WARN, ERROR, FATAL };

class Logger {
public:
  static Logger &getInstance();

  void init(const std::string &logFile, LogLevel level, bool consoleOutput);
  void log(LogLevel level, const std::string &message);

  void debug(const std::string &message);
  void info(const std::string &message);
  void warn(const std::string &message);
  void error(const std::string &message);
  void fatal(const std::string &message);

private:
  Logger() = default;
  ~Logger();

  Logger(const Logger &) = delete;
  Logger &operator=(const Logger &) = delete;

  std::string levelToString(LogLevel level);
  std::string getCurrentTimestamp();

  std::ofstream logFile_;
  LogLevel minLevel_ = LogLevel::INFO;
  bool consoleOutput_ = true;
  std::mutex mutex_;
};

// Convenience macros
#define LOG_DEBUG(msg) Logger::getInstance().debug(msg)
#define LOG_INFO(msg) Logger::getInstance().info(msg)
#define LOG_WARN(msg) Logger::getInstance().warn(msg)
#define LOG_ERROR(msg) Logger::getInstance().error(msg)
#define LOG_FATAL(msg) Logger::getInstance().fatal(msg)
