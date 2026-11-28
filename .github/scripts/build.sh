#!/bin/bash
#
# Runs inside the reproducible-builds Docker image (see .github/workflows/build-artifacts.yml),
# with the source to build mounted at the working directory. Runs Gradle and
# collects the unsigned outputs into $OUT.
#
# Expected environment variables:
#   $BUILD_KIND:  release | instrumentation | benchmark | reproducible
#   $TYPE:        (release only) play | website | nightly | nightlyStaging | github
#   $INDEX:       (reproducible only) which of the parallel builds this is
#   $NIGHTLY_URL: (nightly / nightlyStaging only) the update-check URL to bake into the build
#   $OUT:         directory to collect outputs into

set -euo pipefail
shopt -s inherit_errexit  # errexit also applies inside $(...)

function require_env() {
  local name
  for name in "$@"; do
    if [ -z "${!name:-}" ]; then
      echo "The $name environment variable was not set!" >&2
      exit 1
    fi
  done
}

require_env BUILD_KIND OUT
mkdir -p "$OUT"

echo "::group::Environment"
echo "commit: $(git rev-parse HEAD)"
echo "kind:   $BUILD_KIND"
nproc
free -g | head -2
java -version 2>&1 | head -1
echo "::endgroup::"

GRADLE="./gradlew --console=plain"

case "$BUILD_KIND" in
  release)
    require_env TYPE
    APK_DIR=""
    BUNDLE_FILE=""
    MAPPING_FILE=""

    case "$TYPE" in
      play)
        BUILD_TYPE="bundlePlayProdRelease"
        BUNDLE_FILE=app/build/outputs/bundle/playProdRelease/Signal-Android-play-prod-release.aab
        MAPPING_FILE=app/build/outputs/mapping/playProdRelease/mapping.txt
        ;;
      website)
        BUILD_TYPE="assembleWebsiteProdRelease"
        APK_DIR=app/build/outputs/apk/websiteProd/release/
        MAPPING_FILE=app/build/outputs/mapping/websiteProdRelease/mapping.txt
        ;;
      github)
        BUILD_TYPE="assembleGithubProdRelease"
        APK_DIR=app/build/outputs/apk/githubProd/release/
        MAPPING_FILE=app/build/outputs/mapping/githubProdRelease/mapping.txt
        ;;
      nightly)
        BUILD_TYPE="bundleNightlyProdRelease"
        BUNDLE_FILE=app/build/outputs/bundle/nightlyProdRelease/Signal-Android-nightly-prod-release.aab
        MAPPING_FILE=app/build/outputs/mapping/nightlyProdRelease/mapping.txt
        ;;
      nightlyStaging)
        BUILD_TYPE="assembleNightlyStagingRelease"
        APK_DIR=app/build/outputs/apk/nightlyStaging/release/
        MAPPING_FILE=app/build/outputs/mapping/nightlyStagingRelease/mapping.txt
        ;;
      *)
        echo "Unrecognized type: $TYPE" >&2
        exit 1
        ;;
    esac

    if [ "$TYPE" = "nightly" ] || [ "$TYPE" = "nightlyStaging" ]; then
      require_env NIGHTLY_URL
      echo "$NIGHTLY_URL" > nightly-url.txt
    fi

    echo "::group::Building app ($BUILD_TYPE)"
    $GRADLE "$BUILD_TYPE"
    echo "::endgroup::"

    # Outputs: out/*.aab (bundle types) or out/*.apk (everything else), plus out/mapping.txt.
    if [ -n "$BUNDLE_FILE" ]; then
      cp "$BUNDLE_FILE" "$OUT/"
    else
      cp "$APK_DIR"/*.apk "$OUT/"
    fi
    if [ -f "$MAPPING_FILE" ]; then
      cp "$MAPPING_FILE" "$OUT/mapping.txt"
    else
      echo "Warning: No mapping file found at $MAPPING_FILE, skipping."
    fi
    ;;

  instrumentation)
    echo "::group::App + test APKs"
    # Both tasks must be in a single Gradle invocation so the build script's
    # isInstrumentationTestRun detection (which keys off the task graph) is true
    # for both APK builds. That gives both APKs the matching .test_run applicationId.
    $GRADLE assemblePlayProdDebug assemblePlayProdDebugAndroidTest
    echo "::endgroup::"

    cp app/build/outputs/apk/playProd/debug/Signal-Android-play-prod-universal-debug-*.apk "$OUT/app.apk"
    cp app/build/outputs/apk/androidTest/playProd/debug/Signal-Android-play-prod-debug-androidTest.apk "$OUT/test.apk"
    ;;

  benchmark)
    echo "::group::Benchmark app APK"
    $GRADLE :Signal-Android:assemblePlayProdBenchmark
    echo "::endgroup::"

    echo "::group::Benchmark (test) APK"
    $GRADLE :benchmark:assembleBenchmark
    echo "::endgroup::"

    cp app/build/outputs/apk/playProd/benchmark/Signal-Android-play-prod-universal-benchmark-*.apk "$OUT/app.apk"
    cp benchmark/build/outputs/apk/benchmark/benchmark-benchmark.apk "$OUT/benchmark.apk"
    ;;

  reproducible)
    # One of N parallel jobs that each build the same commit on a different machine.
    INDEX=${INDEX:-0}
    echo "::group::Building website APK (#$INDEX)"
    $GRADLE clean assembleWebsiteProdRelease --no-configuration-cache
    echo "::endgroup::"

    cp app/build/outputs/apk/websiteProd/release/Signal-Android-website-prod-universal-*.apk "$OUT/website-$INDEX.apk"
    ;;

  *)
    echo "Unrecognized build kind: $BUILD_KIND" >&2
    exit 1
    ;;
esac

echo "::group::Outputs"
ls -la "$OUT"
sha256sum "$OUT"/*
echo "::endgroup::"
echo "Done!"
