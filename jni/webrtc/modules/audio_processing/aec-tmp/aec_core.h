/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

/*
 * Specifies the interface for the AEC core.
 */

#ifndef WEBRTC_MODULES_AUDIO_PROCESSING_AEC_AEC_CORE_H_
#define WEBRTC_MODULES_AUDIO_PROCESSING_AEC_AEC_CORE_H_

#include "webrtc/typedefs.h"

#define FRAME_LEN 80
#define PART_LEN 64               // Length of partition
#define PART_LEN1 (PART_LEN + 1)  // Unique fft coefficients
#define PART_LEN2 (PART_LEN * 2)  // Length of partition * 2

typedef float complex_t[2];
// For performance reasons, some arrays of complex numbers are replaced by twice
// as long arrays of float, all the real parts followed by all the imaginary
// ones (complex_t[SIZE] -> float[2][SIZE]). This allows SIMD optimizations and
// is better than two arrays (one for the real parts and one for the imaginary
// parts) as this other way would require two pointers instead of one and cause
// extra register spilling. This also allows the offsets to be calculated at
// compile time.

// Metrics
enum {
  kOffsetLevel = -100
};

typedef struct Stats {
  float instant;
  float average;
  float min;
  float max;
  float sum;
  float hisum;
  float himean;
  int counter;
  int hicounter;
} Stats;

typedef struct AecCore AecCore;

int WebRtcAec_CreateAec(AecCore** aec);
int WebRtcAec_FreeAec(AecCore* aec);
int WebRtcAec_InitAec(AecCore* aec, int sampFreq);
void WebRtcAec_InitAec_SSE2(void);
#if defined(MIPS_FPU_LE)
void WebRtcAec_InitAec_mips(void);
#endif
#if defined(WEBRTC_DETECT_ARM_NEON) || defined(WEBRTC_ARCH_ARM_NEON)
void WebRtcAec_InitAec_neon(void);
#endif

void WebRtcAec_BufferFarendPartition(AecCore* aec, const float* farend);
void WebRtcAec_ProcessFrame(AecCore* aec,
                            const float* nearend,
                            const float* nearendH,
                            int knownDelay,
                            float* out,
                            float* outH);

// A helper function to call WebRtc_MoveReadPtr() for all far-end buffers.
// Returns the number of elements moved, and adjusts |system_delay| by the
// corresponding amount in ms.
int WebRtcAec_MoveFarReadPtr(AecCore* aec, int elements);

// Calculates the median and standard deviation among the delay estimates
// collected since the last call to this function.
int WebRtcAec_GetDelayMetricsCore(AecCore* self, int* median, int* std);

// Returns the echo state (1: echo, 0: no echo).
int WebRtcAec_echo_state(AecCore* self);

// Gets statistics of the echo metrics ERL, ERLE, A_NLP.
void WebRtcAec_GetEchoStats(AecCore* self,
                            Stats* erl,
                            Stats* erle,
                            Stats* a_nlp);
#ifdef WEBRTC_AEC_DEBUG_DUMP
void* WebRtcAec_far_time_buf(AecCore* self);
#endif

// Sets local configuration modes.
void WebRtcAec_SetConfigCore(AecCore* self,
                             int nlp_mode,
                             int metrics_mode,
                             int delay_logging);

// Non-zero enables, zero disables.
void WebRtcAec_enable_reported_delay(AecCore* self, int enable);

// Returns non-zero if reported delay is enabled and zero if disabled.
int WebRtcAec_reported_delay_enabled(AecCore* self);

// We now interpret delay correction to mean an extended filter length feature.
// We reuse the delay correction infrastructure to avoid changes through to
// libjingle. See details along with |DelayCorrection| in
// echo_cancellation_impl.h. Non-zero enables, zero disables.
void WebRtcAec_enable_delay_correction(AecCore* self, int enable);

// Returns non-zero if delay correction is enabled and zero if disabled.
int WebRtcAec_delay_correction_enabled(AecCore* self);

// Returns the current |system_delay|, i.e., the buffered difference between
// far-end and near-end.
int WebRtcAec_system_delay(AecCore* self);

// Sets the |system_delay| to |value|.  Note that if the value is changed
// improperly, there can be a performance regression.  So it should be used with
// care.
void WebRtcAec_SetSystemDelay(AecCore* self, int delay);

#endif  // WEBRTC_MODULES_AUDIO_PROCESSING_AEC_AEC_CORE_H_
