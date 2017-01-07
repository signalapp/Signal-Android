/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

/******************************************************************

 iLBC Speech Coder ANSI-C Source Code

 WebRtcIlbcfix_Decode.h

******************************************************************/

#ifndef WEBRTC_MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_DECODE_H_
#define WEBRTC_MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_DECODE_H_

#include "defines.h"

/*----------------------------------------------------------------*
 *  main decoder function
 *---------------------------------------------------------------*/

void WebRtcIlbcfix_DecodeImpl(
    int16_t *decblock,    /* (o) decoded signal block */
    const uint16_t *bytes, /* (i) encoded signal bits */
    IlbcDecoder *iLBCdec_inst, /* (i/o) the decoder state
                                           structure */
    int16_t mode      /* (i) 0: bad packet, PLC,
                                                                   1: normal */
                           );

#endif
