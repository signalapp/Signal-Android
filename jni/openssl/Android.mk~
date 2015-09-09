LOCAL_PATH := $(call my-dir)

# Enable to be able to use ALOG* with #include "cutils/log.h"
#log_c_includes += system/core/include
#log_shared_libraries := liblog

# These makefiles are here instead of being Android.mk files in the
# respective crypto, ssl, and apps directories so
# that import_openssl.sh import won't remove them.
include $(LOCAL_PATH)/build-config-64.mk
include $(LOCAL_PATH)/build-config-32.mk
include $(LOCAL_PATH)/Crypto.mk
include $(LOCAL_PATH)/Ssl.mk
include $(LOCAL_PATH)/Apps.mk
