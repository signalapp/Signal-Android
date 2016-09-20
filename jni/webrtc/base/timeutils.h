/*
 *  Copyright 2005 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_TIMEUTILS_H_
#define WEBRTC_BASE_TIMEUTILS_H_

#include <ctime>
#include <time.h>

#include "webrtc/base/basictypes.h"

namespace rtc {

static const int64_t kNumMillisecsPerSec = INT64_C(1000);
static const int64_t kNumMicrosecsPerSec = INT64_C(1000000);
static const int64_t kNumNanosecsPerSec = INT64_C(1000000000);

static const int64_t kNumMicrosecsPerMillisec =
    kNumMicrosecsPerSec / kNumMillisecsPerSec;
static const int64_t kNumNanosecsPerMillisec =
    kNumNanosecsPerSec / kNumMillisecsPerSec;
static const int64_t kNumNanosecsPerMicrosec =
    kNumNanosecsPerSec / kNumMicrosecsPerSec;

// TODO(honghaiz): Define a type for the time value specifically.

class ClockInterface {
 public:
  virtual ~ClockInterface() {}
  virtual uint64_t TimeNanos() const = 0;
};

// Sets the global source of time. This is useful mainly for unit tests.
//
// Returns the previously set ClockInterface, or nullptr if none is set.
//
// Does not transfer ownership of the clock. SetClockForTesting(nullptr)
// should be called before the ClockInterface is deleted.
//
// This method is not thread-safe; it should only be used when no other thread
// is running (for example, at the start/end of a unit test, or start/end of
// main()).
//
// TODO(deadbeef): Instead of having functions that access this global
// ClockInterface, we may want to pass the ClockInterface into everything
// that uses it, eliminating the need for a global variable and this function.
ClockInterface* SetClockForTesting(ClockInterface* clock);

// Returns the actual system time, even if a clock is set for testing.
// Useful for timeouts while using a test clock, or for logging.
uint64_t SystemTimeNanos();
int64_t SystemTimeMillis();

// Returns the current time in milliseconds in 32 bits.
uint32_t Time32();

// Returns the current time in milliseconds in 64 bits.
int64_t TimeMillis();
// Deprecated. Do not use this in any new code.
inline int64_t Time() {
  return TimeMillis();
}

// Returns the current time in microseconds.
uint64_t TimeMicros();

// Returns the current time in nanoseconds.
uint64_t TimeNanos();

// Returns a future timestamp, 'elapsed' milliseconds from now.
int64_t TimeAfter(int64_t elapsed);

// Number of milliseconds that would elapse between 'earlier' and 'later'
// timestamps.  The value is negative if 'later' occurs before 'earlier'.
int64_t TimeDiff(int64_t later, int64_t earlier);
int32_t TimeDiff32(uint32_t later, uint32_t earlier);

// The number of milliseconds that have elapsed since 'earlier'.
inline int64_t TimeSince(int64_t earlier) {
  return TimeMillis() - earlier;
}

// The number of milliseconds that will elapse between now and 'later'.
inline int64_t TimeUntil(uint64_t later) {
  return later - TimeMillis();
}

class TimestampWrapAroundHandler {
 public:
  TimestampWrapAroundHandler();

  int64_t Unwrap(uint32_t ts);

 private:
  uint32_t last_ts_;
  int64_t num_wrap_;
};

// Convert from std::tm, which is relative to 1900-01-01 00:00 to number of
// seconds from 1970-01-01 00:00 ("epoch").  Don't return time_t since that
// is still 32 bits on many systems.
int64_t TmToSeconds(const std::tm& tm);

}  // namespace rtc

#endif  // WEBRTC_BASE_TIMEUTILS_H_
