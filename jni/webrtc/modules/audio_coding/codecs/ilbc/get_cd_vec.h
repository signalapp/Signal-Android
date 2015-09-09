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

 WebRtcIlbcfix_GetCbVec.h

******************************************************************/

#ifndef WEBRTC_MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_GET_CD_VEC_H_
#define WEBRTC_MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_GET_CD_VEC_H_

void WebRtcIlbcfix_GetCbVec(
    int16_t *cbvec,   /* (o) Constructed codebook vector */
    int16_t *mem,   /* (i) Codebook buffer */
    int16_t index,   /* (i) Codebook index */
    int16_t lMem,   /* (i) Length of codebook buffer */
    int16_t cbveclen   /* (i) Codebook vector length */
                            );

#endif
