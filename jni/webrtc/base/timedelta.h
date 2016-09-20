/*
 *  Copyright 2016 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_TIMEDELTA_H_
#define WEBRTC_BASE_TIMEDELTA_H_

#include "webrtc/base/basictypes.h"
#include "webrtc/base/timeutils.h"

// Convenience class to convert between different units of relative time.
// Stores time to precision of nanoseconds, as int64_t internally.
// Doesn't check for overflow/underflow.
//
// Based on TimeDelta in:
// https://code.google.com/p/chromium/codesearch#chromium/src/base/time/time.h
namespace rtc {

class TimeDelta {
 public:
  TimeDelta() : delta_(0) {}

  // Converts units of time to TimeDeltas.
  static constexpr TimeDelta FromSeconds(int64_t secs) {
    return TimeDelta(secs * kNumNanosecsPerSec);
  }
  static constexpr TimeDelta FromMilliseconds(int64_t ms) {
    return TimeDelta(ms * kNumNanosecsPerMillisec);
  }
  static constexpr TimeDelta FromMicroseconds(int64_t us) {
    return TimeDelta(us * kNumNanosecsPerMicrosec);
  }
  static constexpr TimeDelta FromNanoseconds(int64_t ns) {
    return TimeDelta(ns);
  }

  // Returns true if the time delta is zero.
  bool is_zero() const { return delta_ == 0; }

  // Converts TimeDelta to units of time.
  int64_t ToSeconds() const { return delta_ / kNumNanosecsPerSec; }
  int64_t ToMilliseconds() const { return delta_ / kNumNanosecsPerMillisec; }
  int64_t ToMicroseconds() const { return delta_ / kNumNanosecsPerMicrosec; }
  int64_t ToNanoseconds() const { return delta_; }

  TimeDelta& operator=(TimeDelta other) {
    delta_ = other.delta_;
    return *this;
  }

  // Computations with other deltas.
  TimeDelta operator+(TimeDelta other) const {
    return TimeDelta(delta_ + other.delta_);
  }
  TimeDelta operator-(TimeDelta other) const {
    return TimeDelta(delta_ + other.delta_);
  }

  TimeDelta& operator+=(TimeDelta other) { return *this = (*this + other); }
  TimeDelta& operator-=(TimeDelta other) { return *this = (*this - other); }
  TimeDelta operator-() const { return TimeDelta(-delta_); }

  // Computations with numeric types.
  template <typename T>
  TimeDelta operator*(T a) const {
    return TimeDelta(delta_ * a);
  }
  template <typename T>
  TimeDelta operator/(T a) const {
    return TimeDelta(delta_ / a);
  }
  template <typename T>
  TimeDelta& operator*=(T a) {
    return *this = (*this * a);
  }
  template <typename T>
  TimeDelta& operator/=(T a) {
    return *this = (*this / a);
  }

  TimeDelta operator%(TimeDelta a) const {
    return TimeDelta(delta_ % a.delta_);
  }

  // Comparison operators.
  constexpr bool operator==(TimeDelta other) const {
    return delta_ == other.delta_;
  }
  constexpr bool operator!=(TimeDelta other) const {
    return delta_ != other.delta_;
  }
  constexpr bool operator<(TimeDelta other) const {
    return delta_ < other.delta_;
  }
  constexpr bool operator<=(TimeDelta other) const {
    return delta_ <= other.delta_;
  }
  constexpr bool operator>(TimeDelta other) const {
    return delta_ > other.delta_;
  }
  constexpr bool operator>=(TimeDelta other) const {
    return delta_ >= other.delta_;
  }

 private:
  // Constructs a delta given the duration in nanoseconds. This is private
  // to avoid confusion by callers with an integer constructor. Use
  // FromSeconds, FromMilliseconds, etc. instead.
  constexpr explicit TimeDelta(int64_t delta_ns) : delta_(delta_ns) {}

  // Delta in nanoseconds.
  int64_t delta_;
};

template <typename T>
inline TimeDelta operator*(T a, TimeDelta td) {
  return td * a;
}

}  // namespace rtc

#endif  // WEBRTC_BASE_TIMEDELTA_H_
