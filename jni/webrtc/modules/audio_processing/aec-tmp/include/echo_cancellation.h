/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_PROCESSING_AEC_INCLUDE_ECHO_CANCELLATION_H_
#define WEBRTC_MODULES_AUDIO_PROCESSING_AEC_INCLUDE_ECHO_CANCELLATION_H_

#include "webrtc/typedefs.h"

// Errors
#define AEC_UNSPECIFIED_ERROR 12000
#define AEC_UNSUPPORTED_FUNCTION_ERROR 12001
#define AEC_UNINITIALIZED_ERROR 12002
#define AEC_NULL_POINTER_ERROR 12003
#define AEC_BAD_PARAMETER_ERROR 12004

// Warnings
#define AEC_BAD_PARAMETER_WARNING 12050

enum {
  kAecNlpConservative = 0,
  kAecNlpModerate,
  kAecNlpAggressive
};

enum {
  kAecFalse = 0,
  kAecTrue
};

typedef struct {
  int16_t nlpMode;      // default kAecNlpModerate
  int16_t skewMode;     // default kAecFalse
  int16_t metricsMode;  // default kAecFalse
  int delay_logging;    // default kAecFalse
  // float realSkew;
} AecConfig;

typedef struct {
  int instant;
  int average;
  int max;
  int min;
} AecLevel;

typedef struct {
  AecLevel rerl;
  AecLevel erl;
  AecLevel erle;
  AecLevel aNlp;
} AecMetrics;

struct AecCore;

#ifdef __cplusplus
extern "C" {
#endif

/*
 * Allocates the memory needed by the AEC. The memory needs to be initialized
 * separately using the WebRtcAec_Init() function.
 *
 * Inputs                       Description
 * -------------------------------------------------------------------
 * void**  aecInst              Pointer to the AEC instance to be created
 *                              and initialized
 *
 * Outputs                      Description
 * -------------------------------------------------------------------
 * int32_t return               0: OK
 *                             -1: error
 */
int32_t WebRtcAec_Create(void** aecInst);

/*
 * This function releases the memory allocated by WebRtcAec_Create().
 *
 * Inputs                       Description
 * -------------------------------------------------------------------
 * void*        aecInst         Pointer to the AEC instance
 *
 * Outputs                      Description
 * -------------------------------------------------------------------
 * int32_t      return          0: OK
 *                             -1: error
 */
int32_t WebRtcAec_Free(void* aecInst);

/*
 * Initializes an AEC instance.
 *
 * Inputs                       Description
 * -------------------------------------------------------------------
 * void*          aecInst       Pointer to the AEC instance
 * int32_t        sampFreq      Sampling frequency of data
 * int32_t        scSampFreq    Soundcard sampling frequency
 *
 * Outputs                      Description
 * -------------------------------------------------------------------
 * int32_t        return        0: OK
 *                             -1: error
 */
int32_t WebRtcAec_Init(void* aecInst, int32_t sampFreq, int32_t scSampFreq);

/*
 * Inserts an 80 or 160 sample block of data into the farend buffer.
 *
 * Inputs                       Description
 * -------------------------------------------------------------------
 * void*          aecInst       Pointer to the AEC instance
 * const float*   farend        In buffer containing one frame of
 *                              farend signal for L band
 * int16_t        nrOfSamples   Number of samples in farend buffer
 *
 * Outputs                      Description
 * -------------------------------------------------------------------
 * int32_t        return        0: OK
 *                             -1: error
 */
int32_t WebRtcAec_BufferFarend(void* aecInst,
                               const float* farend,
                               int16_t nrOfSamples);

/*
 * Runs the echo canceller on an 80 or 160 sample blocks of data.
 *
 * Inputs                       Description
 * -------------------------------------------------------------------
 * void*         aecInst        Pointer to the AEC instance
 * float*        nearend        In buffer containing one frame of
 *                              nearend+echo signal for L band
 * float*        nearendH       In buffer containing one frame of
 *                              nearend+echo signal for H band
 * int16_t       nrOfSamples    Number of samples in nearend buffer
 * int16_t       msInSndCardBuf Delay estimate for sound card and
 *                              system buffers
 * int16_t       skew           Difference between number of samples played
 *                              and recorded at the soundcard (for clock skew
 *                              compensation)
 *
 * Outputs                      Description
 * -------------------------------------------------------------------
 * float*        out            Out buffer, one frame of processed nearend
 *                              for L band
 * float*        outH           Out buffer, one frame of processed nearend
 *                              for H band
 * int32_t       return         0: OK
 *                             -1: error
 */
int32_t WebRtcAec_Process(void* aecInst,
                          const float* nearend,
                          const float* nearendH,
                          float* out,
                          float* outH,
                          int16_t nrOfSamples,
                          int16_t msInSndCardBuf,
                          int32_t skew);

/*
 * This function enables the user to set certain parameters on-the-fly.
 *
 * Inputs                       Description
 * -------------------------------------------------------------------
 * void*          handle        Pointer to the AEC instance
 * AecConfig      config        Config instance that contains all
 *                              properties to be set
 *
 * Outputs                      Description
 * -------------------------------------------------------------------
 * int            return         0: OK
 *                              -1: error
 */
int WebRtcAec_set_config(void* handle, AecConfig config);

/*
 * Gets the current echo status of the nearend signal.
 *
 * Inputs                       Description
 * -------------------------------------------------------------------
 * void*          handle        Pointer to the AEC instance
 *
 * Outputs                      Description
 * -------------------------------------------------------------------
 * int*           status        0: Almost certainly nearend single-talk
 *                              1: Might not be neared single-talk
 * int            return         0: OK
 *                              -1: error
 */
int WebRtcAec_get_echo_status(void* handle, int* status);

/*
 * Gets the current echo metrics for the session.
 *
 * Inputs                       Description
 * -------------------------------------------------------------------
 * void*          handle        Pointer to the AEC instance
 *
 * Outputs                      Description
 * -------------------------------------------------------------------
 * AecMetrics*    metrics       Struct which will be filled out with the
 *                              current echo metrics.
 * int            return         0: OK
 *                              -1: error
 */
int WebRtcAec_GetMetrics(void* handle, AecMetrics* metrics);

/*
 * Gets the current delay metrics for the session.
 *
 * Inputs                       Description
 * -------------------------------------------------------------------
 * void*      handle            Pointer to the AEC instance
 *
 * Outputs                      Description
 * -------------------------------------------------------------------
 * int*       median            Delay median value.
 * int*       std               Delay standard deviation.
 *
 * int        return             0: OK
 *                              -1: error
 */
int WebRtcAec_GetDelayMetrics(void* handle, int* median, int* std);

/*
 * Gets the last error code.
 *
 * Inputs                       Description
 * -------------------------------------------------------------------
 * void*          aecInst       Pointer to the AEC instance
 *
 * Outputs                      Description
 * -------------------------------------------------------------------
 * int32_t        return        11000-11100: error code
 */
int32_t WebRtcAec_get_error_code(void* aecInst);

// Returns a pointer to the low level AEC handle.
//
// Input:
//  - handle                    : Pointer to the AEC instance.
//
// Return value:
//  - AecCore pointer           : NULL for error.
//
struct AecCore* WebRtcAec_aec_core(void* handle);

#ifdef __cplusplus
}
#endif
#endif  // WEBRTC_MODULES_AUDIO_PROCESSING_AEC_INCLUDE_ECHO_CANCELLATION_H_
