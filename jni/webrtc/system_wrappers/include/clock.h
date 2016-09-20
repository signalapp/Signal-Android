/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_SYSTEM_WRAPPERS_INCLUDE_CLOCK_H_
#define WEBRTC_SYSTEM_WRAPPERS_INCLUDE_CLOCK_H_

#include <memory>

#include "webrtc/system_wrappers/include/rw_lock_wrapper.h"
#include "webrtc/typedefs.h"

namespace webrtc {

// January 1970, in NTP seconds.
const uint32_t kNtpJan1970 = 2208988800UL;

// Magic NTP fractional unit.
const double kMagicNtpFractionalUnit = 4.294967296E+9;

// A clock interface that allows reading of absolute and relative timestamps.
class Clock {
 public:
  virtual ~Clock() {}

  // Return a timestamp in milliseconds relative to some arbitrary source; the
  // source is fixed for this clock.
  virtual int64_t TimeInMilliseconds() const = 0;

  // Return a timestamp in microseconds relative to some arbitrary source; the
  // source is fixed for this clock.
  virtual int64_t TimeInMicroseconds() const = 0;

  // Retrieve an NTP absolute timestamp in seconds and fractions of a second.
  virtual void CurrentNtp(uint32_t& seconds, uint32_t& fractions) const = 0;

  // Retrieve an NTP absolute timestamp in milliseconds.
  virtual int64_t CurrentNtpInMilliseconds() const = 0;

  // Converts an NTP timestamp to a millisecond timestamp.
  static int64_t NtpToMs(uint32_t seconds, uint32_t fractions);

  // Returns an instance of the real-time system clock implementation.
  static Clock* GetRealTimeClock();
};

class SimulatedClock : public Clock {
 public:
  explicit SimulatedClock(int64_t initial_time_us);

  ~SimulatedClock() override;

  // Return a timestamp in milliseconds relative to some arbitrary source; the
  // source is fixed for this clock.
  int64_t TimeInMilliseconds() const override;

  // Return a timestamp in microseconds relative to some arbitrary source; the
  // source is fixed for this clock.
  int64_t TimeInMicroseconds() const override;

  // Retrieve an NTP absolute timestamp in milliseconds.
  void CurrentNtp(uint32_t& seconds, uint32_t& fractions) const override;

  // Converts an NTP timestamp to a millisecond timestamp.
  int64_t CurrentNtpInMilliseconds() const override;

  // Advance the simulated clock with a given number of milliseconds or
  // microseconds.
  void AdvanceTimeMilliseconds(int64_t milliseconds);
  void AdvanceTimeMicroseconds(int64_t microseconds);

 private:
  int64_t time_us_;
  std::unique_ptr<RWLockWrapper> lock_;
};

};  // namespace webrtc

#endif  // WEBRTC_SYSTEM_WRAPPERS_INCLUDE_CLOCK_H_
