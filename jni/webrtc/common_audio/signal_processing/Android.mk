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
LOCAL_MODULE_CLASS := STATIC_LIBRARIES
LOCAL_MODULE := libwebrtc_spl
LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES := \
    auto_corr_to_refl_coef.c \
    auto_correlation.c \
    complex_fft.c \
    copy_set_operations.c \
    cross_correlation.c \
    division_operations.c \
    dot_product_with_scale.c \
    downsample_fast.c \
    energy.c \
    filter_ar.c \
    filter_ma_fast_q12.c \
    get_hanning_window.c \
    get_scaling_square.c \
    ilbc_specific_functions.c \
    levinson_durbin.c \
    lpc_to_refl_coef.c \
    min_max_operations.c \
    randomization_functions.c \
    real_fft.c \
    refl_coef_to_lpc.c \
    resample.c \
    resample_48khz.c \
    resample_by_2.c \
    resample_by_2_internal.c \
    resample_fractional.c \
    spl_init.c \
    spl_sqrt.c \
    spl_version.c \
    splitting_filter.c \
    sqrt_of_one_minus_x_squared.c \
    vector_scaling_operations.c

# Flags passed to both C and C++ files.
LOCAL_CFLAGS := \
    $(MY_WEBRTC_COMMON_DEFS) \
    -DWEBRTC_POSIX

LOCAL_C_INCLUDES := \
    $(LOCAL_PATH)/include \
    $(LOCAL_PATH)/../.. \
    $(LOCAL_PATH)/../../..

ifeq ($(ARCH_ARM_HAVE_ARMV7A),true)
LOCAL_SRC_FILES += \
    filter_ar_fast_q12_armv7.S
else
LOCAL_SRC_FILES += \
    filter_ar_fast_q12.c
endif

ifeq ($(TARGET_ARCH),arm)
LOCAL_SRC_FILES += \
    complex_bit_reverse_arm.S \
    spl_sqrt_floor_arm.S
else
LOCAL_SRC_FILES += \
    complex_bit_reverse.c \
    spl_sqrt_floor.c
endif

LOCAL_SHARED_LIBRARIES := libstlport

ifeq ($(TARGET_OS)-$(TARGET_SIMULATOR),linux-true)
LOCAL_LDLIBS += -ldl -lpthread
endif

ifneq ($(TARGET_SIMULATOR),true)
LOCAL_SHARED_LIBRARIES += libdl
endif

ifndef NDK_ROOT
include external/stlport/libstlport.mk
endif
include $(BUILD_STATIC_LIBRARY)

#########################
# Build the neon library.
ifeq ($(WEBRTC_BUILD_NEON_LIBS),true)

include $(CLEAR_VARS)

LOCAL_ARM_MODE := arm
LOCAL_MODULE_CLASS := STATIC_LIBRARIES
LOCAL_MODULE := libwebrtc_spl_neon
LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES := \
    cross_correlation_neon.S \
    downsample_fast_neon.S \
    min_max_operations_neon.S \
    vector_scaling_operations_neon.S

# Flags passed to both C and C++ files.
LOCAL_CFLAGS := \
    $(MY_WEBRTC_COMMON_DEFS) \
    $(MY_ARM_CFLAGS_NEON)

LOCAL_C_INCLUDES := \
    $(LOCAL_PATH)/include \
    $(LOCAL_PATH)/../.. \
    external/webrtc

ifndef NDK_ROOT
include external/stlport/libstlport.mk
endif
include $(BUILD_STATIC_LIBRARY)

endif # ifeq ($(WEBRTC_BUILD_NEON_LIBS),true)

