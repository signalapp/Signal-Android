LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE     := libspeex
LOCAL_CFLAGS     := -DFIXED_POINT -DUSE_KISS_FFT -DEXPORT="" -UHAVE_CONFIG_H
LOCAL_C_INCLUDES := $(LOCAL_PATH)/include

LOCAL_SRC_FILES :=  \
$(LOCAL_PATH)/bits.c \
$(LOCAL_PATH)/buffer.c \
$(LOCAL_PATH)/cb_search.c \
$(LOCAL_PATH)/exc_10_16_table.c \
$(LOCAL_PATH)/exc_10_32_table.c \
$(LOCAL_PATH)/exc_20_32_table.c \
$(LOCAL_PATH)/exc_5_256_table.c \
$(LOCAL_PATH)/exc_5_64_table.c \
$(LOCAL_PATH)/exc_8_128_table.c \
$(LOCAL_PATH)/fftwrap.c \
$(LOCAL_PATH)/filterbank.c \
$(LOCAL_PATH)/filters.c \
$(LOCAL_PATH)/gain_table.c \
$(LOCAL_PATH)/gain_table_lbr.c \
$(LOCAL_PATH)/hexc_10_32_table.c \
$(LOCAL_PATH)/hexc_table.c \
$(LOCAL_PATH)/high_lsp_tables.c \
$(LOCAL_PATH)/jitter.c \
$(LOCAL_PATH)/kiss_fft.c \
$(LOCAL_PATH)/kiss_fftr.c \
$(LOCAL_PATH)/lpc.c \
$(LOCAL_PATH)/lsp.c \
$(LOCAL_PATH)/lsp_tables_nb.c \
$(LOCAL_PATH)/ltp.c \
$(LOCAL_PATH)/mdf.c \
$(LOCAL_PATH)/modes.c \
$(LOCAL_PATH)/modes_wb.c \
$(LOCAL_PATH)/nb_celp.c \
$(LOCAL_PATH)/preprocess.c \
$(LOCAL_PATH)/quant_lsp.c \
$(LOCAL_PATH)/resample.c \
$(LOCAL_PATH)/sb_celp.c \
$(LOCAL_PATH)/scal.c \
$(LOCAL_PATH)/smallft.c \
$(LOCAL_PATH)/speex.c \
$(LOCAL_PATH)/speex_callbacks.c \
$(LOCAL_PATH)/speex_header.c \
$(LOCAL_PATH)/stereo.c \
$(LOCAL_PATH)/vbr.c \
$(LOCAL_PATH)/vq.c \
$(LOCAL_PATH)/window.c

include $(BUILD_STATIC_LIBRARY)
