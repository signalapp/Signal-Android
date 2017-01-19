/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_PROCESSING_AGC_INCLUDE_GAIN_CONTROL_H_
#define WEBRTC_MODULES_AUDIO_PROCESSING_AGC_INCLUDE_GAIN_CONTROL_H_

#include "webrtc/typedefs.h"

// Errors
#define AGC_UNSPECIFIED_ERROR           18000
#define AGC_UNSUPPORTED_FUNCTION_ERROR  18001
#define AGC_UNINITIALIZED_ERROR         18002
#define AGC_NULL_POINTER_ERROR          18003
#define AGC_BAD_PARAMETER_ERROR         18004

// Warnings
#define AGC_BAD_PARAMETER_WARNING       18050

enum
{
    kAgcModeUnchanged,
    kAgcModeAdaptiveAnalog,
    kAgcModeAdaptiveDigital,
    kAgcModeFixedDigital
};

enum
{
    kAgcFalse = 0,
    kAgcTrue
};

typedef struct
{
    int16_t targetLevelDbfs;   // default 3 (-3 dBOv)
    int16_t compressionGaindB; // default 9 dB
    uint8_t limiterEnable;     // default kAgcTrue (on)
} WebRtcAgc_config_t;

#if defined(__cplusplus)
extern "C"
{
#endif

/*
 * This function processes a 10/20ms frame of far-end speech to determine
 * if there is active speech. Far-end speech length can be either 10ms or
 * 20ms. The length of the input speech vector must be given in samples
 * (80/160 when FS=8000, and 160/320 when FS=16000 or FS=32000).
 *
 * Input:
 *      - agcInst           : AGC instance.
 *      - inFar             : Far-end input speech vector (10 or 20ms)
 *      - samples           : Number of samples in input vector
 *
 * Return value:
 *                          :  0 - Normal operation.
 *                          : -1 - Error
 */
int WebRtcAgc_AddFarend(void* agcInst,
                        const int16_t* inFar,
                        int16_t samples);

/*
 * This function processes a 10/20ms frame of microphone speech to determine
 * if there is active speech. Microphone speech length can be either 10ms or
 * 20ms. The length of the input speech vector must be given in samples
 * (80/160 when FS=8000, and 160/320 when FS=16000 or FS=32000). For very low
 * input levels, the input signal is increased in level by multiplying and
 * overwriting the samples in inMic[].
 *
 * This function should be called before any further processing of the
 * near-end microphone signal.
 *
 * Input:
 *      - agcInst           : AGC instance.
 *      - inMic             : Microphone input speech vector (10 or 20 ms) for
 *                            L band
 *      - inMic_H           : Microphone input speech vector (10 or 20 ms) for
 *                            H band
 *      - samples           : Number of samples in input vector
 *
 * Return value:
 *                          :  0 - Normal operation.
 *                          : -1 - Error
 */
int WebRtcAgc_AddMic(void* agcInst,
                     int16_t* inMic,
                     int16_t* inMic_H,
                     int16_t samples);

/*
 * This function replaces the analog microphone with a virtual one.
 * It is a digital gain applied to the input signal and is used in the
 * agcAdaptiveDigital mode where no microphone level is adjustable.
 * Microphone speech length can be either 10ms or 20ms. The length of the
 * input speech vector must be given in samples (80/160 when FS=8000, and
 * 160/320 when FS=16000 or FS=32000).
 *
 * Input:
 *      - agcInst           : AGC instance.
 *      - inMic             : Microphone input speech vector for (10 or 20 ms)
 *                            L band
 *      - inMic_H           : Microphone input speech vector for (10 or 20 ms)
 *                            H band
 *      - samples           : Number of samples in input vector
 *      - micLevelIn        : Input level of microphone (static)
 *
 * Output:
 *      - inMic             : Microphone output after processing (L band)
 *      - inMic_H           : Microphone output after processing (H band)
 *      - micLevelOut       : Adjusted microphone level after processing
 *
 * Return value:
 *                          :  0 - Normal operation.
 *                          : -1 - Error
 */
int WebRtcAgc_VirtualMic(void* agcInst,
                         int16_t* inMic,
                         int16_t* inMic_H,
                         int16_t samples,
                         int32_t micLevelIn,
                         int32_t* micLevelOut);

/*
 * This function processes a 10/20ms frame and adjusts (normalizes) the gain
 * both analog and digitally. The gain adjustments are done only during
 * active periods of speech. The input speech length can be either 10ms or
 * 20ms and the output is of the same length. The length of the speech
 * vectors must be given in samples (80/160 when FS=8000, and 160/320 when
 * FS=16000 or FS=32000). The echo parameter can be used to ensure the AGC will
 * not adjust upward in the presence of echo.
 *
 * This function should be called after processing the near-end microphone
 * signal, in any case after any echo cancellation.
 *
 * Input:
 *      - agcInst           : AGC instance
 *      - inNear            : Near-end input speech vector (10 or 20 ms) for
 *                            L band
 *      - inNear_H          : Near-end input speech vector (10 or 20 ms) for
 *                            H band
 *      - samples           : Number of samples in input/output vector
 *      - inMicLevel        : Current microphone volume level
 *      - echo              : Set to 0 if the signal passed to add_mic is
 *                            almost certainly free of echo; otherwise set
 *                            to 1. If you have no information regarding echo
 *                            set to 0.
 *
 * Output:
 *      - outMicLevel       : Adjusted microphone volume level
 *      - out               : Gain-adjusted near-end speech vector (L band)
 *                          : May be the same vector as the input.
 *      - out_H             : Gain-adjusted near-end speech vector (H band)
 *      - saturationWarning : A returned value of 1 indicates a saturation event
 *                            has occurred and the volume cannot be further
 *                            reduced. Otherwise will be set to 0.
 *
 * Return value:
 *                          :  0 - Normal operation.
 *                          : -1 - Error
 */
int WebRtcAgc_Process(void* agcInst,
                      const int16_t* inNear,
                      const int16_t* inNear_H,
                      int16_t samples,
                      int16_t* out,
                      int16_t* out_H,
                      int32_t inMicLevel,
                      int32_t* outMicLevel,
                      int16_t echo,
                      uint8_t* saturationWarning);

/*
 * This function sets the config parameters (targetLevelDbfs,
 * compressionGaindB and limiterEnable).
 *
 * Input:
 *      - agcInst           : AGC instance
 *      - config            : config struct
 *
 * Output:
 *
 * Return value:
 *                          :  0 - Normal operation.
 *                          : -1 - Error
 */
int WebRtcAgc_set_config(void* agcInst, WebRtcAgc_config_t config);

/*
 * This function returns the config parameters (targetLevelDbfs,
 * compressionGaindB and limiterEnable).
 *
 * Input:
 *      - agcInst           : AGC instance
 *
 * Output:
 *      - config            : config struct
 *
 * Return value:
 *                          :  0 - Normal operation.
 *                          : -1 - Error
 */
int WebRtcAgc_get_config(void* agcInst, WebRtcAgc_config_t* config);

/*
 * This function creates an AGC instance, which will contain the state
 * information for one (duplex) channel.
 *
 * Return value             : AGC instance if successful
 *                          : 0 (i.e., a NULL pointer) if unsuccessful
 */
int WebRtcAgc_Create(void **agcInst);

/*
 * This function frees the AGC instance created at the beginning.
 *
 * Input:
 *      - agcInst           : AGC instance.
 *
 * Return value             :  0 - Ok
 *                            -1 - Error
 */
int WebRtcAgc_Free(void *agcInst);

/*
 * This function initializes an AGC instance.
 *
 * Input:
 *      - agcInst           : AGC instance.
 *      - minLevel          : Minimum possible mic level
 *      - maxLevel          : Maximum possible mic level
 *      - agcMode           : 0 - Unchanged
 *                          : 1 - Adaptive Analog Automatic Gain Control -3dBOv
 *                          : 2 - Adaptive Digital Automatic Gain Control -3dBOv
 *                          : 3 - Fixed Digital Gain 0dB
 *      - fs                : Sampling frequency
 *
 * Return value             :  0 - Ok
 *                            -1 - Error
 */
int WebRtcAgc_Init(void *agcInst,
                   int32_t minLevel,
                   int32_t maxLevel,
                   int16_t agcMode,
                   uint32_t fs);

#if defined(__cplusplus)
}
#endif

#endif  // WEBRTC_MODULES_AUDIO_PROCESSING_AGC_INCLUDE_GAIN_CONTROL_H_
