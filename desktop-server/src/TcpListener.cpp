#include <boost/asio.hpp>
#include <iostream>

using boost::asio::ip::tcp;

int main() {
    try {
        boost::asio::io_context io;
        tcp::acceptor acceptor(io, tcp::endpoint(tcp::v4(), 50505));
        std::cout << "PhotoSync C++ Server listening on port 50505..." << std::endl;

        for (;;) {
            tcp::socket socket(io);
            acceptor.accept(socket);
            std::array<char, 128> buf{};
            boost::system::error_code error;
            size_t len = socket.read_some(boost::asio::buffer(buf), error);
            if (!error) {
                std::string msg(buf.data(), len);
                std::cout << "Received: " << msg << std::endl;
                if (msg == "HELLO")
                    boost::asio::write(socket, boost::asio::buffer("OK"));
            }
        }
    } catch (std::exception &e) {
        std::cerr << "Error: " << e.what() << std::endl;
    }
    return 0;
}