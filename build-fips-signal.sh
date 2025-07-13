
#!/bin/bash

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

# Build the debug APK
echo "Building debug APK..."
./gradlew clean assembleDebug

if [ $? -eq 0 ]; then
    echo "✓ BUILD SUCCESSFUL"
    echo "Debug APK location: app/build/outputs/apk/debug/"
else
    echo "✗ BUILD FAILED"
    exit 1
fi
