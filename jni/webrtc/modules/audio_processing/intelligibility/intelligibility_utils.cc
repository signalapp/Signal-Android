/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_processing/intelligibility/intelligibility_utils.h"

#include <math.h>
#include <stdlib.h>
#include <string.h>
#include <algorithm>
#include <limits>

namespace webrtc {

namespace intelligibility {

namespace {

const float kMinFactor = 0.01f;
const float kMaxFactor = 100.f;

// Return |current| changed towards |target|, with the relative change being at
// most |limit|.
float UpdateFactor(float target, float current, float limit) {
  float gain = target / (current + std::numeric_limits<float>::epsilon());
  gain = std::min(std::max(gain, 1.f - limit), 1.f + limit);
  return std::min(std::max(current * gain, kMinFactor), kMaxFactor);;
}

}  // namespace

template<typename T>
PowerEstimator<T>::PowerEstimator(size_t num_freqs, float decay)
    : power_(num_freqs, 0.f), decay_(decay) {}

template<typename T>
void PowerEstimator<T>::Step(const T* data) {
  for (size_t i = 0; i < power_.size(); ++i) {
    power_[i] = decay_ * power_[i] +
                (1.f - decay_) * std::abs(data[i]) * std::abs(data[i]);
  }
}

template class PowerEstimator<float>;
template class PowerEstimator<std::complex<float>>;

GainApplier::GainApplier(size_t freqs, float relative_change_limit)
    : num_freqs_(freqs),
      relative_change_limit_(relative_change_limit),
      target_(freqs, 1.f),
      current_(freqs, 1.f) {}

void GainApplier::Apply(const std::complex<float>* in_block,
                        std::complex<float>* out_block) {
  for (size_t i = 0; i < num_freqs_; ++i) {
    current_[i] = UpdateFactor(target_[i], current_[i], relative_change_limit_);
    out_block[i] = sqrtf(fabsf(current_[i])) * in_block[i];
  }
}

}  // namespace intelligibility

}  // namespace webrtc
