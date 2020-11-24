# Reproducible Builds


## TL;DR

```bash
# Clone the Signal Android source repository
git clone https://github.com/signalapp/Signal-Android.git && cd Signal-Android

# Check out the release tag for the version you'd like to compare
git checkout v[the version number]

# Build the Docker image
cd reproducible-builds
docker build -t signal-android .

# Go back up to the root of the project
cd ..

# Build using the Docker environment
docker run --rm -v $(pwd):/project -w /project signal-android ./gradlew clean assemblePlayProdRelease

# Verify the APKs
python3 apkdiff/apkdiff.py build/outputs/apks/project-release-unsigned.apk path/to/SignalFromPlay.apk
```

***


## Introduction

Since version 3.15.0 Signal for Android has supported reproducible builds. The instructions were then updated for version 5.0.0. This is achieved by replicating the build environment as a Docker image. You'll need to build the image, run a container instance of it, compile Signal inside the container and finally compare the resulted APK to the APK that is distributed in the Google Play Store.

The command line parts in this guide are written for Linux but with some little modifications you can adapt them to macOS (OS X) and Windows. In the following sections we will use `3.15.2` as an example Signal version. You'll just need to replace all occurrences of `3.15.2` with the version number you are about to verify.


## Setting up directories

First let's create a new directory for this whole reproducible builds project. In your home folder (`~`), create a new directory called `reproducible-signal`.

```bash
mkdir ~/reproducible-signal
```

Next create another directory inside `reproducible-signal` called `apk-from-google-play-store`.

```bash
mkdir ~/reproducible-signal/apk-from-google-play-store
```

We will use this directory to share APKs between the host OS and the Docker container.


## Getting the Google Play Store version of Signal APK

To compare the APKs we of course need a version of Signal from the Google Play Store.

First make sure that the Signal version you want to verify is installed on your Android device. You'll need `adb` for this part.

Plug your device to your computer and run this command to pull the APK from the device:

```bash
adb pull $(adb shell pm path org.thoughtcrime.securesms | grep /base.apk | awk -F':' '{print $2}') ~/reproducible-signal/apk-from-google-play-store/Signal-$(adb shell dumpsys package org.thoughtcrime.securesms | grep versionName | awk -F'=' '{print $2}').apk
```

This will pull a file into `~/reproducible-signal/apk-from-google-play-store/` with the name `Signal-<version>.apk`

Alternatively, you can do this step-by-step:

```bash
adb shell pm path org.thoughtcrime.securesms
```

This will output something like:

```bash
package:/data/app/org.thoughtcrime.securesms-aWRzcGlzcG9wZA==/base.apk
```

The output will tell you where the Signal APK is located in your device. (In this example the path is `/data/app/org.thoughtcrime.securesms-aWRzcGlzcG9wZA==/base.apk`)

Now using this information, pull the APK from your device to the `reproducible-signal/apk-from-google-play-store` directory you created before:

```bash
adb pull \
  /data/app/org.thoughtcrime.securesms-aWRzcGlzcG9wZA==/base.apk \
  ~/reproducible-signal/apk-from-google-play-store/Signal-3.15.2.apk
```

We will use this APK in the final part when we compare it with the self-built APK from GitHub.

## Identifying the ABI

Since v4.37.0, the APKs have been split by ABI, the CPU architecture of the target device. Google Play will serve the correct one to you for your device.

To identify which ABIs the google play APK supports, we can look inside the APK, which is just a zip file:

```bash
unzip -l ~/reproducible-signal/apk-from-google-play-store/Signal-*.apk | grep lib/
```

Example:

```
  1214348  00-00-1980 00:00   lib/armeabi-v7a/libconscrypt_jni.so
   151980  00-00-1980 00:00   lib/armeabi-v7a/libcurve25519.so
  4164320  00-00-1980 00:00   lib/armeabi-v7a/libjingle_peerconnection_so.so
    13948  00-00-1980 00:00   lib/armeabi-v7a/libnative-utils.so
  2357812  00-00-1980 00:00   lib/armeabi-v7a/libsqlcipher.so
```

As there is just one sub directory of `lib/` called `armeabi-v7a`, that is your ABI. Make a note of that for later. If you see more than one subdirectory of `lib/`:

```
  1214348  00-00-1980 00:00   lib/armeabi-v7a/libconscrypt_jni.so
   151980  00-00-1980 00:00   lib/armeabi-v7a/libcurve25519.so
  4164320  00-00-1980 00:00   lib/armeabi-v7a/libjingle_peerconnection_so.so
    13948  00-00-1980 00:00   lib/armeabi-v7a/libnative-utils.so
  2357812  00-00-1980 00:00   lib/armeabi-v7a/libsqlcipher.so
  2111376  00-00-1980 00:00   lib/x86/libconscrypt_jni.so
   201056  00-00-1980 00:00   lib/x86/libcurve25519.so
  7303888  00-00-1980 00:00   lib/x86/libjingle_peerconnection_so.so
     5596  00-00-1980 00:00   lib/x86/libnative-utils.so
  3977636  00-00-1980 00:00   lib/x86/libsqlcipher.so
```

Then that means you have the `universal` APK.

## Installing Docker

Install Docker by following the instructions for your platform at https://docs.docker.com/engine/installation/

Your platform might also have its own preferred way of installing Docker. E.g. Ubuntu has its own Docker package (`docker.io`) if you do not want to follow Docker's instructions.

In the following sections we will assume that your Docker installation works without issues. So after installing, please make sure that everything is running smoothly before continuing.


## Building a Docker image for Signal
First, you need to pull down the source for Signal-Android, which contains everything you need to build the project, including the `Dockerfile`. The `Dockerfile` contains instructions on how to automatically build a Docker image for Signal. It's located in the `reproducible-builds` directory of the repository. To get it, clone the project:

```
git clone https://github.com/signalapp/Signal-Android.git signal-source
```

Then, checkout the specific version you're trying to build:

```
git checkout --quiet v5.0.0
```

Then, to build it, go into the `reproducible-builds` directory:

```
cd ~/reproducible-signal/signal-source/reproducible-builds
```

...and run the docker build command:

```
docker build -t signal-android .
```

(Note that there is a dot at the end of that command!)

Wait a few years for the build to finish... :construction_worker:

(Depending on your computer and network connection, this may take several minutes.)

:calendar: :sleeping:

After the build has finished, you may wish to list all your Docker images to see that it's really there:

```
docker images
```

Output should look something like this:

```
REPOSITORY          TAG                 IMAGE ID            CREATED             VIRTUAL SIZE
signal-android      latest              c6b84450b896        46 seconds ago      2.94 GB
```


## Compiling Signal inside a container

Next we compile Signal.

First go to the directory where the source code is: `reproducible-signal/signal-source`:

```
cd ~/reproducible-signal/signal-source
```

To build with the docker image you just built (`signal-android`), run:

```
docker run --rm -v $(pwd):/project -w /project signal-android ./gradlew clean assemblePlayProdRelease
```

This will take a few minutes :sleeping:


### Checking if the APKs match

So now we can compare the APKs using the `apkdiff.py` tool.

The above build step produced several APKs, one for each supported ABI and one universal one. You will need to determine the correct APK to compare.

Currently, the most common ABI is `armeabi-v7a`. Other options at this time include `x86` and `universal`. In the future it will also include 64-bit options, such as `x86_64` and `arm64-v8a`.

See [Identifying the ABI](#identifying-the-abi) above if you don't know the ABI of your play store APK.

Once you have determined the ABI, add an `abi` environment variable. For example, suppose we determine that `armeabi-v7a` is the ABI google play has served:

```bash
export abi=armeabi-v7a
```

And run the diff script to compare (updating the filenames for your specific version):

```bash
python3 reproducible-builds/apkdiff/apkdiff.py \
        app/build/outputs/apk/playProd/release/*play-prod-$abi-release-unsigned*.apk \
        ../apk-from-google-play-store/Signal-5.0.0.apk
```

Output:

```
APKs match!
```

If you get `APKs match!`, you have successfully verified that the Google Play release matches with your own self-built version of Signal. Congratulations! Your APKs are a match made in heaven! :sparkles:

If you get `APKs don't match!`, you did something wrong in the previous steps. See the [Troubleshooting section](#troubleshooting) for more info.


## Comparing next time

If the build environment (i.e. `Dockerfile`) has not changed, you don't need to build the image again to verify a newer APK. You can just [run the container again](#compiling-signal-inside-a-container).


## Troubleshooting

If you cannot get things to work, please do not open an issue or comment on an existing issue at GitHub. Instead, ask for help at https://community.signalusers.org/c/development

Some common issues why things may not work:
- the Android packages in the Docker image are outdated and compiling Signal fails
- you built the Docker image with a wrong version of the `Dockerfile`
- you didn't checkout the correct Signal version tag with Git before compiling
- the ABI you selected is not the correct ABI, particularly if you see an error along the lines of `Sorted manifests don't match, lib/x86/libcurve25519.so vs lib/armeabi-v7a/libcurve25519.so`.
- this guide is outdated
- you are in a dream
- if you run into this issue: https://issuetracker.google.com/issues/110237303 try to add `resources.arsc` to the list of ignored files and compare again
