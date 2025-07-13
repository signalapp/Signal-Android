
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

# Function to build OpenSSL for all Android ABIs
build_openssl_all() {
    echo "Building OpenSSL for all Android architectures..."
    
    if [ ! -f "./build_openssl_android.sh" ]; then
        echo "Error: build_openssl_android.sh not found in current directory"
        return 1
    fi
    
    chmod +x ./build_openssl_android.sh
    ./build_openssl_android.sh
}

# Function to build OpenSSL for specific Android ABI
build_openssl_for_abi() {
    local abi=$1
    local android_arch=$2
    local output_dir="./openssl_build/$abi"
    
    echo "Building OpenSSL for $abi..."
    
    # Set NDK toolchain path
    export PATH=$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin:$PATH
    
    # Download OpenSSL if not present
    if [ ! -d "openssl-3.1.2" ]; then
        if [ ! -f "openssl-3.1.2.tar.gz" ]; then
            wget "https://www.openssl.org/source/openssl-3.1.2.tar.gz"
        fi
        tar -xzf "openssl-3.1.2.tar.gz"
    fi
    
    cd openssl-3.1.2
    
    # Clean previous build
    make clean 2>/dev/null || true
    
    # Configure for Android
    ./Configure $android_arch -D__ANDROID_API__=23 enable-fips --prefix="$(pwd)/../openssl_build/$abi"
    
    # Build and install
    make -j$(nproc)
    make install_sw
    
    cd ..
    echo "OpenSSL built for $abi at $output_dir"
}

# Function to copy OpenSSL libraries to project
copy_openssl_libs() {
    echo "Copying OpenSSL libraries to project..."
    
    # Create include directory and copy headers
    mkdir -p fips-crypto-bridge/libs/openssl/include
    if [ -d "openssl_build/arm64-v8a/include" ]; then
        cp -r openssl_build/arm64-v8a/include/* fips-crypto-bridge/libs/openssl/include/
    fi
    
    # Copy for each ABI
    for abi in "arm64-v8a" "armeabi-v7a" "x86_64" "x86"; do
        mkdir -p "fips-crypto-bridge/libs/openssl/$abi"
        
        # Determine lib directory (some use lib, some use lib64)
        libdir="openssl_build/$abi/lib"
        if [ -d "openssl_build/$abi/lib64" ]; then
            libdir="openssl_build/$abi/lib64"
        fi
        
        if [ -f "$libdir/libcrypto.so" ]; then
            cp "$libdir/libcrypto.so" "fips-crypto-bridge/libs/openssl/$abi/"
            echo "Copied libcrypto.so for $abi"
        else
            echo "Warning: libcrypto.so not found for $abi"
        fi
        
        if [ -f "$libdir/fips.so" ]; then
            cp "$libdir/fips.so" "fips-crypto-bridge/libs/openssl/$abi/"
            echo "Copied fips.so for $abi"
        else
            echo "Warning: fips.so not found for $abi"
        fi
    done
    
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
export -f build_openssl_all
export -f build_openssl_for_abi
export -f copy_openssl_libs
export -f sync_upstream

echo "Setup complete! Functions available:"
echo "  build_openssl_all - Build OpenSSL for all Android architectures"
echo "  build_openssl_for_abi <abi> <android_arch> - Build for specific ABI"
echo "  copy_openssl_libs - Copy built libraries to project"
echo "  sync_upstream <upstream_directory> - Sync from upstream Signal"
