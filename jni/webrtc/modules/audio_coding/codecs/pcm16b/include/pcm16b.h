/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_CODECS_PCM16B_MAIN_INTERFACE_PCM16B_H_
#define WEBRTC_MODULES_AUDIO_CODING_CODECS_PCM16B_MAIN_INTERFACE_PCM16B_H_
/*
 * Define the fixpoint numeric formats
 */

#include "typedefs.h"

#ifdef __cplusplus
extern "C" {
#endif

/****************************************************************************
 * WebRtcPcm16b_EncodeW16(...)
 *
 * "Encode" a sample vector to 16 bit linear (Encoded standard is big endian)
 *
 * Input:
 *    - speechIn16b    : Input speech vector
 *    - length_samples : Number of samples in speech vector
 *
 * Output:
 *    - speechOut16b   : Encoded data vector (big endian 16 bit)
 *
 * Returned value      : Size in bytes of speechOut16b
 */

int16_t WebRtcPcm16b_EncodeW16(const int16_t* speechIn16b,
                               int16_t length_samples,
                               int16_t* speechOut16b);

/****************************************************************************
 * WebRtcPcm16b_Encode(...)
 *
 * "Encode" a sample vector to 16 bit linear (Encoded standard is big endian)
 *
 * Input:
 *		- speech16b		: Input speech vector
 *		- len			: Number of samples in speech vector
 *
 * Output:
 *		- speech8b		: Encoded data vector (big endian 16 bit)
 *
 * Returned value		: Size in bytes of speech8b
 */

int16_t WebRtcPcm16b_Encode(int16_t *speech16b,
                            int16_t len,
                            unsigned char *speech8b);

/****************************************************************************
 * WebRtcPcm16b_DecodeW16(...)
 *
 * "Decode" a vector to 16 bit linear (Encoded standard is big endian)
 *
 * Input:
 *    - speechIn16b  : Encoded data vector (big endian 16 bit)
 *    - length_bytes : Number of bytes in speechIn16b
 *
 * Output:
 *    - speechOut16b : Decoded speech vector
 *
 * Returned value    : Samples in speechOut16b
 */

int16_t WebRtcPcm16b_DecodeW16(void *inst,
                               int16_t *speechIn16b,
                               int16_t length_bytes,
                               int16_t *speechOut16b,
                               int16_t* speechType);

/****************************************************************************
 * WebRtcPcm16b_Decode(...)
 *
 * "Decode" a vector to 16 bit linear (Encoded standard is big endian)
 *
 * Input:
 *		- speech8b		: Encoded data vector (big endian 16 bit)
 *		- len			: Number of bytes in speech8b
 *
 * Output:
 *		- speech16b		: Decoded speech vector
 *
 * Returned value		: Samples in speech16b
 */


int16_t WebRtcPcm16b_Decode(unsigned char *speech8b,
                            int16_t len,
                            int16_t *speech16b);

#ifdef __cplusplus
}
#endif

#endif /* PCM16B */
