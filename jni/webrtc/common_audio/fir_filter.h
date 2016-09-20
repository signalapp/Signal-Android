/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_COMMON_AUDIO_FIR_FILTER_H_
#define WEBRTC_COMMON_AUDIO_FIR_FILTER_H_

#include <string.h>

namespace webrtc {

// Finite Impulse Response filter using floating-point arithmetic.
class FIRFilter {
 public:
  // Creates a filter with the given coefficients. All initial state values will
  // be zeros.
  // The length of the chunks fed to the filter should never be greater than
  // |max_input_length|. This is needed because, when vectorizing it is
  // necessary to concatenate the input after the state, and resizing this array
  // dynamically is expensive.
  static FIRFilter* Create(const float* coefficients,
                           size_t coefficients_length,
                           size_t max_input_length);

  virtual ~FIRFilter() {}

  // Filters the |in| data supplied.
  // |out| must be previously allocated and it must be at least of |length|.
  virtual void Filter(const float* in, size_t length, float* out) = 0;
};

}  // namespace webrtc

#endif  // WEBRTC_COMMON_AUDIO_FIR_FILTER_H_
