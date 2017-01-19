/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */



#include <stdlib.h>
#include <string.h>
#include "g722_interface.h"
#include "g722_enc_dec.h"
#include "typedefs.h"


int16_t WebRtcG722_CreateEncoder(G722EncInst **G722enc_inst)
{
    *G722enc_inst=(G722EncInst*)malloc(sizeof(g722_encode_state_t));
    if (*G722enc_inst!=NULL) {
      return(0);
    } else {
      return(-1);
    }
}

int16_t WebRtcG722_EncoderInit(G722EncInst *G722enc_inst)
{
    // Create and/or reset the G.722 encoder
    // Bitrate 64 kbps and wideband mode (2)
    G722enc_inst = (G722EncInst *) WebRtc_g722_encode_init(
        (g722_encode_state_t*) G722enc_inst, 64000, 2);
    if (G722enc_inst == NULL) {
        return -1;
    } else {
        return 0;
    }
}

int16_t WebRtcG722_FreeEncoder(G722EncInst *G722enc_inst)
{
    // Free encoder memory
    return WebRtc_g722_encode_release((g722_encode_state_t*) G722enc_inst);
}

int16_t WebRtcG722_Encode(G722EncInst *G722enc_inst,
                          int16_t *speechIn,
                          int16_t len,
                          int16_t *encoded)
{
    unsigned char *codechar = (unsigned char*) encoded;
    // Encode the input speech vector
    return WebRtc_g722_encode((g722_encode_state_t*) G722enc_inst,
                       codechar, speechIn, len);
}

int16_t WebRtcG722_CreateDecoder(G722DecInst **G722dec_inst)
{
    *G722dec_inst=(G722DecInst*)malloc(sizeof(g722_decode_state_t));
    if (*G722dec_inst!=NULL) {
      return(0);
    } else {
      return(-1);
    }
}

int16_t WebRtcG722_DecoderInit(G722DecInst *G722dec_inst)
{
    // Create and/or reset the G.722 decoder
    // Bitrate 64 kbps and wideband mode (2)
    G722dec_inst = (G722DecInst *) WebRtc_g722_decode_init(
        (g722_decode_state_t*) G722dec_inst, 64000, 2);
    if (G722dec_inst == NULL) {
        return -1;
    } else {
        return 0;
    }
}

int16_t WebRtcG722_FreeDecoder(G722DecInst *G722dec_inst)
{
    // Free encoder memory
    return WebRtc_g722_decode_release((g722_decode_state_t*) G722dec_inst);
}

int16_t WebRtcG722_Decode(G722DecInst *G722dec_inst,
                          int16_t *encoded,
                          int16_t len,
                          int16_t *decoded,
                          int16_t *speechType)
{
    // Decode the G.722 encoder stream
    *speechType=G722_WEBRTC_SPEECH;
    return WebRtc_g722_decode((g722_decode_state_t*) G722dec_inst,
                              decoded, (uint8_t*) encoded, len);
}

int16_t WebRtcG722_Version(char *versionStr, short len)
{
    // Get version string
    char version[30] = "2.0.0\n";
    if (strlen(version) < (unsigned int)len)
    {
        strcpy(versionStr, version);
        return 0;
    }
    else
    {
        return -1;
    }
}

