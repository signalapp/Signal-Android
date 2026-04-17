
#!/bin/bash
# DEPRECATED — see proposals/fips-discovery-2026-04-17.md
set -e

OPENSSL_VERSION="3.1.2"
TOOLCHAIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64"
OUTPUT_DIR="$(pwd)/openssl_build"

# Architectures to build for
ARCHS=( "android-arm64" "android-arm" "android-x86_64" "android-x86" )
ABIS=( "arm64-v8a" "armeabi-v7a" "x86_64" "x86" )

# Download and extract OpenSSL
if [ ! -f "openssl-${OPENSSL_VERSION}.tar.gz" ]; then
    wget "https://www.openssl.org/source/openssl-${OPENSSL_VERSION}.tar.gz"
fi
tar -xzf "openssl-${OPENSSL_VERSION}.tar.gz"

cd "openssl-${OPENSSL_VERSION}"

for i in "${!ARCHS[@]}"; do
    ARCH=${ARCHS[$i]}
    ABI=${ABIS[$i]}
    API=23 # Match minSdk

    echo "================================================="
    echo "Building OpenSSL for ${ABI}"
    echo "================================================="

    export PATH="$TOOLCHAIN/bin:$PATH"
    export ANDROID_API=${API}

    # Configure OpenSSL with FIPS provider support
    ./Configure ${ARCH} -D__ANDROID_API__=${API} enable-fips --prefix="${OUTPUT_DIR}/${ABI}"

    # Compile
    make -j$(nproc)

    # Install to our output directory
    make install_sw

    # Clean up for the next architecture
    make clean
done

echo "OpenSSL build complete. Libraries are in ${OUTPUT_DIR}"
