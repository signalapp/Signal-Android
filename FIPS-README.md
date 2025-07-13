
# FIPS Signal Android Setup

This repository contains the FIPS-compliant Signal Android implementation based on the upstream Signal-Android project.

## Prerequisites

1. Run the setup script to install dependencies:
   ```bash
   ./setup-fips-env.sh
   ```

2. Download and install Android Studio with NDK 25.2.9519653

3. Download OpenSSL source code (e.g., openssl-3.5.0.tar.gz) and extract to `~/openssl-build/`

## Build Process

### 1. Build OpenSSL FIPS Provider for Host (One-time)
```bash
cd ~/openssl-build/openssl-fips-3.1.2
./config enable-fips no-shared --prefix=/opt/openssl-out/fips-3.1.2
make -j$(nproc)
sudo make install_sw
sudo make install_fips
openssl fipsinstall -out /opt/openssl-out/fips-3.1.2/fipsmodule.cnf -module /opt/openssl-out/fips-3.1.2/lib64/ossl-modules/fips.so
```

### 2. Cross-compile OpenSSL for Android ABIs
```bash
source ./setup-fips-env.sh

# Build for each ABI
build_openssl_for_abi arm64-v8a android-arm64
build_openssl_for_abi armeabi-v7a android-arm
build_openssl_for_abi x86_64 android-x86_64
build_openssl_for_abi x86 android-x86
```

### 3. Copy Libraries and Build
```bash
copy_openssl_libs
./build-fips-signal.sh
```

## Sync with Upstream

To update from upstream Signal:
```bash
cd ~/GitHub/signal-upstream
git fetch --tags
git checkout v<NEW_TAG>

cd ../fips-signal-android
sync_upstream ~/GitHub/signal-upstream
./gradlew assembleDebug
```

## Directory Structure

- `fips-crypto-bridge/` - FIPS cryptography bridge module
- `fips-crypto-bridge/libs/openssl/` - Compiled OpenSSL libraries for each ABI
- `setup-fips-env.sh` - Environment setup script
- `build-fips-signal.sh` - Build script

## Security Notes

- Keep system updated: `sudo apt update && sudo apt upgrade`
- Verify SHA-256 checksums for all FIPS module binaries
- Use only validated FIPS modules in production
