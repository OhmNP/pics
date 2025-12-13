#pragma once

#include <boost/asio.hpp>
#include <boost/asio/deadline_timer.hpp>
#include <boost/bind/bind.hpp>
#include <string>
#include <thread>
#include <vector>

using boost::asio::ip::udp;

class UdpBroadcaster {
public:
  UdpBroadcaster(boost::asio::io_context &io_context, int port);
  void start();

private:
  void doBroadcast();
  void handleSend(const boost::system::error_code &error,
                  std::size_t bytes_transferred);

  udp::socket socket_;
  udp::endpoint broadcastEndpoint_;
  boost::asio::deadline_timer timer_;
  std::vector<char> message_;
  int port_;
};
