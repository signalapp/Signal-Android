/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_PROCESSING_AGC_MAIN_SOURCE_DIGITAL_AGC_H_
#define WEBRTC_MODULES_AUDIO_PROCESSING_AGC_MAIN_SOURCE_DIGITAL_AGC_H_

#ifdef AGC_DEBUG
#include <stdio.h>
#endif
#include "webrtc/common_audio/signal_processing/include/signal_processing_library.h"
#include "webrtc/typedefs.h"

// the 32 most significant bits of A(19) * B(26) >> 13
#define AGC_MUL32(A, B)             (((B)>>13)*(A) + ( ((0x00001FFF & (B))*(A)) >> 13 ))
// C + the 32 most significant bits of A * B
#define AGC_SCALEDIFF32(A, B, C)    ((C) + ((B)>>16)*(A) + ( ((0x0000FFFF & (B))*(A)) >> 16 ))

typedef struct
{
    int32_t downState[8];
    int16_t HPstate;
    int16_t counter;
    int16_t logRatio; // log( P(active) / P(inactive) ) (Q10)
    int16_t meanLongTerm; // Q10
    int32_t varianceLongTerm; // Q8
    int16_t stdLongTerm; // Q10
    int16_t meanShortTerm; // Q10
    int32_t varianceShortTerm; // Q8
    int16_t stdShortTerm; // Q10
} AgcVad_t; // total = 54 bytes

typedef struct
{
    int32_t capacitorSlow;
    int32_t capacitorFast;
    int32_t gain;
    int32_t gainTable[32];
    int16_t gatePrevious;
    int16_t agcMode;
    AgcVad_t      vadNearend;
    AgcVad_t      vadFarend;
#ifdef AGC_DEBUG
    FILE*         logFile;
    int           frameCounter;
#endif
} DigitalAgc_t;

int32_t WebRtcAgc_InitDigital(DigitalAgc_t *digitalAgcInst, int16_t agcMode);

int32_t WebRtcAgc_ProcessDigital(DigitalAgc_t *digitalAgcInst,
                                 const int16_t *inNear, const int16_t *inNear_H,
                                 int16_t *out, int16_t *out_H, uint32_t FS,
                                 int16_t lowLevelSignal);

int32_t WebRtcAgc_AddFarendToDigital(DigitalAgc_t *digitalAgcInst,
                                     const int16_t *inFar,
                                     int16_t nrSamples);

void WebRtcAgc_InitVad(AgcVad_t *vadInst);

int16_t WebRtcAgc_ProcessVad(AgcVad_t *vadInst, // (i) VAD state
                             const int16_t *in, // (i) Speech signal
                             int16_t nrSamples); // (i) number of samples

int32_t WebRtcAgc_CalculateGainTable(int32_t *gainTable, // Q16
                                     int16_t compressionGaindB, // Q0 (in dB)
                                     int16_t targetLevelDbfs,// Q0 (in dB)
                                     uint8_t limiterEnable,
                                     int16_t analogTarget);

#endif // WEBRTC_MODULES_AUDIO_PROCESSING_AGC_MAIN_SOURCE_ANALOG_AGC_H_
