cmake_minimum_required(VERSION 3.15)
project(PhotoSyncServer VERSION 1.0.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_BUILD_TYPE Debug)

# Define Windows version for Boost.Asio
add_definitions(-D_WIN32_WINNT=0x0601)

find_package(Boost REQUIRED COMPONENTS system)
find_package(SQLite3 REQUIRED)
find_package(nlohmann_json REQUIRED)
find_package(Crow CONFIG REQUIRED)
find_package(OpenSSL REQUIRED)
# TODO: Fix GTest configuration
# find_package(GTest CONFIG REQUIRED)

# Main server executable
add_executable(PhotoSyncServer
    src/main.cpp
    src/TcpListener.cpp
    src/ConfigManager.cpp
    src/Logger.cpp
    src/DatabaseManager.cpp
    src/ProtocolParser.cpp
    src/ApiServer.cpp
    src/FileManager.cpp
    src/ConnectionManager.cpp
    src/UdpBroadcaster.cpp
    src/AuthenticationManager.cpp
    src/ThumbnailGenerator.cpp
    src/ApiServer_thumbnails_impl.cpp
)

target_include_directories(PhotoSyncServer PRIVATE ${Boost_INCLUDE_DIRS} ${SQLite3_INCLUDE_DIRS} ${OPENSSL_INCLUDE_DIR})
target_link_libraries(PhotoSyncServer PRIVATE ${Boost_LIBRARIES} ${SQLite3_LIBRARIES} nlohmann_json::nlohmann_json Crow::Crow OpenSSL::SSL OpenSSL::Crypto)

# TODO: Re-enable tests after fixing GTest configuration
# # Test executable
# add_executable(PhotoSyncTests
#     tests/test_main.cpp
#     tests/test_authentication.cpp
#     tests/test_database_auth.cpp
#     src/AuthenticationManager.cpp
#     src/DatabaseManager.cpp
#     src/Logger.cpp
#     src/ConfigManager.cpp
# )
# 
# target_include_directories(PhotoSyncTests PRIVATE 
#     ${CMAKE_SOURCE_DIR}/src
#     ${Boost_INCLUDE_DIRS} 
#     ${SQLite3_INCLUDE_DIRS} 
#     ${OPENSSL_INCLUDE_DIR}
# )
# 
# target_link_libraries(PhotoSyncTests PRIVATE 
#     GTest::gtest 
#     GTest::gtest_main
#     ${Boost_LIBRARIES} 
#     ${SQLite3_LIBRARIES} 
#     nlohmann_json::nlohmann_json 
#     OpenSSL::SSL 
#     OpenSSL::Crypto
# )
# 
# # Enable testing
# enable_testing()
# add_test(NAME PhotoSyncTests COMMAND PhotoSyncTests)