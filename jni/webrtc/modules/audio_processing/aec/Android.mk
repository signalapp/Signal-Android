# Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
#
# Use of this source code is governed by a BSD-style license
# that can be found in the LICENSE file in the root of the source
# tree. An additional intellectual property rights grant can be found
# in the file PATENTS.  All contributing project authors may
# be found in the AUTHORS file in the root of the source tree.

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

include $(LOCAL_PATH)/../../../../android-webrtc.mk

LOCAL_MODULE_CLASS := STATIC_LIBRARIES
LOCAL_MODULE := libwebrtc_aec
LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES := \
    echo_cancellation.c \
    aec_resampler.c \
    aec_core.c \
    aec_rdft.c \

ifeq ($(TARGET_ARCH),x86)
LOCAL_SRC_FILES += \
    aec_core_sse2.c \
    aec_rdft_sse2.c
endif

# Flags passed to both C and C++ files.
LOCAL_CFLAGS := \
    $(MY_WEBRTC_COMMON_DEFS)

LOCAL_C_INCLUDES := \
    $(LOCAL_PATH)/include \
    $(LOCAL_PATH)/../utility \
    $(LOCAL_PATH)/../../.. \
    $(LOCAL_PATH)/../../../common_audio/signal_processing/include \
    $(LOCAL_PATH)/../../../..

LOCAL_SHARED_LIBRARIES := \
    libcutils \
    libdl \
    libstlport

ifndef NDK_ROOT
include external/stlport/libstlport.mk
endif
include $(BUILD_STATIC_LIBRARY)
