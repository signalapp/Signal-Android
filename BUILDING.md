Building TextSecure
=====================

Basics
------

TextSecure uses [Gradle](http://gradle.org) to build the project and to maintain
dependencies.

Building TextSecure
-------------------

The following steps should help you (re)build TextSecure from the command line.

1. Checkout the source somewhere on your filesystem with 

        git clone https://github.com/WhisperSystems/TextSecure.git

2. Make sure you have the [Android SDK](https://developer.android.com/sdk/index.html) installed somewhere on your system.
3. Ensure the "Android Support Repository" and "Android SDK Build-tools" are installed from the Android SDK manager.
4. Create a local.properties file at the root of your source checkout and add an sdk.dir entry to it.

        sdk.dir=\<path to your sdk installation\>

5. Execute Gradle:

        ./gradlew build

Re-building native components
-----------------------------

Note: This step is optional; native components are contained as binaries (see [library/libs](library/libs)).

1. Ensure that the Android NDK is installed.

Execute ndk-build:

    cd library
    ndk-build

Afterwards, execute Gradle as above to re-create the APK.

Setting up a development environment
------------------------------------

[Android Studio](https://developer.android.com/sdk/installing/studio.html) is the recommended development environment.

1. Install Android Studio.
2. Make sure the "Android Support Repository" is installed in the Android Studio SDK.
3. Make sure the latest "Android SDK build-tools" is installed in the Android Studio SDK.
4. Create a new Android Studio project. from the Quickstart pannel (use File > Close Project to see it), choose "Checkout from Version Control" then "git".
5. Paste the URL for the TextSecure project when prompted (https://github.com/WhisperSystems/TextSecure.git).
6. Android studio should detect the presence of a project file and ask you whether to open it. Click "yes".
7. Default config options should be good enough.
8. Project initialisation and build should proceed.

Contributing code
-----------------

Code contributions should be sent via github as pull requests, from feature branches [as explained here](https://help.github.com/articles/using-pull-requests).

Mailing list
------------

Development discussion happens on the whispersystems mailing list.
[To join](https://lists.riseup.net/www/info/whispersystems)
Send emails to whispersystems@lists.riseup.net
