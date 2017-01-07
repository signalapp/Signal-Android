# Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
#
# Use of this source code is governed by a BSD-style license
# that can be found in the LICENSE file in the root of the source
# tree. An additional intellectual property rights grant can be found
# in the file PATENTS.  All contributing project authors may
# be found in the AUTHORS file in the root of the source tree.

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

include $(LOCAL_PATH)/../../android-webrtc.mk

LOCAL_ARM_MODE := arm
LOCAL_MODULE := libwebrtc_base
LOCAL_MODULE_TAGS := optional
LOCAL_CPP_EXTENSION := .cc
LOCAL_SRC_FILES := \
  timeutils.cc \
  buffer.cc \
  bufferqueue.cc \
  bytebuffer.cc \
  checks.cc \
  common.cc \
  asyncfile.cc \
  asyncinvoker.cc \
  asyncpacketsocket.cc \
  asyncresolverinterface.cc \
  asyncsocket.cc \
  asynctcpsocket.cc \
  asyncudpsocket.cc \
  autodetectproxy.cc \
  bandwidthsmoother.cc \
  base64.cc \
  bitbuffer.cc \
  buffer.cc \
  bufferqueue.cc \
  bytebuffer.cc \
  checks.cc \
  common.cc \
  copyonwritebuffer.cc \
  crc32.cc \
  criticalsection.cc \
  cryptstring.cc \
  dbus.cc \
  diskcache.cc \
  event.cc \
  event_tracer.cc \
  exp_filter.cc \
  fakeclock.cc \
  filerotatingstream.cc \
  fileutils.cc \
  firewallsocketserver.cc \
  flags.cc \
  helpers.cc \
  httpbase.cc \
  httpclient.cc \
  httpcommon.cc \
  httprequest.cc \
  httpserver.cc \
  ifaddrs-android.cc \
  ifaddrs_converter.cc \
  ipaddress.cc \
  latebindingsymboltable.cc \
  libdbusglibsymboltable.cc \
  linux.cc \
  location.cc \
  logging.cc \
  logsinks.cc \
  md5.cc \
  md5digest.cc \
  messagedigest.cc \
  messagehandler.cc \
  messagequeue.cc \
  multipart.cc \
  natserver.cc \
  natsocketfactory.cc \
  nattypes.cc \
  nethelpers.cc \
  network.cc \
  networkmonitor.cc \
  nullsocketserver.cc \
  openssladapter.cc \
  openssldigest.cc \
  opensslidentity.cc \
  opensslstreamadapter.cc \
  optionsfile.cc \
  pathutils.cc \
  physicalsocketserver.cc \
  platform_file.cc \
  platform_thread.cc \
  posix.cc \
  profiler.cc \
  proxydetect.cc \
  proxyinfo.cc \
  proxyserver.cc \
  random.cc \
  rate_statistics.cc \
  ratetracker.cc \
  rtccertificate.cc \
  rtccertificategenerator.cc \
  sha1.cc \
  sha1digest.cc \
  sharedexclusivelock.cc \
  signalthread.cc \
  sigslot.cc \
  socketadapters.cc \
  socketaddress.cc \
  socketaddresspair.cc \
  socketpool.cc \
  socketstream.cc \
  ssladapter.cc \
  sslfingerprint.cc \
  sslidentity.cc \
  sslsocketfactory.cc \
  sslstreamadapter.cc \
  stream.cc \
  stringencode.cc \
  stringutils.cc \
  systeminfo.cc \
  task.cc \
  taskparent.cc \
  taskrunner.cc \
  thread.cc \
  thread_checker_impl.cc \
  worker.cc 


LOCAL_CFLAGS := \
    $(MY_WEBRTC_COMMON_DEFS) \
    -DWEBRTC_ANDROID \
    -DWEBRTC_POSIX \
    -DFEATURE_ENABLE_SSL \
    -DSSL_USE_OPENSSL 


LOCAL_C_INCLUDES := \
    $(LOCAL_PATH)/include \
    $(LOCAL_PATH)/../.. \
    $(LOCAL_PATH)/../../openssl/include/

LOCAL_SHARED_LIBRARIES := \
    libpthread \
    libcutils \
    libdl \
    libstlport

ifndef NDK_ROOT
include external/stlport/libstlport.mk
endif
include $(BUILD_STATIC_LIBRARY)
