CODING_PATH := $(call my-dir)

include $(CLEAR_VARS)

include $(CODING_PATH)/../../../android-webrtc.mk

include $(CODING_PATH)/acm2/Android.mk
include $(CODING_PATH)/codecs/Android.mk
include $(CODING_PATH)/codecs/cng/Android.mk
include $(CODING_PATH)/codecs/g711/Android.mk
include $(CODING_PATH)/codecs/isac/Android.mk
include $(CODING_PATH)/codecs/pcm16b/Android.mk
include $(CODING_PATH)/neteq/Android.mk

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE     := libwebrtc_audio_coding 
LOCAL_CFLAGS     += -Wall -std=c++11

LOCAL_WHOLE_STATIC_LIBRARIES := \
  libwebrtc_acm2 \
  libwebrtc_codec \
  libwebrtc_cng \
  libwebrtc_g711 \
  libwebrtc_isac \
  libwebrtc_pcm16b \
  libwebrtc_neteq 

include $(BUILD_STATIC_LIBRARY)
