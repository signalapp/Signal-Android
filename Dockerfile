FROM openjdk:8-jdk

ENV DEBIAN_FRONTEND=noninteractive \
    DEBIAN_PRIORITY=critical \
    DEBCONF_NOWARNINGS=yes

RUN set -ex \
        && dpkg --add-architecture i386 \
        && apt-get update -qqy \
        && apt-get -qqy --no-install-recommends install \
            software-properties-common \
            libc6:i386=2.24-11+deb9u1 \
            libncurses5:i386=6.0+20161126-1+deb9u1 \
            libstdc++6:i386=6.3.0-18+deb9u1 \
            lib32z1=1:1.2.8.dfsg-5 \
            wget git unzip \
        && apt-get purge -y \
        && apt-get autoremove -y \
        && apt-get clean \
        && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

ENV ANDROID_SDK_FILENAME android-sdk_r24.4.1-linux.tgz
ENV ANDROID_SDK_SHA256 e16917ad685c1563ccbc5dd782930ee1a700a1b6a6fd3e44b83ac694650435e9
ENV ANDROID_SDK_URL https://dl.google.com/android/${ANDROID_SDK_FILENAME}
ENV ANDROID_API_LEVELS android-27
ENV ANDROID_BUILD_TOOLS_VERSION 27.0.1
ENV ANDROID_HOME /usr/local/android-sdk-linux
ENV PATH ${PATH}:${ANDROID_HOME}/tools:${ANDROID_HOME}/platform-tools

RUN cd /usr/local/ \
    && wget -q ${ANDROID_SDK_URL} \
    && echo "${ANDROID_SDK_SHA256} ${ANDROID_SDK_FILENAME}" | sha256sum -c - \
    && tar -xzf ${ANDROID_SDK_FILENAME} \
    && rm ${ANDROID_SDK_FILENAME}
RUN echo y | android update sdk --no-ui -a --filter ${ANDROID_API_LEVELS}
RUN echo y | android update sdk --no-ui -a --filter extra-android-m2repository,extra-android-support,extra-google-google_play_services,extra-google-m2repository
RUN echo y | android update sdk --no-ui -a --filter tools,platform-tools,build-tools-${ANDROID_BUILD_TOOLS_VERSION}
RUN rm -rf ${ANDROID_HOME}/tools
