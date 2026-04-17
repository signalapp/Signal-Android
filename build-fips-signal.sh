
#!/bin/bash
# DEPRECATED — see proposals/fips-discovery-2026-04-17.md

# FIPS Signal Android Build Script

set -e

echo "Starting FIPS Signal Android build..."

# Source environment setup
source ./setup-fips-env.sh

# Check if we're in the right directory
if [ ! -f "build.gradle.kts" ]; then
    echo "Error: Not in Signal Android project root directory"
    exit 1
fi

# Check if OpenSSL libraries are available
echo "Checking for OpenSSL libraries..."
if [ ! -f "fips-crypto-bridge/libs/openssl/arm64-v8a/libcrypto.so" ]; then
    echo "OpenSSL libraries not found. Building OpenSSL..."
    build_openssl_all
    copy_openssl_libs
else
    echo "OpenSSL libraries found."
fi

# Install required Rust targets if not already installed
echo "Ensuring Rust targets are installed..."
rustup target add aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android

# Build the debug APK
echo "Building debug APK..."
./gradlew clean assembleDebug

if [ $? -eq 0 ]; then
    echo "✓ BUILD SUCCESSFUL"
    echo "Debug APK location: app/build/outputs/apk/debug/"
    echo ""
    echo "You can install the APK using:"
    echo "adb install app/build/outputs/apk/debug/app-debug.apk"
else
    echo "✗ BUILD FAILED"
    exit 1
fi
