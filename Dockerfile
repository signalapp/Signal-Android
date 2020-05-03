FROM debian:buster-slim

RUN apt-get update && apt-get install -y \
        gpg

COPY keys/snapshot.debian.org/buster.pubkey /etc/apt/buster.pubkey
COPY keys/snapshot.debian.org/buster-updates.pubkey /etc/apt/buster-updates.pubkey
COPY keys/snapshot.debian.org/buster-security.pubkey /etc/apt/buster-security.pubkey
RUN apt-key add /etc/apt/buster.pubkey && \
    apt-key add /etc/apt/buster-updates.pubkey && \
    apt-key add /etc/apt/buster-security.pubkey

ENV SNAPSHOT "20200502T085134Z"

RUN rm /etc/apt/sources.list && \
    printf "deb http://snapshot.debian.org/archive/debian/${SNAPSHOT}/ buster main\n" >> /etc/apt/sources.list && \
    printf "deb http://snapshot.debian.org/archive/debian-security/${SNAPSHOT}/ buster/updates main\n" >> /etc/apt/sources.list && \
    printf "deb http://snapshot.debian.org/archive/debian/${SNAPSHOT}/ buster-updates main\n" >> /etc/apt/sources.list

# https://bugs.debian.org/cgi-bin/bugreport.cgi?bug=863199#23
RUN mkdir -p /usr/share/man/man1

RUN dpkg --add-architecture i386 && \
    apt-get update && apt-get install -y \
        git \
        lib32z1=1:1.2.11.dfsg-1 \
        libc6:i386=2.28-10 \
        libncurses6:i386=6.1+20181013-2+deb10u2 \
        libstdc++6:i386=8.3.0-6 \
        openjdk-11-jdk=11.0.7+10-3~deb10u1 \
        opensc \
        pcscd \
        unzip \
        wget \
        && \
    rm -rf /var/lib/apt/lists/*

ENV ANDROID_SDK_FILENAME commandlinetools-linux-6200805_latest.zip
ENV ANDROID_SDK_SHA f10f9d5bca53cc27e2d210be2cbc7c0f1ee906ad9b868748d74d62e10f2c8275
ENV ANDROID_SDK_URL https://dl.google.com/android/repository/${ANDROID_SDK_FILENAME}
ENV ANDROID_API_LEVELS android-28
ENV ANDROID_BUILD_TOOLS_VERSION 28.0.3
ENV ANDROID_HOME /usr/local/android-sdk-linux

RUN wget -q ${ANDROID_SDK_URL} && \
    echo ${ANDROID_SDK_SHA} ${ANDROID_SDK_FILENAME} | sha256sum -c \
    unzip -q ${ANDROID_SDK_FILENAME} -d ${ANDROID_HOME} && \
    rm ${ANDROID_SDK_FILENAME}

ENV PATH ${PATH}:${ANDROID_HOME}/tools/bin
RUN yes | sdkmanager --licenses --sdk_root=${ANDROID_HOME} && \
    sdkmanager --install --sdk_root=${ANDROID_HOME} "platforms;${ANDROID_API_LEVELS}" && \
    sdkmanager --install --sdk_root=${ANDROID_HOME} "build-tools;${ANDROID_BUILD_TOOLS_VERSION}" && \
    sdkmanager --install --sdk_root=${ANDROID_HOME} "platform-tools"
