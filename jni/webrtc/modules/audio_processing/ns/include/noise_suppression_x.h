/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_PROCESSING_NS_INCLUDE_NOISE_SUPPRESSION_X_H_
#define WEBRTC_MODULES_AUDIO_PROCESSING_NS_INCLUDE_NOISE_SUPPRESSION_X_H_

#include "webrtc/typedefs.h"

typedef struct NsxHandleT NsxHandle;

#ifdef __cplusplus
extern "C" {
#endif

/*
 * This function creates an instance to the noise reduction structure
 *
 * Input:
 *      - nsxInst       : Pointer to noise reduction instance that should be
 *                       created
 *
 * Output:
 *      - nsxInst       : Pointer to created noise reduction instance
 *
 * Return value         :  0 - Ok
 *                        -1 - Error
 */
int WebRtcNsx_Create(NsxHandle** nsxInst);


/*
 * This function frees the dynamic memory of a specified Noise Suppression
 * instance.
 *
 * Input:
 *      - nsxInst       : Pointer to NS instance that should be freed
 *
 * Return value         :  0 - Ok
 *                        -1 - Error
 */
int WebRtcNsx_Free(NsxHandle* nsxInst);


/*
 * This function initializes a NS instance
 *
 * Input:
 *      - nsxInst       : Instance that should be initialized
 *      - fs            : sampling frequency
 *
 * Output:
 *      - nsxInst       : Initialized instance
 *
 * Return value         :  0 - Ok
 *                        -1 - Error
 */
int WebRtcNsx_Init(NsxHandle* nsxInst, uint32_t fs);

/*
 * This changes the aggressiveness of the noise suppression method.
 *
 * Input:
 *      - nsxInst       : Instance that should be initialized
 *      - mode          : 0: Mild, 1: Medium , 2: Aggressive
 *
 * Output:
 *      - nsxInst       : Initialized instance
 *
 * Return value         :  0 - Ok
 *                        -1 - Error
 */
int WebRtcNsx_set_policy(NsxHandle* nsxInst, int mode);

/*
 * This functions does noise suppression for the inserted speech frame. The
 * input and output signals should always be 10ms (80 or 160 samples).
 *
 * Input
 *      - nsxInst       : NSx instance. Needs to be initiated before call.
 *      - speechFrame   : Pointer to speech frame buffer for L band
 *      - speechFrameHB : Pointer to speech frame buffer for H band
 *      - fs            : sampling frequency
 *
 * Output:
 *      - nsxInst       : Updated NSx instance
 *      - outFrame      : Pointer to output frame for L band
 *      - outFrameHB    : Pointer to output frame for H band
 *
 * Return value         :  0 - OK
 *                        -1 - Error
 */
int WebRtcNsx_Process(NsxHandle* nsxInst,
                      short* speechFrame,
                      short* speechFrameHB,
                      short* outFrame,
                      short* outFrameHB);

#ifdef __cplusplus
}
#endif

#endif  // WEBRTC_MODULES_AUDIO_PROCESSING_NS_INCLUDE_NOISE_SUPPRESSION_X_H_
