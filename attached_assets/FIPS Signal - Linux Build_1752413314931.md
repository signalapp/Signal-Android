### **FIPS-Signal Android: Linux Setup & Build Guide**

This guide provides step-by-step instructions for setting up a Linux development environment (Ubuntu/Debian) to build a FIPS-compliant version of the Signal Android app. It covers everything from installing prerequisites to compiling native OpenSSL libraries and integrating them into the project.

### **A. One-Time System Setup**

These steps configure your computer with the necessary tools and libraries. They only need to be performed once.

**1\. Install Core Tools & Libraries**

First, open a terminal and run the following commands to install the required development tools and libraries from the system's package manager.

* **Update Package Lists & Install Dependencies**:  
  sudo apt update  
  sudo apt install \-y git openjdk-17-jdk build-essential curl wget rsync

* **Verify Java Installation**:  
  java \-version

**2\. Configure Tools & Environment**

Next, configure the specific tools for the build environment.

* **Install Rust Toolchain**:  
  curl \--proto '=https' \--tlsv1.2 \-sSf https://sh.rustup.rs | sh  
  \# Follow the on-screen prompts, then restart your shell or run:  
  source "$HOME/.cargo/env"

* **Configure Rust for Android**: Add the Android cross-compilation targets to your Rust installation:  
  rustup target add aarch64-linux-android armv7-linux-androideabi x86\_64-linux-android i686-linux-android  
  cargo install cargo-ndk

* **Install Android Studio & NDK**:  
  1. Download the **Android Studio** .tar.gz package for Linux from [https://developer.android.com/studio](https://developer.android.com/studio).  
  2. Extract the archive to a suitable location, such as /opt/ or \~/.  
  3. Run the installer script: cd android-studio/bin/ && ./studio.sh.  
  4. In the setup wizard, choose the **Standard** installation.  
  5. After installation, launch Android Studio, navigate to **More Actions → SDK Manager**.  
  6. Go to the **SDK Tools** tab, check **Show Package Details**, and select **NDK (Side by side) 25.2.9519653**.  
  7. Click **Apply** to install the NDK.  
* **Set Environment Variables**: For command-line tools to function correctly, set ANDROID\_HOME and ANDROID\_NDK\_HOME. Add the following lines to your \~/.bashrc or \~/.zshrc file.  
  \# Add to your \~/.bashrc or \~/.zshrc  
  export ANDROID\_HOME="$HOME/Android/Sdk"  
  export ANDROID\_NDK\_HOME="$ANDROID\_HOME/ndk/25.2.9519653"  
  export PATH="$ANDROID\_HOME/cmdline-tools/latest/bin:$ANDROID\_HOME/platform-tools:$PATH"

  \# Apply the changes to your current shell  
  source \~/.bashrc

**3\. Build Host OpenSSL FIPS Provider**

This step builds a FIPS provider for your Linux host machine, which is needed to validate the FIPS module itself.

**Prerequisite:** Download the OpenSSL source code (e.g., openssl-fips-3.1.2.tar.gz) and extract it. These commands assume the extracted folder is your current directory.

From within a terminal:

1. **Configure**:  
   \# From inside the openssl-fips-3.1.2 source directory  
   ./config enable-fips no-shared \--prefix=/opt/openssl-out/fips-3.1.2

2. **Build & Install**:  
   make \-j$(nproc)  
   sudo make install\_sw  \# Installs host libraries  
   sudo make install\_fips  \# Installs fips.so

3. **Generate FIPS Config**: This final step runs the FIPS self-test and generates the configuration file needed by the module.  
   openssl fipsinstall \-out /opt/openssl-out/fips-3.1.2/fipsmodule.cnf \-module /opt/openssl-out/fips-3.1.2/lib64/ossl-modules/fips.so

### **B. Project Workspace & Build**

With the one-time setup complete, you can now set up the project and run the build.

**4\. Define Directory Structure**

Create a GitHub directory in your home folder. All repository clones will live here. The guide will use the following conventions:

* \~/GitHub/signal-upstream: A shallow clone of the official Signal-Android repository.  
* \~/GitHub/fips-signal-android: Your organization's fork where FIPS integration occurs.

**5\. Clone Repositories**

Open a terminal and navigate to your projects directory to clone the necessary repositories.

mkdir \-p \~/GitHub && cd \~/GitHub  
\# Clone the specific upstream version tag  
git clone \--depth 1 \--branch v7.48.0 https://github.com/signalapp/Signal-Android.git signal-upstream  
\# Clone your organization's repository  
git clone https://github.com/\<YOUR-ORG\>/fips-signal-android.git

**6\. Cross-Compile OpenSSL for Android**

This critical step compiles libcrypto.so with embedded FIPS support for each required Android architecture (ABI).

**Prerequisite:** Download the OpenSSL source code (e.g., openssl-3.5.0.tar.gz) and extract it to a workspace like \~/openssl-build/openssl-3.5.0.

For **each** target ABI (arm64-v8a, armeabi-v7a, x86\_64, x86), run the following process from a terminal. The example below is for arm64-v8a.

1. **Set Environment:** Point the build tools to your Android NDK.  
   export PATH=$ANDROID\_NDK\_HOME/toolchains/llvm/prebuilt/linux-x86\_64/bin:$PATH

2. **Clean & Configure:**  
   \# Navigate to the OpenSSL source directory  
   make clean  
   ./Configure android-arm64 \-D\_\_ANDROID\_API\_\_=23 enable-fips \--prefix=/opt/openssl-out/arm64-v8a

3. **Build & Install:**  
   make \-j$(nproc)  
   make install\_sw

**Important:** Repeat the clean, configure, build, and install steps for all other ABIs, replacing android-arm64 and the output directory path (arm64-v8a) accordingly. (Use android-arm for armeabi-v7a, android-x86\_64 for x86\_64, and android-x86 for x86).

**7\. Integrate and Build the App**

Now, combine the upstream source, your custom FIPS code, and the compiled OpenSSL libraries.

1. **Sync Upstream Source:** Use rsync to copy the latest source and resource files from the upstream clone into your FIPS repository.  
   \# From \~/GitHub/  
   rsync \-a \--delete signal-upstream/app/src/ fips-signal-android/app/src/  
   rsync \-a \--delete signal-upstream/app/res/ fips-signal-android/app/res/

2. **Integrate Custom Files:** Move your custom FIPS-related Kotlin/JNI files from their staging location into the appropriate source directories within fips-signal-android using git mv. Ensure the package declaration in each Kotlin file matches its new directory path.  
3. **Copy Compiled Libraries:** Copy the libcrypto.so artifacts you built into the project's JNI library folder.  
   \# Repeat for each ABI  
   cp /opt/openssl-out/arm64-v8a/lib64/libcrypto.so \~/GitHub/fips-signal-android/fips-crypto-bridge/libs/openssl/arm64-v8a/  
   cp /opt/openssl-out/armeabi-v7a/lib/libcrypto.so \~/GitHub/fips-signal-android/fips-crypto-bridge/libs/openssl/armeabi-v7a/  
   \# ... etc. for x86\_64 and x86, noting lib vs lib64 paths

4. **Run Local Build:** Navigate to your repository and run the Gradle wrapper to build the debug APK.  
   cd \~/GitHub/fips-signal-android  
   ./gradlew clean assembleDebug

   A BUILD SUCCESSFUL message indicates the process worked.

### **C. Development, Testing & Maintenance**

* **Running the App:** Open the fips-signal-android project in Android Studio (\~/android-studio/bin/studio.sh) and use the standard **Run ▶** button.  
* **Committing Changes:** Once you've integrated a new upstream version and your FIPS modules, commit and push the changes.  
  git add .  
  git commit \-m "Integrate upstream v7.48.0 and FIPS modules"  
  git push \-u origin integrate-fips

* **Monthly Upstream Refresh:** To update your fork with a newer version of Signal, follow the refresh process:  
  \# 1\. Update the upstream repo  
  cd \~/GitHub/signal-upstream  
  git fetch \--tags  
  git checkout v\<NEW\_TAG\>

  \# 2\. Sync sources to your repo and build  
  cd ../fips-signal-android  
  rsync \-a \--delete ../signal-upstream/app/src/ app/src/  
  rsync \-a \--delete ../signal-upstream/app/res/ app/res/  
  ./gradlew assembleDebug

  \# 3\. Commit the update  
  git add app/src app/res  
  git commit \-m "Bump upstream to v\<NEW\_TAG\>"  
  git push

* **Security Hygiene:**  
  * Keep your system updated with sudo apt update && sudo apt upgrade.  
  * Always verify the SHA-256 checksums for any downloaded FIPS module binaries.

