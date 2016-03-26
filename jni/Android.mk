JNI_DIR := $(call my-dir)

include $(JNI_DIR)/libspeex/Android.mk

include $(JNI_DIR)/webrtc/common_audio/signal_processing/Android.mk
include $(JNI_DIR)/webrtc/modules/audio_processing/aec/Android.mk
include $(JNI_DIR)/webrtc/modules/audio_processing/aecm/Android.mk
include $(JNI_DIR)/webrtc/modules/audio_processing/agc/Android.mk
include $(JNI_DIR)/webrtc/modules/audio_processing/ns/Android.mk
include $(JNI_DIR)/webrtc/modules/audio_processing/utility/Android.mk
include $(JNI_DIR)/webrtc/system_wrappers/source/Android.mk

include $(JNI_DIR)/webrtc/modules/audio_coding/neteq/Android.mk
include $(JNI_DIR)/webrtc/modules/audio_coding/codecs/g711/Android.mk
include $(JNI_DIR)/webrtc/modules/audio_coding/codecs/cng/Android.mk
include $(JNI_DIR)/webrtc/common_audio/vad/Android.mk

include $(JNI_DIR)/openssl/Android.mk

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE     := redphone-audio
LOCAL_C_INCLUDES := $(JNI_DIR)/libsrtp/include/ $(JNI_DIR)/libsrtp/crypto/include/ $(JNI_DIR)/libspeex/include/ $(JNI_DIR)/webrtc/ $(JNI_DIR)/openssl/include/ $(JNI_DIR)
LOCAL_LDLIBS     += -lOpenSLES -llog
LOCAL_CFLAGS     += -Wall

LOCAL_SRC_FILES := \
$(JNI_DIR)/redphone/MicrophoneReader.cpp \
$(JNI_DIR)/redphone/AudioCodec.cpp \
$(JNI_DIR)/redphone/RtpAudioSender.cpp \
$(JNI_DIR)/redphone/RtpPacket.cpp \
$(JNI_DIR)/redphone/RtpAudioReceiver.cpp \
$(JNI_DIR)/redphone/AudioPlayer.cpp \
$(JNI_DIR)/redphone/JitterBuffer.cpp \
$(JNI_DIR)/redphone/CallAudioManager.cpp \
$(JNI_DIR)/redphone/WebRtcJitterBuffer.cpp \
$(JNI_DIR)/redphone/SrtpStream.cpp \
$(JNI_DIR)/redphone/NetworkUtil.cpp

LOCAL_STATIC_LIBRARIES := \
libspeex \
libwebrtc_aecm \
libwebrtc_ns \
libwebrtc_spl \
libwebrtc_apm_utility \
libwebrtc_system_wrappers \
libwebrtc_neteq \
libwebrtc_g711 \
libwebrtc_cng \
libwebrtc_spl \
libwebrtc_vad \
libcrypto_static

include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)

LOCAL_MODULE     := native-utils
LOCAL_C_INCLUDES := $(JNI_DIR)/utils/
LOCAL_CFLAGS     += -Wall

LOCAL_SRC_FILES := $(JNI_DIR)/utils/org_thoughtcrime_securesms_util_FileUtils.cpp

include $(BUILD_SHARED_LIBRARY)