/*
 *  Copyright 2011 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/base/bandwidthsmoother.h"

#include <limits.h>
#include <algorithm>

namespace rtc {

BandwidthSmoother::BandwidthSmoother(int initial_bandwidth_guess,
                                     uint32_t time_between_increase,
                                     double percent_increase,
                                     size_t samples_count_to_average,
                                     double min_sample_count_percent)
    : time_between_increase_(time_between_increase),
      percent_increase_(std::max(1.0, percent_increase)),
      time_at_last_change_(0),
      bandwidth_estimation_(initial_bandwidth_guess),
      accumulator_(samples_count_to_average),
      min_sample_count_percent_(
          std::min(1.0, std::max(0.0, min_sample_count_percent))) {
}

BandwidthSmoother::~BandwidthSmoother() = default;

// Samples a new bandwidth measurement
// returns true if the bandwidth estimation changed
bool BandwidthSmoother::Sample(uint32_t sample_time, int bandwidth) {
  if (bandwidth < 0) {
    return false;
  }

  accumulator_.AddSample(bandwidth);

  if (accumulator_.count() < static_cast<size_t>(
          accumulator_.max_count() * min_sample_count_percent_)) {
    // We have not collected enough samples yet.
    return false;
  }

  // Replace bandwidth with the mean of sampled bandwidths.
  const int mean_bandwidth = static_cast<int>(accumulator_.ComputeMean());

  if (mean_bandwidth < bandwidth_estimation_) {
    time_at_last_change_ = sample_time;
    bandwidth_estimation_ = mean_bandwidth;
    return true;
  }

  const int old_bandwidth_estimation = bandwidth_estimation_;
  const double increase_threshold_d = percent_increase_ * bandwidth_estimation_;
  if (increase_threshold_d > INT_MAX) {
    // If bandwidth goes any higher we would overflow.
    return false;
  }

  const int increase_threshold = static_cast<int>(increase_threshold_d);
  if (mean_bandwidth < increase_threshold) {
    time_at_last_change_ = sample_time;
    // The value of bandwidth_estimation remains the same if we don't exceed
    // percent_increase_ * bandwidth_estimation_ for at least
    // time_between_increase_ time.
  } else if (sample_time >= time_at_last_change_ + time_between_increase_) {
    time_at_last_change_ = sample_time;
    if (increase_threshold == 0) {
      // Bandwidth_estimation_ must be zero. Assume a jump from zero to a
      // positive bandwidth means we have regained connectivity.
      bandwidth_estimation_ = mean_bandwidth;
    } else {
      bandwidth_estimation_ = increase_threshold;
    }
  }
  // Else don't make a change.

  return old_bandwidth_estimation != bandwidth_estimation_;
}

}  // namespace rtc
