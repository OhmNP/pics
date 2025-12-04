cmake_minimum_required(VERSION 3.15)
project(PhotoSyncServer LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_BUILD_TYPE Debug)

# Define Windows version for Boost.Asio
add_definitions(-D_WIN32_WINNT=0x0601)

cmake_minimum_required(VERSION 3.15)
project(PhotoSyncServer LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_BUILD_TYPE Debug)

# Define Windows version for Boost.Asio
add_definitions(-D_WIN32_WINNT=0x0601)

find_package(Boost REQUIRED COMPONENTS system)
find_package(SQLite3 REQUIRED)
find_package(nlohmann_json REQUIRED)
find_package(Crow CONFIG REQUIRED)
find_package(OpenSSL REQUIRED)

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
)

target_include_directories(PhotoSyncServer PRIVATE ${Boost_INCLUDE_DIRS} ${SQLite3_INCLUDE_DIRS} ${OPENSSL_INCLUDE_DIR})
target_link_libraries(PhotoSyncServer PRIVATE ${Boost_LIBRARIES} ${SQLite3_LIBRARIES} nlohmann_json::nlohmann_json Crow::Crow OpenSSL::SSL OpenSSL::Crypto)