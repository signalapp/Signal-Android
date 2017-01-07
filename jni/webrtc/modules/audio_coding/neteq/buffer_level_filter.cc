/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/neteq/buffer_level_filter.h"

#include <algorithm>  // Provide access to std::max.

namespace webrtc {

BufferLevelFilter::BufferLevelFilter() {
  Reset();
}

void BufferLevelFilter::Reset() {
  filtered_current_level_ = 0;
  level_factor_ = 253;
}

void BufferLevelFilter::Update(size_t buffer_size_packets,
                               int time_stretched_samples,
                               size_t packet_len_samples) {
  // Filter:
  // |filtered_current_level_| = |level_factor_| * |filtered_current_level_| +
  //                            (1 - |level_factor_|) * |buffer_size_packets|
  // |level_factor_| and |filtered_current_level_| are in Q8.
  // |buffer_size_packets| is in Q0.
  filtered_current_level_ = ((level_factor_ * filtered_current_level_) >> 8) +
      ((256 - level_factor_) * static_cast<int>(buffer_size_packets));

  // Account for time-scale operations (accelerate and pre-emptive expand).
  if (time_stretched_samples && packet_len_samples > 0) {
    // Time-scaling has been performed since last filter update. Subtract the
    // value of |time_stretched_samples| from |filtered_current_level_| after
    // converting |time_stretched_samples| from samples to packets in Q8.
    // Make sure that the filtered value remains non-negative.
    filtered_current_level_ = std::max(0,
        filtered_current_level_ -
        (time_stretched_samples << 8) / static_cast<int>(packet_len_samples));
  }
}

void BufferLevelFilter::SetTargetBufferLevel(int target_buffer_level) {
  if (target_buffer_level <= 1) {
    level_factor_ = 251;
  } else if (target_buffer_level <= 3) {
    level_factor_ = 252;
  } else if (target_buffer_level <= 7) {
    level_factor_ = 253;
  } else {
    level_factor_ = 254;
  }
}

int BufferLevelFilter::filtered_current_level() const {
  return filtered_current_level_;
}

}  // namespace webrtc
