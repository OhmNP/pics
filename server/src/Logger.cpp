#include "Logger.h"
#include <chrono>
#include <iomanip>
#include <iostream>
#include <sstream>


// Undefine Windows macros that conflict with our enums
#ifdef ERROR
#undef ERROR
#endif

Logger &Logger::getInstance() {
  static Logger instance;
  return instance;
}

void Logger::init(const std::string &logFile, LogLevel level,
                  bool consoleOutput) {
  std::lock_guard<std::mutex> lock(mutex_);
  minLevel_ = level;
  consoleOutput_ = consoleOutput;

  if (!logFile.empty()) {
    logFile_.open(logFile, std::ios::app);
    if (!logFile_.is_open()) {
      std::cerr << "Failed to open log file: " << logFile << std::endl;
    }
  }
}

Logger::~Logger() {
  if (logFile_.is_open()) {
    logFile_.close();
  }
}

void Logger::log(LogLevel level, const std::string &message) {
  if (level < minLevel_) {
    return;
  }

  std::lock_guard<std::mutex> lock(mutex_);

  std::string timestamp = getCurrentTimestamp();
  std::string levelStr = levelToString(level);
  std::string logMessage = "[" + timestamp + "] [" + levelStr + "] " + message;

  if (consoleOutput_) {
    std::cout << logMessage << std::endl;
  }

  if (logFile_.is_open()) {
    logFile_ << logMessage << std::endl;
    logFile_.flush();
  }
}

void Logger::debug(const std::string &message) {
  log(LogLevel::DEBUG, message);
}

void Logger::info(const std::string &message) { log(LogLevel::INFO, message); }

void Logger::warn(const std::string &message) { log(LogLevel::WARN, message); }

void Logger::error(const std::string &message) {
  log(LogLevel::ERROR, message);
}

void Logger::fatal(const std::string &message) {
  log(LogLevel::FATAL, message);
}

std::string Logger::levelToString(LogLevel level) {
  switch (level) {
  case LogLevel::DEBUG:
    return "DEBUG";
  case LogLevel::INFO:
    return "INFO ";
  case LogLevel::WARN:
    return "WARN ";
  case LogLevel::ERROR:
    return "ERROR";
  case LogLevel::FATAL:
    return "FATAL";
  default:
    return "UNKNOWN";
  }
}

std::string Logger::getCurrentTimestamp() {
  auto now = std::chrono::system_clock::now();
  auto time = std::chrono::system_clock::to_time_t(now);
  auto ms = std::chrono::duration_cast<std::chrono::milliseconds>(
                now.time_since_epoch()) %
            1000;

  std::stringstream ss;
  ss << std::put_time(std::localtime(&time), "%Y-%m-%d %H:%M:%S");
  ss << '.' << std::setfill('0') << std::setw(3) << ms.count();
  return ss.str();
}
