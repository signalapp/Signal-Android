/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

/*
 * crc.h
 *
 * Checksum functions
 *
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_CODECS_ISAC_MAIN_SOURCE_CRC_H_
#define WEBRTC_MODULES_AUDIO_CODING_CODECS_ISAC_MAIN_SOURCE_CRC_H_

#include "typedefs.h"

/****************************************************************************
 * WebRtcIsac_GetCrc(...)
 *
 * This function returns a 32 bit CRC checksum of a bit stream
 *
 * Input:
 *  - encoded      : payload bit stream
 *  - no_of_word8s : number of 8-bit words in the bit stream
 *
 * Output:
 *  - crc          : checksum
 *
 * Return value    :  0 - Ok
 *                   -1 - Error
 */

int16_t WebRtcIsac_GetCrc(
    const int16_t* encoded,
    int16_t        no_of_word8s,
    uint32_t*      crc);



#endif /* WEBRTC_MODULES_AUDIO_CODING_CODECS_ISAC_MAIN_SOURCE_CRC_H_ */
