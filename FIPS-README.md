> **DEPRECATED** — see proposals/fips-discovery-2026-04-17.md


# FIPS Signal Android Setup

This repository contains the FIPS-compliant Signal Android implementation based on the upstream Signal-Android project.

## Prerequisites

1. **Java Development Kit (JDK)**: Version 11 or higher
   ```bash
   # On Ubuntu
   sudo apt-get install openjdk-11-jdk
   ```

2. **Android Studio**: Download and install the latest version
   - Install Android SDK Platform for API Level 34
   - Install Android NDK version 25.1.8937393

3. **Environment Variables**: Add to your shell profile
   ```bash
   export ANDROID_HOME=$HOME/Android/Sdk
   export ANDROID_NDK_HOME=$HOME/Android/Sdk/ndk/25.1.8937393
   export PATH=$PATH:$ANDROID_HOME/tools:$ANDROID_HOME/platform-tools
   ```

4. **Rust Toolchain**: Install Rust and Android targets
   ```bash
   curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
   source "$HOME/.cargo/env"
   rustup target add aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android
   ```

5. **CMake**: Version 3.22.1 or higher

6. **Setup Script**: Run the environment setup
   ```bash
   ./setup-fips-env.sh
   ```

## Build Process

### Option 1: Automated Build (Recommended)
```bash
# This will build OpenSSL, copy libraries, and build the APK
./build-fips-signal.sh
```

### Option 2: Manual Step-by-Step Build

#### 1. Build OpenSSL for All Android ABIs
```bash
source ./setup-fips-env.sh
build_openssl_all
```

#### 2. Copy Libraries to Project
```bash
copy_openssl_libs
```

#### 3. Build the APK
```bash
./gradlew clean assembleDebug
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
