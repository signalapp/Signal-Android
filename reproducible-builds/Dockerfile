FROM debian:stretch

COPY docker/ docker/
COPY docker/apt.conf docker/sources.list /etc/apt/

RUN dpkg --add-architecture i386

RUN apt-get update -y && apt-get install -y apt-utils

RUN apt-get update -y && apt-get install -y $(cat docker/dependencies.txt)
RUN docker/print-versions.sh docker/dependencies.txt

ENV ANDROID_SDK_FILENAME        android-sdk_r24.4.1-linux.tgz
ENV ANDROID_API_LEVELS          android-29
ENV ANDROID_BUILD_TOOLS_VERSION 29.0.3

ENV ANDROID_HOME /usr/local/android-sdk-linux
ENV PATH         ${PATH}:${ANDROID_HOME}/tools:${ANDROID_HOME}/platform-tools

RUN cd /usr/local/ && \
    wget -q "https://dl.google.com/android/${ANDROID_SDK_FILENAME}" && \
    tar -xzf ${ANDROID_SDK_FILENAME} && \
    rm ${ANDROID_SDK_FILENAME} 
RUN echo y | android update sdk --no-ui -a --filter ${ANDROID_API_LEVELS}
RUN echo y | android update sdk --no-ui -a --filter extra-android-m2repository,extra-android-support,extra-google-google_play_services,extra-google-m2repository
RUN echo y | android update sdk --no-ui -a --filter tools,platform-tools,build-tools-${ANDROID_BUILD_TOOLS_VERSION}
RUN rm -rf ${ANDROID_HOME}/tools

