
/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_processing/aecm/aecm_core.h"

#include <stddef.h>

// Define offset variables that will be compiled and abstracted to constant
// defines, which will then only be used in ARM assembly code.
int offset_aecm_dfaCleanQDomain = offsetof(AecmCore_t, dfaCleanQDomain);
int offset_aecm_outBuf = offsetof(AecmCore_t, outBuf);
int offset_aecm_xBuf = offsetof(AecmCore_t, xBuf);
int offset_aecm_dBufNoisy = offsetof(AecmCore_t, dBufNoisy);
int offset_aecm_dBufClean = offsetof(AecmCore_t, dBufClean);
int offset_aecm_channelStored = offsetof(AecmCore_t, channelStored);
int offset_aecm_channelAdapt16 = offsetof(AecmCore_t, channelAdapt16);
int offset_aecm_channelAdapt32 = offsetof(AecmCore_t, channelAdapt32);
int offset_aecm_real_fft = offsetof(AecmCore_t, real_fft);
