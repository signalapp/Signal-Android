FROM debian:stretch

COPY docker/ docker/
COPY docker/apt.conf docker/sources.list /etc/apt/

RUN dpkg --add-architecture i386

RUN apt-get update -y && apt-get install -y apt-utils

RUN apt-get update -y && apt-get install -y $(cat docker/dependencies.txt)
RUN docker/print-versions.sh docker/dependencies.txt

ENV ANDROID_COMMAND_LINE_TOOLS_FILENAME commandlinetools-linux-7583922_latest.zip
ENV ANDROID_API_LEVELS                  android-33
ENV ANDROID_BUILD_TOOLS_VERSION         32.0.0

ENV ANDROID_HOME /usr/local/android-sdk-linux
ENV PATH         ${PATH}:${ANDROID_HOME}/tools:${ANDROID_HOME}/platform-tools:${ANDROID_HOME}/cmdline-tools/bin

RUN cd /usr/local/
RUN wget -q "https://dl.google.com/android/repository/${ANDROID_COMMAND_LINE_TOOLS_FILENAME}"
RUN unzip ${ANDROID_COMMAND_LINE_TOOLS_FILENAME} -d /usr/local/android-sdk-linux
RUN rm ${ANDROID_COMMAND_LINE_TOOLS_FILENAME}

RUN yes | sdkmanager --update --sdk_root="${ANDROID_HOME}"
RUN yes | sdkmanager --sdk_root="${ANDROID_HOME}" "platforms;${ANDROID_API_LEVELS}" "build-tools;${ANDROID_BUILD_TOOLS_VERSION}" "extras;google;m2repository" "extras;android;m2repository" "extras;google;google_play_services"

RUN update-java-alternatives -s java-1.8.0-openjdk-amd64
RUN yes | sdkmanager --licenses --sdk_root="${ANDROID_HOME}"
RUN update-java-alternatives -s java-1.11.0-openjdk-amd64

RUN rm -rf ${ANDROID_HOME}/tools

RUN docker/gradlewarmer/gradlew --version
