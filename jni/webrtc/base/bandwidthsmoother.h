/*
 *  Copyright 2011 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_BANDWIDTHSMOOTHER_H_
#define WEBRTC_BASE_BANDWIDTHSMOOTHER_H_

#include "webrtc/base/rollingaccumulator.h"
#include "webrtc/base/timeutils.h"

namespace rtc {

// The purpose of BandwidthSmoother is to smooth out bandwidth
// estimations so that 'trstate' messages can be triggered when we
// are "sure" there is sufficient bandwidth.  To avoid frequent fluctuations,
// we take a slightly pessimistic view of our bandwidth.  We only increase
// our estimation when we have sampled bandwidth measurements of values
// at least as large as the current estimation * percent_increase
// for at least time_between_increase time.  If a sampled bandwidth
// is less than our current estimation we immediately decrease our estimation
// to that sampled value.
// We retain the initial bandwidth guess as our current bandwidth estimation
// until we have received (min_sample_count_percent * samples_count_to_average)
// number of samples. Min_sample_count_percent must be in range [0, 1].
class BandwidthSmoother {
 public:
  BandwidthSmoother(int initial_bandwidth_guess,
                    uint32_t time_between_increase,
                    double percent_increase,
                    size_t samples_count_to_average,
                    double min_sample_count_percent);
  ~BandwidthSmoother();

  // Samples a new bandwidth measurement.
  // bandwidth is expected to be non-negative.
  // returns true if the bandwidth estimation changed
  bool Sample(uint32_t sample_time, int bandwidth);

  int get_bandwidth_estimation() const {
    return bandwidth_estimation_;
  }

 private:
  uint32_t time_between_increase_;
  double percent_increase_;
  uint32_t time_at_last_change_;
  int bandwidth_estimation_;
  RollingAccumulator<int> accumulator_;
  double min_sample_count_percent_;
};

}  // namespace rtc

#endif  // WEBRTC_BASE_BANDWIDTHSMOOTHER_H_
