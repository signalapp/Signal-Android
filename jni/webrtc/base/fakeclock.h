/*
 *  Copyright 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_FAKECLOCK_H_
#define WEBRTC_BASE_FAKECLOCK_H_

#include "webrtc/base/criticalsection.h"
#include "webrtc/base/timedelta.h"
#include "webrtc/base/timeutils.h"

namespace rtc {

// Fake clock for use with unit tests, which does not tick on its own.
// Starts at time 0.
//
// TODO(deadbeef): Unify with webrtc::SimulatedClock.
class FakeClock : public ClockInterface {
 public:
  ~FakeClock() override {}

  // ClockInterface implementation.
  uint64_t TimeNanos() const override;

  // Methods that can be used by the test to control the time.

  // Should only be used to set a time in the future.
  void SetTimeNanos(uint64_t nanos);

  void AdvanceTime(TimeDelta delta);

 private:
  CriticalSection lock_;
  uint64_t time_ GUARDED_BY(lock_) = 0u;
};

// Helper class that sets itself as the global clock in its constructor and
// unsets it in its destructor.
class ScopedFakeClock : public FakeClock {
 public:
  ScopedFakeClock();
  ~ScopedFakeClock() override;

 private:
  ClockInterface* prev_clock_;
};

}  // namespace rtc

#endif  // WEBRTC_BASE_FAKECLOCK_H_
