cmake_minimum_required(VERSION 3.15)
project(MockClient LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)

find_package(Boost REQUIRED COMPONENTS system program_options)
find_package(OpenSSL REQUIRED)

add_executable(MockClient
    MockClient.cpp
)

target_include_directories(MockClient PRIVATE ${Boost_INCLUDE_DIRS})
target_link_libraries(MockClient PRIVATE 
    ${Boost_LIBRARIES}
    OpenSSL::SSL
    OpenSSL::Crypto
)
