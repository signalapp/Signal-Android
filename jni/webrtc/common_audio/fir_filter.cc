/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/common_audio/fir_filter.h"

#include <assert.h>
#include <string.h>

#include <memory>

#include "webrtc/common_audio/fir_filter_neon.h"
#include "webrtc/common_audio/fir_filter_sse.h"
#include "webrtc/system_wrappers/include/cpu_features_wrapper.h"

namespace webrtc {

class FIRFilterC : public FIRFilter {
 public:
  FIRFilterC(const float* coefficients,
             size_t coefficients_length);

  void Filter(const float* in, size_t length, float* out) override;

 private:
  size_t coefficients_length_;
  size_t state_length_;
  std::unique_ptr<float[]> coefficients_;
  std::unique_ptr<float[]> state_;
};

FIRFilter* FIRFilter::Create(const float* coefficients,
                             size_t coefficients_length,
                             size_t max_input_length) {
  if (!coefficients || coefficients_length <= 0 || max_input_length <= 0) {
    assert(false);
    return NULL;
  }

  FIRFilter* filter = NULL;
// If we know the minimum architecture at compile time, avoid CPU detection.
#if defined(WEBRTC_ARCH_X86_FAMILY)
#if defined(__SSE2__)
  filter =
      new FIRFilterSSE2(coefficients, coefficients_length, max_input_length);
#else
  // x86 CPU detection required.
  if (WebRtc_GetCPUInfo(kSSE2)) {
    filter =
        new FIRFilterSSE2(coefficients, coefficients_length, max_input_length);
  } else {
    filter = new FIRFilterC(coefficients, coefficients_length);
  }
#endif
#elif defined(WEBRTC_HAS_NEON)
  filter =
      new FIRFilterNEON(coefficients, coefficients_length, max_input_length);
#else
  filter = new FIRFilterC(coefficients, coefficients_length);
#endif

  return filter;
}

FIRFilterC::FIRFilterC(const float* coefficients, size_t coefficients_length)
    : coefficients_length_(coefficients_length),
      state_length_(coefficients_length - 1),
      coefficients_(new float[coefficients_length_]),
      state_(new float[state_length_]) {
  for (size_t i = 0; i < coefficients_length_; ++i) {
    coefficients_[i] = coefficients[coefficients_length_ - i - 1];
  }
  memset(state_.get(), 0, state_length_ * sizeof(state_[0]));
}

void FIRFilterC::Filter(const float* in, size_t length, float* out) {
  assert(length > 0);

  // Convolves the input signal |in| with the filter kernel |coefficients_|
  // taking into account the previous state.
  for (size_t i = 0; i < length; ++i) {
    out[i] = 0.f;
    size_t j;
    for (j = 0; state_length_ > i && j < state_length_ - i; ++j) {
      out[i] += state_[i + j] * coefficients_[j];
    }
    for (; j < coefficients_length_; ++j) {
      out[i] += in[j + i - state_length_] * coefficients_[j];
    }
  }

  // Update current state.
  if (length >= state_length_) {
    memcpy(
        state_.get(), &in[length - state_length_], state_length_ * sizeof(*in));
  } else {
    memmove(state_.get(),
            &state_[length],
            (state_length_ - length) * sizeof(state_[0]));
    memcpy(&state_[state_length_ - length], in, length * sizeof(*in));
  }
}

}  // namespace webrtc
