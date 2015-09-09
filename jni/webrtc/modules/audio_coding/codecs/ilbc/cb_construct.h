/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

/******************************************************************

 iLBC Speech Coder ANSI-C Source Code

 WebRtcIlbcfix_CbConstruct.h

******************************************************************/

#ifndef WEBRTC_MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_CB_CONSTRUCT_H_
#define WEBRTC_MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_CB_CONSTRUCT_H_

#include "defines.h"

/*----------------------------------------------------------------*
 *  Construct decoded vector from codebook and gains.
 *---------------------------------------------------------------*/

void WebRtcIlbcfix_CbConstruct(
    int16_t *decvector,  /* (o) Decoded vector */
    int16_t *index,   /* (i) Codebook indices */
    int16_t *gain_index,  /* (i) Gain quantization indices */
    int16_t *mem,   /* (i) Buffer for codevector construction */
    int16_t lMem,   /* (i) Length of buffer */
    int16_t veclen   /* (i) Length of vector */
                               );


#endif
