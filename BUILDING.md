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
* SDK Platform (all API levels)
* Android Support Repository
* Google Repository

In Android studio, this can be done from the Quickstart panel. Just choose "Configure", then "SDK Manager". In the SDK Tools tab of the SDK Manager, make sure that "Android Support Repository" is installed, and that the latest "Android SDK build-tools" are installed. Click "OK" to return to the Quickstart panel. You may also need to install API version 28 in the SDK platforms tab.

Setting up a development environment and building from Android Studio
------------------------------------

[Android Studio](https://developer.android.com/sdk/installing/studio.html) is the recommended development environment.

1. Open Android Studio. On a new installation, the Quickstart panel will appear. If you have open projects, close them using "File > Close Project" to see the Quickstart panel.
2. From the Quickstart panel, choose "Checkout from Version Control" then "git".
3. Paste the URL for the session-android project when prompted (https://github.com/oxen-io/session-android.git).
4. Android Studio should detect the presence of a project file and ask you whether to open it. Click "yes".
5. Default config options should be good enough.
6. Project initialization and building should proceed.

Contributing code
-----------------

Code contributions should be sent via Github as pull requests, from feature branches [as explained here](https://help.github.com/articles/using-pull-requests).
