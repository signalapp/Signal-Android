/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_RATE_STATISTICS_H_
#define WEBRTC_BASE_RATE_STATISTICS_H_

#include <memory>

#include "webrtc/base/optional.h"
#include "webrtc/typedefs.h"

namespace webrtc {

class RateStatistics {
 public:
  // max_window_size_ms = Maximum window size in ms for the rate estimation.
  //                      Initial window size is set to this, but may be changed
  //                      to something lower by calling SetWindowSize().
  // scale = coefficient to convert counts/ms to desired units,
  //         ex: if counts represents bytes, use 8*1000 to go to bits/s
  RateStatistics(int64_t max_window_size_ms, float scale);
  ~RateStatistics();

  void Reset();
  void Update(size_t count, int64_t now_ms);
  rtc::Optional<uint32_t> Rate(int64_t now_ms);
  bool SetWindowSize(int64_t window_size_ms, int64_t now_ms);

 private:
  void EraseOld(int64_t now_ms);
  bool IsInitialized();

  // Counters are kept in buckets (circular buffer), with one bucket
  // per millisecond.
  struct Bucket {
    size_t sum;      // Sum of all samples in this bucket.
    size_t samples;  // Number of samples in this bucket.
  };
  std::unique_ptr<Bucket[]> buckets_;

  // Total count recorded in buckets.
  size_t accumulated_count_;

  // The total number of samples in the buckets.
  size_t num_samples_;

  // Oldest time recorded in buckets.
  int64_t oldest_time_;

  // Bucket index of oldest counter recorded in buckets.
  uint32_t oldest_index_;

  // To convert counts/ms to desired units
  const float scale_;

  // The window sizes, in ms, over which the rate is calculated.
  const int64_t max_window_size_ms_;
  int64_t current_window_size_ms_;
};
}  // namespace webrtc

#endif  // WEBRTC_BASE_RATE_STATISTICS_H_
