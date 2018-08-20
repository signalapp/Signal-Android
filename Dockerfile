FROM ubuntu:17.04

RUN dpkg --add-architecture i386 && \
    apt-get update -y && \
    apt-get install -y software-properties-common && \
    apt-get update -y && \
    apt-get install -y libc6:i386=2.24-9ubuntu2.2 libncurses5:i386=6.0+20160625-1ubuntu1 libstdc++6:i386=6.3.0-12ubuntu2 lib32z1=1:1.2.11.dfsg-0ubuntu1 wget openjdk-8-jdk=8u131-b11-2ubuntu1.17.04.3 git unzip && \
    rm -rf /var/lib/apt/lists/* && \
    apt-get autoremove -y && \
    apt-get clean

ENV ANDROID_SDK_FILENAME android-sdk_r24.4.1-linux.tgz
ENV ANDROID_SDK_URL https://dl.google.com/android/${ANDROID_SDK_FILENAME}
ENV ANDROID_API_LEVELS android-27
ENV ANDROID_BUILD_TOOLS_VERSION 27.0.1
ENV ANDROID_HOME /usr/local/android-sdk-linux
ENV PATH ${PATH}:${ANDROID_HOME}/tools:${ANDROID_HOME}/platform-tools
RUN cd /usr/local/ && \
    wget -q ${ANDROID_SDK_URL} && \
    tar -xzf ${ANDROID_SDK_FILENAME} && \
    rm ${ANDROID_SDK_FILENAME} 
RUN echo y | android update sdk --no-ui -a --filter ${ANDROID_API_LEVELS}
RUN echo y | android update sdk --no-ui -a --filter extra-android-m2repository,extra-android-support,extra-google-google_play_services,extra-google-m2repository
RUN echo y | android update sdk --no-ui -a --filter tools,platform-tools,build-tools-${ANDROID_BUILD_TOOLS_VERSION}
RUN rm -rf ${ANDROID_HOME}/tools && unzip ${ANDROID_HOME}/temp/*.zip -d ${ANDROID_HOME}
