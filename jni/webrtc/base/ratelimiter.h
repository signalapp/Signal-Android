/*
 *  Copyright 2012 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_RATELIMITER_H_
#define WEBRTC_BASE_RATELIMITER_H_

#include <stdlib.h>
#include "webrtc/base/basictypes.h"

namespace rtc {

// Limits the rate of use to a certain maximum quantity per period of
// time.  Use, for example, for simple bandwidth throttling.
//
// It's implemented like a diet plan: You have so many calories per
// day.  If you hit the limit, you can't eat any more until the next
// day.
class RateLimiter {
 public:
  // For example, 100kb per second.
  RateLimiter(size_t max, double period)
      : max_per_period_(max),
        period_length_(period),
        used_in_period_(0),
        period_start_(0.0),
        period_end_(period) {
  }
  virtual ~RateLimiter() {}

  // Returns true if if the desired quantity is available in the
  // current period (< (max - used)).  Once the given time passes the
  // end of the period, used is set to zero and more use is available.
  bool CanUse(size_t desired, double time);
  // Increment the quantity used this period.  If past the end of a
  // period, a new period is started.
  void Use(size_t used, double time);

  size_t used_in_period() const {
    return used_in_period_;
  }

  size_t max_per_period() const {
    return max_per_period_;
  }

 private:
  size_t max_per_period_;
  double period_length_;
  size_t used_in_period_;
  double period_start_;
  double period_end_;
};

}  // namespace rtc

#endif  // WEBRTC_BASE_RATELIMITER_H_
