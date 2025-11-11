cmake_minimum_required(VERSION 3.15)
project(PhotoSyncServer LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_BUILD_TYPE Debug)

find_package(Boost REQUIRED COMPONENTS system)
find_package(SQLite3 REQUIRED)

add_executable(PhotoSyncServer
    src/main.cpp
    src/TcpListener.cpp
)

target_include_directories(PhotoSyncServer PRIVATE ${Boost_INCLUDE_DIRS} ${SQLite3_INCLUDE_DIRS})
target_link_libraries(PhotoSyncServer PRIVATE ${Boost_LIBRARIES} ${SQLite3_LIBRARIES})