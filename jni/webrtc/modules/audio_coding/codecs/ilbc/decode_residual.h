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

 WebRtcIlbcfix_DecodeResidual.h

******************************************************************/

#ifndef WEBRTC_MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_DECODE_RESIDUAL_H_
#define WEBRTC_MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_DECODE_RESIDUAL_H_

#include "defines.h"

/*----------------------------------------------------------------*
 *  frame residual decoder function (subrutine to iLBC_decode)
 *---------------------------------------------------------------*/

void WebRtcIlbcfix_DecodeResidual(
    iLBC_Dec_Inst_t *iLBCdec_inst,
    /* (i/o) the decoder state structure */
    iLBC_bits *iLBC_encbits, /* (i/o) Encoded bits, which are used
                                   for the decoding  */
    int16_t *decresidual,  /* (o) decoded residual frame */
    int16_t *syntdenum   /* (i) the decoded synthesis filter
                                                   coefficients */
                                  );

#endif
