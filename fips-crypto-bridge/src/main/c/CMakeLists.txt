# DEPRECATED — see proposals/fips-discovery-2026-04-17.md

# CMake build script for the OpenSSL FIPS C Bridge library.
#
# This script is invoked by the Gradle build system (via the externalNativeBuild block)
# to compile the C source file (`openssl_fips_bridge.c`) into a shared library
# that can be loaded by the Rust JNI bridge. This version is tailored for the
# Client-Only architecture.

# Specify the minimum version of CMake required to build this project.
cmake_minimum_required(VERSION 3.22.1)

# Define the project name. This is for organizational purposes within CMake.
project(openssl_fips_bridge_project C)

# --- Find OpenSSL Libraries ---
# The path to the pre-compiled OpenSSL libraries is passed from the Gradle script
# as a definition: -DOPENSSL_LIBS_DIR=...
# We use this path to locate the necessary libcrypto.so file.

# Set the path to the OpenSSL include directory.
# Assumes a standard layout where 'include/' is at the same level as 'lib/'.
set(OPENSSL_INCLUDE_DIR "${OPENSSL_LIBS_DIR}/../include" CACHE PATH "Path to OpenSSL include dir")

# Find the specific libcrypto.so file for the target architecture.
# ANDROID_ABI is a variable provided by the Android Gradle Plugin.
find_library(
    CRYPTO_LIBRARY
    NAMES crypto
    HINTS "${OPENSSL_LIBS_DIR}/${ANDROID_ABI}"
    NO_DEFAULT_PATH
)

if(NOT CRYPTO_LIBRARY)
    message(FATAL_ERROR "Could not find libcrypto.so in ${OPENSSL_LIBS_DIR}/${ANDROID_ABI}")
endif()

message(STATUS "Found libcrypto.so at: ${CRYPTO_LIBRARY}")
message(STATUS "Using OpenSSL includes from: ${OPENSSL_INCLUDE_DIR}")


# --- Build the C Bridge Library ---
# This section defines our custom C bridge as a shared library.

add_library(
    # The name of the library we are building. This will produce `libopenssl_fips_bridge.so`.
    # This name is referenced in the `ldLibs` section of the Gradle script for the Rust build.
    openssl_fips_bridge

    # Specifies that this is a shared library (.so file).
    SHARED

    # The source file(s) for this library.
    openssl_fips_bridge.c
)

# --- Link Libraries ---
# This section links our C bridge library against the OpenSSL library we found.

target_link_libraries(
    # The library to link against.
    openssl_fips_bridge

    # Link against the full path to libcrypto.so that we found earlier.
    ${CRYPTO_LIBRARY}

    # Also link against the standard Android log library for debug output.
    log
)

# --- Set Include Directories ---
# Tell the C compiler where to find the header files (e.g., <openssl/evp.h>).

target_include_directories(
    openssl_fips_bridge
    PRIVATE
    ${OPENSSL_INCLUDE_DIR}
)
