LOCAL_DIR := $(GET_LOCAL_DIR)

MODULE := $(LOCAL_DIR)

TARGET_ARCH := $(ARCH)
TARGET_2ND_ARCH := $(ARCH)

# Reset local variables
LOCAL_CFLAGS :=
LOCAL_C_INCLUDES :=
LOCAL_SRC_FILES_$(TARGET_ARCH) :=
LOCAL_SRC_FILES_$(TARGET_2ND_ARCH) :=
LOCAL_CFLAGS_$(TARGET_ARCH) :=
LOCAL_CFLAGS_$(TARGET_2ND_ARCH) :=
LOCAL_ADDITIONAL_DEPENDENCIES :=

# get openssl_cflags
MODULE_SRCDEPS += $(LOCAL_DIR)/build-config-trusty.mk
include $(LOCAL_DIR)/build-config-trusty.mk

# get target_c_flags, target_c_includes, target_src_files
MODULE_SRCDEPS += $(LOCAL_DIR)/Crypto-config-trusty.mk
include $(LOCAL_DIR)/Crypto-config-trusty.mk

MODULE_SRCS += $(addprefix $(LOCAL_DIR)/,$(LOCAL_SRC_FILES_$(ARCH)))

MODULE_CFLAGS += $(LOCAL_CFLAGS)
MODULE_CFLAGS += -Wno-error=implicit-function-declaration

# Global for other modules which include openssl headers
GLOBAL_DEFINES += OPENSSL_SYS_TRUSTY

LOCAL_C_INCLUDES := $(patsubst external/openssl/%,%,$(LOCAL_C_INCLUDES))
GLOBAL_INCLUDES += $(addprefix $(LOCAL_DIR)/,$(LOCAL_C_INCLUDES))

MODULE_DEPS := \
	lib/openssl-stubs \
	lib/libc-trusty

include make/module.mk
