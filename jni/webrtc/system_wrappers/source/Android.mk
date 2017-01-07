# Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
#
# Use of this source code is governed by a BSD-style license
# that can be found in the LICENSE file in the root of the source
# tree. An additional intellectual property rights grant can be found
# in the file PATENTS.  All contributing project authors may
# be found in the AUTHORS file in the root of the source tree.

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

include $(LOCAL_PATH)/../../../android-webrtc.mk

LOCAL_ARM_MODE := arm
LOCAL_MODULE := libwebrtc_system_wrappers
LOCAL_MODULE_TAGS := optional
LOCAL_CPP_EXTENSION := .cc
LOCAL_SRC_FILES := \
    cpu_features_android.c \
    sort.cc \
    aligned_malloc.cc \
    atomic32_non_darwin_unix.cc \
    cpu_features.cc \
    cpu_info.cc \
    event.cc \
    event_timer_posix.cc \
    file_impl.cc \
    rw_lock.cc \
    trace_impl.cc \
    rtp_to_ntp.cc \
    sleep.cc \
    timestamp_extrapolator.cc \
    trace_posix.cc \
    rw_lock_posix.cc \
    metrics_default.cc \
    logging.cc \
    clock.cc

LOCAL_CFLAGS := \
    $(MY_WEBRTC_COMMON_DEFS) \
    -DWEBRTC_POSIX

LOCAL_C_INCLUDES := \
    $(LOCAL_PATH)/../.. \
    $(LOCAL_PATH)/../interface \
    $(LOCAL_PATH)/spreadsortlib \
    $(LOCAL_PATH)/../../..

LOCAL_SHARED_LIBRARIES := \
    libcutils \
    libdl \
    libstlport

LOCAL_STATIC_LIBRARIES := cpufeatures

ifndef NDK_ROOT
include external/stlport/libstlport.mk
endif
include $(BUILD_STATIC_LIBRARY)

$(call import-module,android/cpufeatures)
