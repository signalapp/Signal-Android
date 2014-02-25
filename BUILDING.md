Building TextSecure
===================

1. Ensure the 'Android Support Repository' is installed from the Android SDK manager.

Execute Gradle:

    ./gradlew build

Re-building native components
-----------------------------

Note: This step is optional; native components are contained as binaries (see [library/libs](library/libs)).

1. Ensure that the Android NDK is installed.

Execute ndk-build:

    cd library
    ndk-build

Afterwards, execute Gradle as above to re-create the APK.
