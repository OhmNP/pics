#include "UdpBroadcaster.h"
#include "ConfigManager.h"
#include "Logger.h"
#include "ProtocolParser.h"
#include <boost/bind/bind.hpp>
#include <iostream>

UdpBroadcaster::UdpBroadcaster(boost::asio::io_context &io_context, int port)
    : socket_(io_context, udp::endpoint(udp::v4(), 0)),
      broadcastEndpoint_(boost::asio::ip::address_v4::broadcast(), port),
      timer_(io_context), port_(port) {
  socket_.set_option(udp::socket::reuse_address(true));
  socket_.set_option(boost::asio::socket_base::broadcast(true));

  // Create Discovery Packet
  std::string serverName = ConfigManager::getInstance().getServerName();
  Packet packet = ProtocolParser::createDiscoveryPacket(port, serverName);

  // Serialize once + cache
  message_ = ProtocolParser::pack(packet);
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
