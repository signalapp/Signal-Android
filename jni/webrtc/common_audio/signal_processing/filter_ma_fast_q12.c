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
 * This file contains the function WebRtcSpl_FilterMAFastQ12().
 * The description header can be found in signal_processing_library.h
 *
 */

#include "webrtc/common_audio/signal_processing/include/signal_processing_library.h"

void WebRtcSpl_FilterMAFastQ12(int16_t* in_ptr,
                               int16_t* out_ptr,
                               int16_t* B,
                               int16_t B_length,
                               int16_t length)
{
    int32_t o;
    int i, j;
    for (i = 0; i < length; i++)
    {
        const int16_t* b_ptr = &B[0];
        const int16_t* x_ptr = &in_ptr[i];

        o = (int32_t)0;

        for (j = 0; j < B_length; j++)
        {
            o += WEBRTC_SPL_MUL_16_16(*b_ptr++, *x_ptr--);
        }

        // If output is higher than 32768, saturate it. Same with negative side
        // 2^27 = 134217728, which corresponds to 32768 in Q12

        // Saturate the output
        o = WEBRTC_SPL_SAT((int32_t)134215679, o, (int32_t)-134217728);

        *out_ptr++ = (int16_t)((o + (int32_t)2048) >> 12);
    }
    return;
}
