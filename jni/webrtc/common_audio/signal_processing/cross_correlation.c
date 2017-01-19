/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/common_audio/signal_processing/include/signal_processing_library.h"

/* C version of WebRtcSpl_CrossCorrelation() for generic platforms. */
void WebRtcSpl_CrossCorrelationC(int32_t* cross_correlation,
                                 const int16_t* seq1,
                                 const int16_t* seq2,
                                 int16_t dim_seq,
                                 int16_t dim_cross_correlation,
                                 int16_t right_shifts,
                                 int16_t step_seq2) {
  int i = 0, j = 0;

  for (i = 0; i < dim_cross_correlation; i++) {
    *cross_correlation = 0;
    /* Unrolling doesn't seem to improve performance. */
    for (j = 0; j < dim_seq; j++) {
      *cross_correlation += (seq1[j] * seq2[step_seq2 * i + j]) >> right_shifts;
    }
    cross_correlation++;
  }
}
