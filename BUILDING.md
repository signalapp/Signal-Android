Building Session
===============

Basics
------

Session uses [Gradle](http://gradle.org) to build the project and to maintain
dependencies.  However, you needn't install it yourself; the
"gradle wrapper" `gradlew`, mentioned below, will do that for you.

Dependencies
---------------
You will need Java 8 set up on your machine.

Ensure that the following packages are installed from the Android SDK manager:
    * Android SDK Build Tools (see buildToolsVersion in build.gradle)
    * SDK Platform (All API levels)
    * Android Support Repository
    * Google Repository
    
In Android studio, this can be done from the Quickstart panel, choose "Configure" then "SDK Manager". In the SDK Tools tab of the SDK Manager, make sure that the "Android Support Repository" is installed, and that the latest "Android SDK build-tools" are installed. Click "OK" to return to the Quickstart panel. You may also need to install API version 28 in the SDK platforms tab.
    
You will then need to clone and run `./gradlew build` on each of the following repositories IN ORDER.

https://github.com/loki-project/loki-messenger-android-curve-25519
https://github.com/loki-project/loki-messenger-android-protocol
https://github.com/loki-project/loki-messenger-android-meta
https://github.com/loki-project/session-android-service

This will build these depndencies into a common maven filepath which the main repo will then draw from. The easiest way to do this is to create new projects from git version control in Android Studio, allow Gradle to sort itself out, then run `./gradlew build`

Visual assets
----------------------

Source assets tend to be large binary blobs, which are best stored outside of git repositories. Some source files are SVGs that can be auto-colored and sized using a tool like [android-res-utils](https://github.com/sebkur/android-res-utils).

Sample command for generating our audio placeholder image:

```bash
pngs_from_svg.py ic_audio.svg /path/to/Session/res/ 150 --color #000 --opacity 0.54 --suffix _light
pngs_from_svg.py ic_audio.svg /path/to/Session/res/ 150 --color #fff --opacity 1.00 --suffix _light
```

Setting up a development environment and building from Android Studio
------------------------------------

[Android Studio](https://developer.android.com/sdk/installing/studio.html) is the recommended development environment.

1. Open Android Studio. On a new installation, the Quickstart panel will appear. If you have open projects, close them using "File > Close Project" to see the Quickstart panel.
2. From the Quickstart panel, choose "Checkout from Version Control" then "git".
3. Paste the URL for the session-android project when prompted (https://github.com/loki-project/session-android.git).
4. Android Studio should detect the presence of a project file and ask you whether to open it. Click "yes".
5. Default config options should be good enough.
6. Right now, we have kept the google-services.json file out of the public repository as it contains credentials for the firebase auth. We haven't yet made a way to build the app without this file or without firebase, as you don't technically need it to run the app. Email niels@loki.network to get this file for your build.
7. Project initialisation and build should proceed.

Building Session from Command Line
---------------

The following steps should help you (re)build Session from the command line one all dependencies have been aquired.

1. Checkout the session-android project source with the command:

        git clone https://github.com/loki-project/session-android.git

2. Make sure you have the [Android SDK](https://developer.android.com/sdk/index.html) installed.
3. Create a local.properties file at the root of your source checkout and add an sdk.dir entry to it.  For example:

        sdk.dir=/Application/android-sdk-macosx

4. Execute Gradle:

        ./gradlew build

Contributing code
-----------------

Code contributions should be sent via github as pull requests, from feature branches [as explained here](https://help.github.com/articles/using-pull-requests).
