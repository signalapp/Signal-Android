cmake_minimum_required(VERSION 3.22.1)

project(signal-webp)

add_compile_options(-Oz)
set(CMAKE_POLICY_DEFAULT_CMP0077 NEW)

set(BUILD_SHARED_LIBS ON)
set(WEBP_BUILD_ANIM_UTILS OFF)
set(WEBP_BUILD_CWEBP OFF)
set(WEBP_BUILD_DWEBP OFF)
set(WEBP_BUILD_VWEBP OFF)
set(WEBP_BUILD_WEBPINFO OFF)
set(WEBP_BUILD_LIBWEBPMUX OFF)
set(WEBP_BUILD_EXTRAS OFF)
set(WEBP_NEAR_LOSSLESS, OFF)

file(GLOB SOURCES ${CMAKE_SOURCE_DIR}/*.cpp)

add_subdirectory(${LIBWEBP_PATH} ${CMAKE_CURRENT_BINARY_DIR}/libwebp)

add_library(signalwebp SHARED ${SOURCES})
target_include_directories(signalwebp PRIVATE ${LIBWEBP_PATH}/src)
target_link_libraries(signalwebp webpdemux)
