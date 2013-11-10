LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := libcurve25519-donna
LOCAL_SRC_FILES := curve25519-donna.c

include $(BUILD_STATIC_LIBRARY)

include $(CLEAR_VARS)

LOCAL_MODULE    := libcurve25519
LOCAL_SRC_FILES := curve25519-donna-jni.c

LOCAL_STATIC_LIBRARIES := libcurve25519-donna

include $(BUILD_SHARED_LIBRARY)