Building Signal
===============

Basics
------

Signal uses [Gradle](http://gradle.org) to build the project and to maintain
dependencies.  However, you needn't install it yourself; the
"gradle wrapper" `gradlew`, mentioned below, will do that for you.

Building Signal
---------------

The following steps should help you (re)build Signal from the command line.

1. Checkout the Signal-Android project source with the command:

        git clone https://github.com/WhisperSystems/Signal-Android.git

2. Make sure you have the [Android SDK](https://developer.android.com/sdk/index.html) installed.
3. Ensure that the following packages are installed from the Android SDK manager:
    * Android SDK Build Tools (22.0.1)
    * SDK Platform (API level 22)
    * Android Support Repository
    * Google Repository
4. Create a local.properties file at the root of your source checkout and add an sdk.dir entry to it.  For example:

        sdk.dir=/Application/android-sdk-macosx

5. Make sure you have Java 1.7 JDK installed. JAVA_HOME environment variable must be pointing to the JDK. For example:
        
        export JAVA_HOME=/usr/lib/jvm/java-7-openjdk-amd64

6. Execute Gradle:

        ./gradlew build

Visual assets
----------------------

Source assets tend to be large binary blobs, which are best stored outside of git repositories. We host ours in a [Pixelapse repository](https://www.pixelapse.com/openwhispersystems/projects/signal-android/). Some source files are SVGs that can be auto-colored and sized using a tool like [android-res-utils](https://github.com/sebkur/android-res-utils).

Sample command for generating our audio placeholder image:

```bash
pngs_from_svg.py ic_audio.svg /path/to/Signal/res/ 150 --color #000 --opacity 0.54 --suffix _light
pngs_from_svg.py ic_audio.svg /path/to/Signal/res/ 150 --color #fff --opacity 1.00 --suffix _light
```

Setting up a development environment
------------------------------------

[Android Studio](https://developer.android.com/sdk/installing/studio.html) is the recommended development environment.

1. Install Android Studio.
2. Open Android Studio. On a new installation, the Quickstart panel will appear. If you have open projects, close them using "File > Close Project" to see the Quickstart panel.
3. From the Quickstart panel, choose "Configure" then "SDK Manager".
4. In the SDK Tools tab of the SDK Manager, make sure that the "Android Support Repository" is installed, and that the latest "Android SDK build-tools" are installed. Click "OK" to return to the Quickstart panel.
5. From the Quickstart panel, choose "Checkout from Version Control" then "git".
6. Paste the URL for the Signal-Android project when prompted (https://github.com/WhisperSystems/Signal-Android.git).
7. Android studio should detect the presence of a project file and ask you whether to open it. Click "yes".
9. Default config options should be good enough.
9. Project initialisation and build should proceed.

Contributing code
-----------------

Code contributions should be sent via github as pull requests, from feature branches [as explained here](https://help.github.com/articles/using-pull-requests).

Mailing list
------------

Development discussion happens on the whispersystems mailing list.
[To join](https://lists.riseup.net/www/info/whispersystems)
Send emails to whispersystems@lists.riseup.net

