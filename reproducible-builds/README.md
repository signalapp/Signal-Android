# Reproducible Builds

Signal has supported reproducible builds since Signal Android version 3.15.0, which was first released in March 2016. This process lets you verify that the version of the app that was downloaded from the Play Store matches the source code in our public repository.

This is achieved by replicating the build environment as a Docker image. You'll need to build the Docker image, run an instance of that container, compile Signal inside the container, and finally compare the compiled app bundle to the APKs that are installed on your device.

The command-line instructions in this guide are written for Linux, but you can adapt them to run on macOS and Windows with some small modifications.

In the following sections, we will use Signal version `7.7.0` as the reference example. Simply replace all occurrences of `7.7.0` with the version number you are about to verify.

## Overview of the process
Completing the reproducible build process verifies that the code in our public repository is exactly the same code that's running on your device.

Signal is built using [app bundles](https://developer.android.com/guide/app-bundle), which means that the Play Store builds a set of APKs that only include the resources that are needed for your specific device. This adds a couple of extra steps, but overall the process is still relatively straightforward:

1. Check out the source code for the version of Signal you're running.
1. Build the source to generate an app bundle.
1. Use [bundletool](https://github.com/google/bundletool/releases) to generate the set of APKs that the Play Store should have installed on your device.
1. Pull the Signal APKs that are installed on your device.
1. Use our `apkdiff.py` script to compare the two sets of APKs to make sure they match.

> Note: If you're using the APK from our website instead, please read all of the instructions to understand the process, and then read [this section](#verifying-the-website-apk) that includes additional information and specific instructions for the website build.

With that in mind, let's begin!


## Step-by-step instructions

### 0. Prerequisites
Before you begin, ensure you have the following installed:
- `git`
- `docker`
- `python` (version 3.x)
- `adb` ([link](https://developer.android.com/tools/adb))
- `bundletool` ([link](https://github.com/google/bundletool/releases))

You will also need to have Developer Options and USB Debugging enabled on your Android device. You can find instructions to do so [here](https://developer.android.com/studio/debug/dev-options). After the prerequisites are installed and the dev options are enabled, you can connect your Android device to your computer and run the `adb devices` command in your terminal. If everything has been set up correctly, your Android device will show up in the list.

### 1. Setting up directories
First, let's create a new directory for our reproducible builds. In your home directory (`~`), create a new directory called `reproducible-signal`:

```bash
mkdir ~/reproducible-signal
```

Next, we'll create two directories inside the `reproducible-signal` directory where we'll store all of the APKs that we're going to compare:

```bash
mkdir ~/reproducible-signal/apks-from-device
mkdir ~/reproducible-signal/apks-i-built
```

Now we're ready to build our own copy of Signal.

### 2. Building Signal

First, find the version of Signal that is running on your device by going to `Settings > Help` in the Signal app. You should see a 'Version' field like `7.7.0`.

Next, use `git` to clone that specific version. The tag consists of the version name prefixed with a `v` (e.g. `v7.7.0`):

```bash
git clone --depth 1 --branch v7.7.0 https://github.com/signalapp/Signal-Android.git
```

You can also download an archive of the source code for a specific tag [here](https://github.com/signalapp/Signal-Android/tags).

Now we can switch to the `reproducible-builds` directory in the Signal repo we just cloned and build the Docker image that we'll use to build Signal in a reproducible manner. Building the Docker image might take a while depending on your network connection.

> Note: Although the names may look similar at first, the `reproducible-builds` directory in the Signal git repository that is specified below is different than the `reproducible-signal` directory that we created in step 1 to store the APKs.

```bash
# Move into the right directory
cd Signal-Android/reproducible-builds

# Build the Docker image
docker build -t signal-android .
```

Now we are ready to start building the Signal Android app bundle. The following commands will invoke the `bundlePlayProdRelease` Gradle task in a container that uses the Docker image we just built. Again, this may take a while depending on your network connection and CPU.

```bash
# Move back to the root of the repository
cd ..

# Build the app
docker run --rm -v "$(pwd)":/project -w /project --user "$(id -u):$(id -g)" signal-android ./gradlew bundlePlayProdRelease
```

After that's done, you have your app bundle! It's located in `app/build/outputs/bundle/playProdRelease`. Let's copy it into the directory we set up in the first step:

```bash
cp app/build/outputs/bundle/playProdRelease/Signal-Android-play-prod-release.aab ~/reproducible-signal/apks-i-built/bundle.aab
```

Now let's switch from the git repo and use `bundletool` to generate the set of APKs that should be installed on your device.

While your Android device is connected to your computer, run the following command to generate the APKs for that device:

```bash
# Move to the directory where we copied the app bundle
cd ~/reproducible-signal/apks-i-built

# Generate a set of APKs in an output directory
bundletool build-apks --bundle=bundle.aab --output-format=DIRECTORY --output=apks --connected-device
```

Afterwards, your project directory should now look something like this:

```
reproducible-signal
|_apks-from-device
|_apks-i-built
  |_bundle.aab
  |_apks
    |_toc.pb
    |_splits
      |_base-master.apk
      |_base-arm64-v8a.apk
      |_base-xxhdpi.apk
```

> Note: The filenames in the example above that include `arm64-v8` and `xxhdpi` may be different depending on your device. This is because the APKs contain code that's specific to your device's CPU architecture and screen density, and the files are named accordingly.

At this point, we recommend cleaning things up a bit and deleting the stuff you no longer need. The remaining instructions will assume you have a directory structure and file layout that looks like this:

```
reproducible-signal
|_apks-from-device
|_apks-i-built
  |_base-master.apk
  |_base-arm64-v8a.apk
  |_base-xxhdpi.apk
```

Now that we have fresh APKs that were built from Signal's source code, let's retrieve the APKs that are installed on your device and compare them!

### 3. Pulling the APKs from your device

Compared to the previous steps, this one is pretty easy. With your Android device connected to your computer, run the following commands to pull all of Signal's APKs and store them in the `apks-from-device` directory:

```bash
# Move to the right directory
cd ~/reproducible-signal

# Pull the APKs from the device
adb shell pm path org.thoughtcrime.securesms | sed 's/package://' | xargs -I{} adb pull {} apks-from-device/
```

If everything went well, your directory structure should now look something like this:

```
reproducible-signal
|_apks-from-device
  |_base.apk
  |_split_config.arm64-v8a.apk
  |_split_config.xxhdpi.apk
|_apks-i-built
  |_base-master.apk
  |_base-arm64-v8a.apk
  |_base-xxhdpi.apk
```

(Once again, please note that the `arm64-v8a` and `xxhdpi` filenames may be different based on the device you are using.)

You'll notice that the names of the APKs in each directory are very similar, but they aren't exactly the same. This is because `bundletool` and Android have slightly different naming conventions. However, it should still be clear which APKs will pair up with each other for comparison purposes.

### 4. Checking if the APKs match

Finally, it's time for the moment of truth! Let's compare the APKs that were pulled from your device with the APKs that were compiled from the Signal source code. The [`apkdiff.py`](./apkdiff/apkdiff.py) utility that is provided in the Signal repo makes this step easy.

The code for the `apkdiff.py` script is short and easy to examine, and it simply extracts the zipped APKs and automates the comparison process. Using this script to check the APKs is helpful because APKs are compressed archives that can't easily be compared with a tool like `diff`. The script also knows how to skip files that are unrelated to any of the app's code or functionality (like signing information).

Let's copy the script to our working directory and ensure that it's executable:

```bash
cp ~/Signal-Android/reproducible-builds/apkdiff/apkdiff.py ~/reproducible-signal

chmod +x ~/reproducible-signal/apkdiff.py
```

The script expects two APK filenames as arguments. In order to verify all of the APKs, simply run the script for each pair of APKs as follows. Be sure to update the filenames for your specific device (e.g. replacing `arm64-v8a` or `xxhdpi` if necessary):

```bash
./apkdiff.py apks-i-built/base-master.apk    apks-from-device/base.apk
./apkdiff.py apks-i-built/base-arm64-v8a.apk apks-from-device/split_config.arm64-v8a.apk
./apkdiff.py apks-i-built/base-xxhdpi.apk    apks-from-device/split_config.xxhdpi.apk
```

If each step says `APKs match!`, you're good to go! You've successfully verified that your device is running exactly the same code that is in the Signal Android git repository.

If you get `APKs don't match!`, it means something went wrong. Please see the [Troubleshooting section](#troubleshooting) for more information.

## Verifying the website APK

For people without access to the Play Store, we provide a version of our app via [our website](https://signal.org/android/apk/). Unlike our Play Store build, the website APK is a larger "universal" APK so that it's easy to install on a wide variety of devices.

This actually ends up making things a bit easier because you will only have one pair of APKs to compare. The only other difference is the Gradle command to build the release has a different argument (`assembleWebsiteProdRelease` instead of `bundlePlayProdRelease`):

```bash
# Make website build (output to app/build/outputs/apk/websiteProdRelease)
docker run --rm -v "$(pwd)":/project -w /project --user "$(id -u):$(id -g)" signal-android ./gradlew assembleWebsiteProdRelease
```

Otherwise, all of the steps above will still apply, and you will only need to compare the APK you downloaded from the website (or pulled from your device) with the one you built above.

## Troubleshooting

If you're able to successfully build and retrieve all of the APKs yet some of them are failing to verify, please check the following:

- Are you sure you're building the exact same version? Make sure you pulled the git tag that corresponds exactly with the version of Signal you have installed on your device.
- Are you comparing the right APKs? Multiple APKs are present with app bundles, so make sure you're comparing base-to-base, density-to-density, and ABI-to-ABI. The wrong filename in the wrong place will cause the `apkdiff.py` script to report a mismatch.
- Are you using the latest version of the Docker image? The Dockerfile can change on a version-by-version basis, and you should be re-building the image each time to make sure it hasn't changed.

We have a daily automated task that tests the reproducible build process, but bugs are still possible.

If you're having trouble even after building and pulling all the APKs correctly and trying the troubleshooting steps above, please [open an issue](https://github.com/signalapp/Signal-Android/issues/new/choose).

If you're having difficulty getting things to build at all, the [community forum](https://community.signalusers.org/c/development) is a great place to ask for advice or get help.

## Extra credit: Code Transparency verification

> **Important**: At the time of writing, the public version of `bundletool` does not support v3.1 signature schemes and will output an error when running the instructions below.
> We have opened a [pull request](https://github.com/google/bundletool/pull/368) to fix this, but until it is merged, you will need to build the tool from source.
> Feel free to comment on the accompanying [issue](https://github.com/google/bundletool/issues/369) to expedite the process ;)

As part of the release of app bundles, Google also added a new [Code Transparency](https://developer.android.com/guide/app-bundle/code-transparency) mechanism. This is a process by which we can sign certain parts of the APK with a private key, allowing users to verify that the APK from the Play Store has not been modified after it was submitted by Signal.

This is labeled as "extra credit" because it is, by definition, a weaker check than the above reproducible build verification process. For one, the Code Transparency signature does not cover the contents of the entire APK — media assets and other auxiliary files are excluded. Also, it only verifies that the code Signal submitted matches the code in the APK — it does _not_ verify that the code that was submitted matches the public git repository. In contrast, the reproducible build steps above cover all of these scenarios.

That said, the code transparency check does verify many important things, and the steps are much easier! So without further ado, let's go.

While your device is connected to your computer, run the following command to check the "code transparency" status for Signal Android:

```bash
bundletool check-transparency \
  --mode=connected_device \
  --package-name="org.thoughtcrime.securesms"
```

(For other ways of verifying the code transparency fingerprint, you can check out the [official guide](https://developer.android.com/guide/app-bundle/code-transparency#verify_apk) on the Android Developers site.)

The command above will output the code transparency results, including a certificate fingerprint. You can verify that this matches the code transparency fingerprint:

```
57 24 B1 15 23 8C 7B 03 E2 6A D9 01 34 FC 77 C5 7B 69 E7 ED DE 3B 70 C2 A7 8E C7 A5 58 3E FC 8E
```

Thanks for using (and reproducibly building) Signal!
