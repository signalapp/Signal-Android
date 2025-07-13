
#!/bin/bash

# FIPS Signal Android Environment Setup Script
# Based on the Linux Build Guide

echo "Setting up FIPS Signal Android environment..."

# Environment variables for Android development
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/25.2.9519653"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

# Create directory structure
mkdir -p ~/GitHub
mkdir -p ~/openssl-build
mkdir -p fips-crypto-bridge/libs/openssl/{arm64-v8a,armeabi-v7a,x86_64,x86}

echo "Environment variables set:"
echo "ANDROID_HOME: $ANDROID_HOME"
echo "ANDROID_NDK_HOME: $ANDROID_NDK_HOME"

echo "Directory structure created for FIPS build"
echo "To make environment variables permanent, add them to ~/.bashrc"

# Function to build OpenSSL for specific Android ABI
build_openssl_for_abi() {
    local abi=$1
    local android_arch=$2
    local output_dir="/opt/openssl-out/$abi"
    
    echo "Building OpenSSL for $abi..."
    
    # Set NDK toolchain path
    export PATH=$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin:$PATH
    
    # Navigate to OpenSSL source (assumes ~/openssl-build/openssl-3.5.0)
    cd ~/openssl-build/openssl-*
    
    # Clean previous build
    make clean 2>/dev/null || true
    
    # Configure for Android
    ./Configure $android_arch -D__ANDROID_API__=23 enable-fips --prefix=$output_dir
    
    # Build and install
    make -j$(nproc)
    sudo make install_sw
    
    echo "OpenSSL built for $abi at $output_dir"
}

# Function to copy OpenSSL libraries to project
copy_openssl_libs() {
    echo "Copying OpenSSL libraries to project..."
    
    # Copy for each ABI
    cp /opt/openssl-out/arm64-v8a/lib64/libcrypto.so fips-crypto-bridge/libs/openssl/arm64-v8a/ 2>/dev/null || echo "arm64-v8a lib not found"
    cp /opt/openssl-out/armeabi-v7a/lib/libcrypto.so fips-crypto-bridge/libs/openssl/armeabi-v7a/ 2>/dev/null || echo "armeabi-v7a lib not found"
    cp /opt/openssl-out/x86_64/lib64/libcrypto.so fips-crypto-bridge/libs/openssl/x86_64/ 2>/dev/null || echo "x86_64 lib not found"
    cp /opt/openssl-out/x86/lib/libcrypto.so fips-crypto-bridge/libs/openssl/x86/ 2>/dev/null || echo "x86 lib not found"
    
    echo "OpenSSL libraries copied to project"
}

# Function to sync from upstream Signal
sync_upstream() {
    local upstream_dir="$1"
    echo "Syncing from upstream Signal repository..."
    
    if [ -d "$upstream_dir" ]; then
        rsync -a --delete $upstream_dir/app/src/ app/src/
        rsync -a --delete $upstream_dir/app/res/ app/res/
        echo "Upstream sync completed"
    else
        echo "Upstream directory not found: $upstream_dir"
    fi
}

# Export functions for use in terminal
export -f build_openssl_for_abi
export -f copy_openssl_libs
export -f sync_upstream

echo "Setup complete! Functions available:"
echo "  build_openssl_for_abi <abi> <android_arch>"
echo "  copy_openssl_libs"
echo "  sync_upstream <upstream_directory>"
