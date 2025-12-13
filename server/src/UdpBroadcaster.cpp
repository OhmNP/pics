#include "UdpBroadcaster.h"
#include "ConfigManager.h"
#include "Logger.h"
#include <boost/date_time/posix_time/posix_time.hpp>
#include <iostream>
#include <nlohmann/json.hpp>

using json = nlohmann::json;

UdpBroadcaster::UdpBroadcaster(boost::asio::io_context &io_context, int port)
    : socket_(io_context, udp::endpoint(udp::v4(), 0)),
      broadcastEndpoint_(boost::asio::ip::address_v4::broadcast(), port),
      timer_(io_context), port_(port) {
  socket_.set_option(udp::socket::reuse_address(true));
  socket_.set_option(boost::asio::socket_base::broadcast(true));

  // Create JSON discovery message
  json msg;
  msg["service"] = "photosync";
  msg["port"] = port;
  msg["serverName"] =
      ConfigManager::getInstance().getServerName(); // Add server name
  message_ = msg.dump();
}

void UdpBroadcaster::start() {
  LOG_INFO("Starting UDP Broadcaster on port " + std::to_string(port_));
  doBroadcast();
}

void UdpBroadcaster::doBroadcast() {
  socket_.async_send_to(
      boost::asio::buffer(message_), broadcastEndpoint_,
      boost::bind(&UdpBroadcaster::handleSend, this,
                  boost::asio::placeholders::error,
                  boost::asio::placeholders::bytes_transferred));

  // Schedule next broadcast in 2 seconds
  timer_.expires_from_now(boost::posix_time::seconds(2));
  timer_.async_wait(boost::bind(&UdpBroadcaster::doBroadcast, this));
}

void UdpBroadcaster::handleSend(const boost::system::error_code &error,
                                std::size_t /*bytes_transferred*/) {
  if (error) {
    LOG_ERROR("Broadcast error: " + error.message());
  }
}
