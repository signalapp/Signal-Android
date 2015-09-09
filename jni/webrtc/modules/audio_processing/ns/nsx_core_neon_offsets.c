/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_processing/ns/nsx_core.h"

#include <stddef.h>

// Define offset variables that will be compiled and abstracted to constant
// defines, which will then only be used in ARM assembly code.
int offset_nsx_anaLen = offsetof(NsxInst_t, anaLen);
int offset_nsx_anaLen2 = offsetof(NsxInst_t, anaLen2);
int offset_nsx_normData = offsetof(NsxInst_t, normData);
int offset_nsx_analysisBuffer = offsetof(NsxInst_t, analysisBuffer);
int offset_nsx_synthesisBuffer = offsetof(NsxInst_t, synthesisBuffer);
int offset_nsx_blockLen10ms = offsetof(NsxInst_t, blockLen10ms);
int offset_nsx_window = offsetof(NsxInst_t, window);
int offset_nsx_real = offsetof(NsxInst_t, real);
int offset_nsx_imag = offsetof(NsxInst_t, imag);
int offset_nsx_noiseSupFilter = offsetof(NsxInst_t, noiseSupFilter);
int offset_nsx_magnLen = offsetof(NsxInst_t, magnLen);
int offset_nsx_noiseEstLogQuantile = offsetof(NsxInst_t, noiseEstLogQuantile);
int offset_nsx_noiseEstQuantile = offsetof(NsxInst_t, noiseEstQuantile);
int offset_nsx_qNoise = offsetof(NsxInst_t, qNoise);
int offset_nsx_stages = offsetof(NsxInst_t, stages);
int offset_nsx_blockIndex = offsetof(NsxInst_t, blockIndex);
int offset_nsx_noiseEstCounter = offsetof(NsxInst_t, noiseEstCounter);
int offset_nsx_noiseEstDensity = offsetof(NsxInst_t, noiseEstDensity);
