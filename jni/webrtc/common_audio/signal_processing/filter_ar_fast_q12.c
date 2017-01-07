/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include <assert.h>

#include "webrtc/common_audio/signal_processing/include/signal_processing_library.h"

// TODO(bjornv): Change the return type to report errors.

void WebRtcSpl_FilterARFastQ12(const int16_t* data_in,
                               int16_t* data_out,
                               const int16_t* __restrict coefficients,
                               size_t coefficients_length,
                               size_t data_length) {
  size_t i = 0;
  size_t j = 0;

  assert(data_length > 0);
  assert(coefficients_length > 1);

  for (i = 0; i < data_length; i++) {
    int32_t output = 0;
    int32_t sum = 0;

    for (j = coefficients_length - 1; j > 0; j--) {
      sum += coefficients[j] * data_out[i - j];
    }

    output = coefficients[0] * data_in[i];
    output -= sum;

    // Saturate and store the output.
    output = WEBRTC_SPL_SAT(134215679, output, -134217728);
    data_out[i] = (int16_t)((output + 2048) >> 12);
  }
}
