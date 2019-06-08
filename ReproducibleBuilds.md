# Reproducible Builds


## TL;DR

You can just use these [instructions](https://signal.org/blog/reproducible-android/) from the official announcement at Open Whisper Systems's blog:
```
# Clone the Signal Android source repository
$ git clone https://github.com/signalapp/Signal-Android.git && cd Signal-Android

# Check out the release tag for the version you'd like to compare
$ git checkout v[the version number]

# Build using the Docker environment
$ docker run --rm -v $(pwd):/project -w /project whispersystems/signal-android:1.3 ./gradlew clean assembleRelease

# Verify the APKs
$ python3 apkdiff/apkdiff.py build/outputs/apks/project-release-unsigned.apk path/to/SignalFromPlay.apk
```

Note that the instructions above use a pre-built Signal Docker image from [Docker Hub](https://hub.docker.com/u/whispersystems/). If you wish to compile the image yourself, continue reading the longer version below.


***


## Introduction

Since version 3.15.0 Signal for Android has supported reproducible builds. This is achieved by replicating the build environment as a Docker image. You'll need to build the image, run a container instance of it, compile Signal inside the container and finally compare the resulted APK to the APK that is distributed in the Google Play Store.

The command line parts in this guide are written for Linux but with some little modifications you can adapt them to macOS (OS X) and Windows. In the following sections we will use `3.15.2` as an example Signal version. You'll just need to replace all occurrences of `3.15.2` with the version number you are about to verify.


## Setting up directories

First let's create a new directory for this whole reproducible builds project. In your home folder (`~`), create a new directory called `reproducible-signal`.
```
user@host:$ mkdir ~/reproducible-signal
```

Next create another directory inside `reproducible-signal` called `apk-from-google-play-store`.
```
user@host:$ mkdir ~/reproducible-signal/apk-from-google-play-store
```

We will use this directory to share APKs between the host OS and the Docker container.

Finally create one more directory inside `reproducible-signal` called `image-build-context`.
```
user@host:$ mkdir ~/reproducible-signal/image-build-context
```

This directory will be used later to build our Docker image.


## Getting the Google Play Store version of Signal APK

To compare the APKs we of course need a version of Signal from the Google Play Store.

First make sure that the Signal version you want to verify is installed on your Android device. You'll need `adb` for this part.

Plug your device to your computer and run this command to pull the APK from the device:

```
user@host:$ adb pull $(adb shell pm path org.thoughtcrime.securesms | grep /base.apk | awk -F':' '{print $2}') ~/reproducible-signal/apk-from-google-play-store/Signal-$(adb shell dumpsys package org.thoughtcrime.securesms | grep versionName | awk -F'=' '{print $2}').apk
```

This will pull a file into `~/reproducible-signal/apk-from-google-play-store/` with the name `Signal-<version>.apk`

Alternatively, you can do this step-by-step:

```
user@host:$ adb shell pm path org.thoughtcrime.securesms
```
This will output something like:
```
package:/data/app/org.thoughtcrime.securesms-aWRzcGlzcG9wZA==/base.apk
```

The output will tell you where the Signal APK is located in your device. (In this example the path is `/data/app/org.thoughtcrime.securesms-aWRzcGlzcG9wZA==/base.apk`)

Now using this information, pull the APK from your device to the `reproducible-signal/apk-from-google-play-store` directory you created before:
```
user@host:$ adb pull \
              /data/app/org.thoughtcrime.securesms-aWRzcGlzcG9wZA==/base.apk \
              ~/reproducible-signal/apk-from-google-play-store/Signal-3.15.2.apk
```

We will use this APK in the final part when we compare it with the self-built APK from GitHub.

## Identifying the ABI

Since v4.37.0, the APKs have been split by ABI, the CPU architecture of the target device. Google play will serve the correct one to you for your device.

To identify which ABIs the google play APK supports, we can look inside the APK, which is just a zip file:

```
user@host:$ unzip -l ~/reproducible-signal/apk-from-google-play-store/Signal-*.apk | grep lib/
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

#### Grabbing the `Dockerfile`

First you will need the `Dockerfile` for Signal Android. It comes bundled with Signal's source code. The `Dockerfile` contains instructions on how to automatically build a Docker image for Signal. You just need to run it and it builds itself.

Download the `Dockerfile` to the `image-build-context` directory.

```
user@host:$ wget -O ~/reproducible-signal/image-build-context/Dockerfile_v3.15.2 \
                    https://raw.githubusercontent.com/signalapp/Signal-Android/v3.15.2/Dockerfile
```

Note that the `Dockerfile` is specific to the Signal version you want to compare to. Again you have to adjust the URL above to match the right version. (Though sometimes the file might not be up to date, see the [Troubleshooting section](#troubleshooting))


#### Building the image

Now we have everything we need to build the Docker image for Signal. Go to the `image-build-context` directory:

```
user@host:$ cd ~/reproducible-signal/image-build-context
```

And list the contents.
```
user@host:$ ls
```
The output should look like this:
```
Dockerfile_v3.15.2
```

Now in this directory build the image using `Dockerfile_v3.15.2`:
```
user@host:$ docker build --file Dockerfile_v3.15.2 --tag signal-android .
```

(Note that there is a dot at the end of that command!)

Wait a few years for the build to finish... :construction_worker:

(Depending on your computer and network connection, this may take several minutes.)

:calendar: :sleeping:

After the build has finished, you may wish to list all your Docker images to see that it's really there:
```
user@host:$ docker images
```
Output should look something like this:
```
REPOSITORY          TAG                 IMAGE ID            CREATED             VIRTUAL SIZE
signal-android      latest              c6b84450b896        46 seconds ago      2.94 GB
ubuntu              14.04.3             8693db7e8a00        9 weeks ago         187.9 MB
```


## Compiling Signal inside a container

Next we will run a container of the image we just built, grab Signal's source code and compile Signal.

First go to the `reproducible-signal` directory:

```
user@host:$ cd ~/reproducible-signal/
```

To run a new ephemeral container with an interactive terminal session execute the following long command:
```
user@host:$ docker run \
              --name signal \
              --rm \
              --interactive \
              --tty \
              --volume $(pwd)/apk-from-google-play-store:/signal-build/apk-from-google-play-store \
              --workdir /signal-build \
              signal-android
```
Now you are inside the container.

Grab Signal's source code from GitHub and go to the repository directory:
```
root@container:# git clone https://github.com/signalapp/Signal-Android.git
root@container:# cd Signal-Android
```

Before you can compile, you **must** ensure that you are at the right commit. In other words you **must** checkout the version you wish to verify (here we are verifying 3.15.2):
```
root@container:# git checkout --quiet v3.15.2
```

Now you may compile the release APK by running:
```
root@container:# ./gradlew clean assemblePlayRelease --exclude-task signProductionPlayRelease
```
This will take a few minutes :sleeping:


#### Checking if the APKs match

After the build has completed successfully we can finally compare if the APKs match. For the comparison we need of course the Google Play Store version of Signal APK which you copied to the `apk-from-google-play-store` directory in the beginning of this guide. Because we used that directory as a `--volume` parameter for our container, we can see all the files in that directory within our container.

So now we can compare the APKs using the `apkdiff.py` tool.

The above build step produced several APKs, one for each supported ABI and one universal one. You will need to determine the correct APK to compare.

Currently, the most common ABI is `armeabi-v7a`. Other options at this time include `x86` and `universal`. In the future it will also include 64-bit options, such as `x86_64` and `arm64-v8a`.

See [Identifying the ABI](#identifying-the-abi) above if you don't know the ABI of your play store APK.

Once you have determined the ABI, add an `abi` environment variable. For example, suppose we determine that `armeabi-v7a` is the ABI google play has served:

```
root@container:# export abi=armeabi-v7a
```

And the diff script to compare:
```
root@container:# python3 apkdiff/apkdiff.py \
                   build/outputs/apk/play/release/*play-$abi-release-unsigned*.apk \
                   ../apk-from-google-play-store/Signal-3.15.2.apk
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
- some pinned packages in the `Dockerfile` are not available anymore and building of the Docker image fails
- the Android packages in the Docker image are outdated and compiling Signal fails
- you built the Docker image with a wrong version of the `Dockerfile`
- you didn't checkout the correct Signal version tag with Git before compiling
- the ABI you selected is not the correct ABI, particularly if you see an error along the lines of `Sorted manifests don't match, lib/x86/libcurve25519.so vs lib/armeabi-v7a/libcurve25519.so`.
- this guide is outdated
- you are in a dream
- if you run into this issue: https://issuetracker.google.com/issues/110237303 try to add `resources.arsc` to the list of ignored files and compare again
