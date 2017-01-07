/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_processing/rms_level.h"

#include <assert.h>
#include <math.h>

namespace webrtc {

static const float kMaxSquaredLevel = 32768 * 32768;

RMSLevel::RMSLevel()
    : sum_square_(0),
      sample_count_(0) {}

RMSLevel::~RMSLevel() {}

void RMSLevel::Reset() {
  sum_square_ = 0;
  sample_count_ = 0;
}

void RMSLevel::Process(const int16_t* data, size_t length) {
  for (size_t i = 0; i < length; ++i) {
    sum_square_ += data[i] * data[i];
  }
  sample_count_ += length;
}

void RMSLevel::ProcessMuted(size_t length) {
  sample_count_ += length;
}

int RMSLevel::RMS() {
  if (sample_count_ == 0 || sum_square_ == 0) {
    Reset();
    return kMinLevel;
  }

  // Normalize by the max level.
  float rms = sum_square_ / (sample_count_ * kMaxSquaredLevel);
  // 20log_10(x^0.5) = 10log_10(x)
  rms = 10 * log10(rms);
  assert(rms <= 0);
  if (rms < -kMinLevel)
    rms = -kMinLevel;

  rms = -rms;
  Reset();
  return static_cast<int>(rms + 0.5);
}

}  // namespace webrtc
