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
        android-sdk=25.0.0+11+deb10u1 \
        android-sdk-build-tools=27.0.1+11+deb10u1 \
        android-sdk-platform-tools=27.0.0+11+deb10u1 \
        git \
        lib32z1=1:1.2.11.dfsg-1 \
        libc6:i386=2.28-10 \
        libncurses6:i386=6.1+20181013-2+deb10u2 \
        libstdc++6:i386=8.3.0-6 \
        openjdk-11-jdk=11.0.7+10-3~deb10u1 \
        opensc \
        pcscd \
        wget \
        && \
    rm -rf /var/lib/apt/lists/*
