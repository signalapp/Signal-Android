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
        libc6:i386=2.28-10 \
        libncurses6:i386=6.1+20181013-2+deb10u2 \
        libstdc++6:i386=8.3.0-6 \
        lib32z1=1:1.2.11.dfsg-1 \
        wget \
        openjdk-11-jdk=11.0.7+10-3~deb10u1 \
        git \
        opensc \
        pcscd \
        && \
    rm -rf /var/lib/apt/lists/*

ENV ANDROID_SDK_FILENAME android-sdk_r24.4.1-linux.tgz
ENV ANDROID_SDK_URL https://dl.google.com/android/${ANDROID_SDK_FILENAME}
ENV ANDROID_API_LEVELS android-28
ENV ANDROID_BUILD_TOOLS_VERSION 28.0.3
ENV ANDROID_HOME /usr/local/android-sdk-linux
ENV PATH ${PATH}:${ANDROID_HOME}/tools:${ANDROID_HOME}/platform-tools
RUN cd /usr/local/ && \
    wget -q ${ANDROID_SDK_URL} && \
    tar --no-same-owner -xzf ${ANDROID_SDK_FILENAME} && \
    rm ${ANDROID_SDK_FILENAME} 
RUN echo y | android update sdk --no-ui -a --filter ${ANDROID_API_LEVELS}
RUN echo y | android update sdk --no-ui -a --filter extra-android-m2repository,extra-android-support,extra-google-google_play_services,extra-google-m2repository
RUN echo y | android update sdk --no-ui -a --filter tools,platform-tools,build-tools-${ANDROID_BUILD_TOOLS_VERSION}
RUN rm -rf ${ANDROID_HOME}/tools
