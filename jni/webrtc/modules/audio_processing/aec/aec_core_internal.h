/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_PROCESSING_AEC_AEC_CORE_INTERNAL_H_
#define WEBRTC_MODULES_AUDIO_PROCESSING_AEC_AEC_CORE_INTERNAL_H_

#ifdef WEBRTC_AEC_DEBUG_DUMP
#include <stdio.h>
#endif

#include "webrtc/modules/audio_processing/aec/aec_core.h"
#include "webrtc/modules/audio_processing/utility/ring_buffer.h"
#include "webrtc/typedefs.h"

// Number of partitions for the extended filter mode. The first one is an enum
// to be used in array declarations, as it represents the maximum filter length.
enum {
  kExtendedNumPartitions = 32
};
static const int kNormalNumPartitions = 12;

// Delay estimator constants, used for logging.
enum {
  kMaxDelayBlocks = 60
};
enum {
  kLookaheadBlocks = 15
};
enum {
  kHistorySizeBlocks = kMaxDelayBlocks + kLookaheadBlocks
};

// Extended filter adaptation parameters.
// TODO(ajm): No narrowband tuning yet.
static const float kExtendedMu = 0.4f;
static const float kExtendedErrorThreshold = 1.0e-6f;

typedef struct PowerLevel {
  float sfrsum;
  int sfrcounter;
  float framelevel;
  float frsum;
  int frcounter;
  float minlevel;
  float averagelevel;
} PowerLevel;

struct AecCore {
  int farBufWritePos, farBufReadPos;

  int knownDelay;
  int inSamples, outSamples;
  int delayEstCtr;

  RingBuffer* nearFrBuf;
  RingBuffer* outFrBuf;

  RingBuffer* nearFrBufH;
  RingBuffer* outFrBufH;

  float dBuf[PART_LEN2];  // nearend
  float eBuf[PART_LEN2];  // error

  float dBufH[PART_LEN2];  // nearend

  float xPow[PART_LEN1];
  float dPow[PART_LEN1];
  float dMinPow[PART_LEN1];
  float dInitMinPow[PART_LEN1];
  float* noisePow;

  float xfBuf[2][kExtendedNumPartitions * PART_LEN1];  // farend fft buffer
  float wfBuf[2][kExtendedNumPartitions * PART_LEN1];  // filter fft
  complex_t sde[PART_LEN1];  // cross-psd of nearend and error
  complex_t sxd[PART_LEN1];  // cross-psd of farend and nearend
  // Farend windowed fft buffer.
  complex_t xfwBuf[kExtendedNumPartitions * PART_LEN1];

  float sx[PART_LEN1], sd[PART_LEN1], se[PART_LEN1];  // far, near, error psd
  float hNs[PART_LEN1];
  float hNlFbMin, hNlFbLocalMin;
  float hNlXdAvgMin;
  int hNlNewMin, hNlMinCtr;
  float overDrive, overDriveSm;
  int nlp_mode;
  float outBuf[PART_LEN];
  int delayIdx;

  short stNearState, echoState;
  short divergeState;

  int xfBufBlockPos;

  RingBuffer* far_buf;
  RingBuffer* far_buf_windowed;
  int system_delay;  // Current system delay buffered in AEC.

  int mult;  // sampling frequency multiple
  int sampFreq;
  uint32_t seed;

  float normal_mu;               // stepsize
  float normal_error_threshold;  // error threshold

  int noiseEstCtr;

  PowerLevel farlevel;
  PowerLevel nearlevel;
  PowerLevel linoutlevel;
  PowerLevel nlpoutlevel;

  int metricsMode;
  int stateCounter;
  Stats erl;
  Stats erle;
  Stats aNlp;
  Stats rerl;

  // Quantities to control H band scaling for SWB input
  int freq_avg_ic;       // initial bin for averaging nlp gain
  int flag_Hband_cn;     // for comfort noise
  float cn_scale_Hband;  // scale for comfort noise in H band

  int delay_histogram[kHistorySizeBlocks];
  int delay_logging_enabled;
  void* delay_estimator_farend;
  void* delay_estimator;

  int reported_delay_enabled;  // 0 = disabled, otherwise enabled.
  // 1 = extended filter mode enabled, 0 = disabled.
  int extended_filter_enabled;
  // Runtime selection of number of filter partitions.
  int num_partitions;

#ifdef WEBRTC_AEC_DEBUG_DUMP
  RingBuffer* far_time_buf;
  FILE* farFile;
  FILE* nearFile;
  FILE* outFile;
  FILE* outLinearFile;
#endif
};

typedef void (*WebRtcAec_FilterFar_t)(AecCore* aec, float yf[2][PART_LEN1]);
extern WebRtcAec_FilterFar_t WebRtcAec_FilterFar;
typedef void (*WebRtcAec_ScaleErrorSignal_t)(AecCore* aec,
                                             float ef[2][PART_LEN1]);
extern WebRtcAec_ScaleErrorSignal_t WebRtcAec_ScaleErrorSignal;
typedef void (*WebRtcAec_FilterAdaptation_t)(AecCore* aec,
                                             float* fft,
                                             float ef[2][PART_LEN1]);
extern WebRtcAec_FilterAdaptation_t WebRtcAec_FilterAdaptation;
typedef void (*WebRtcAec_OverdriveAndSuppress_t)(AecCore* aec,
                                                 float hNl[PART_LEN1],
                                                 const float hNlFb,
                                                 float efw[2][PART_LEN1]);
extern WebRtcAec_OverdriveAndSuppress_t WebRtcAec_OverdriveAndSuppress;

typedef void (*WebRtcAec_ComfortNoise_t)(AecCore* aec,
                                         float efw[2][PART_LEN1],
                                         complex_t* comfortNoiseHband,
                                         const float* noisePow,
                                         const float* lambda);
extern WebRtcAec_ComfortNoise_t WebRtcAec_ComfortNoise;

#endif  // WEBRTC_MODULES_AUDIO_PROCESSING_AEC_AEC_CORE_INTERNAL_H_
