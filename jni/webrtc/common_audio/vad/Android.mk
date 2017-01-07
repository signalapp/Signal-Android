
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

include $(LOCAL_PATH)/../../../android-webrtc.mk

LOCAL_ARM_MODE := arm
LOCAL_MODULE_CLASS := STATIC_LIBRARIES
LOCAL_MODULE := libwebrtc_vad
LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES := \
    webrtc_vad.c \
    vad_core.c \
    vad_filterbank.c \
    vad_gmm.c \
    vad_sp.c \
    vad.cc

# Flags passed to both C and C++ files.
LOCAL_CFLAGS := \
    $(MY_WEBRTC_COMMON_DEFS)

LOCAL_C_INCLUDES := \
    $(LOCAL_PATH)/include \
    $(LOCAL_PATH)/../.. \
    $(LOCAL_PATH)/../../../ \
    $(LOCAL_PATH)/../signal_processing/include

LOCAL_SHARED_LIBRARIES := \
    libdl \
    libstlport \
    libwebrtc_spl

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

