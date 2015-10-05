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
 * This file contains the function WebRtcSpl_Energy().
 * The description header can be found in signal_processing_library.h
 *
 */

#include "webrtc/common_audio/signal_processing/include/signal_processing_library.h"

int32_t WebRtcSpl_Energy(int16_t* vector, int vector_length, int* scale_factor)
{
    int32_t en = 0;
    int i;
    int scaling = WebRtcSpl_GetScalingSquare(vector, vector_length, vector_length);
    int looptimes = vector_length;
    int16_t *vectorptr = vector;

    for (i = 0; i < looptimes; i++)
    {
        en += WEBRTC_SPL_MUL_16_16_RSFT(*vectorptr, *vectorptr, scaling);
        vectorptr++;
    }
    *scale_factor = scaling;

    return en;
}
