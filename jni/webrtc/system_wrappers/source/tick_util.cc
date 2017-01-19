/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/system_wrappers/interface/tick_util.h"

#include <assert.h>

namespace webrtc {

bool TickTime::use_fake_clock_ = false;
int64_t TickTime::fake_ticks_ = 0;

void TickTime::UseFakeClock(int64_t start_millisecond) {
  use_fake_clock_ = true;
  fake_ticks_ = MillisecondsToTicks(start_millisecond);
}

void TickTime::AdvanceFakeClock(int64_t milliseconds) {
  assert(use_fake_clock_);
  fake_ticks_ += MillisecondsToTicks(milliseconds);
}

int64_t TickTime::QueryOsForTicks() {
  TickTime result;
#if _WIN32
  // TODO(wu): Remove QueryPerformanceCounter implementation.
#ifdef USE_QUERY_PERFORMANCE_COUNTER
  // QueryPerformanceCounter returns the value from the TSC which is
  // incremented at the CPU frequency. The algorithm used requires
  // the CPU frequency to be constant. Technology like speed stepping
  // which has variable CPU frequency will therefore yield unpredictable,
  // incorrect time estimations.
  LARGE_INTEGER qpcnt;
  QueryPerformanceCounter(&qpcnt);
  result.ticks_ = qpcnt.QuadPart;
#else
  static volatile LONG last_time_get_time = 0;
  static volatile int64_t num_wrap_time_get_time = 0;
  volatile LONG* last_time_get_time_ptr = &last_time_get_time;
  DWORD now = timeGetTime();
  // Atomically update the last gotten time
  DWORD old = InterlockedExchange(last_time_get_time_ptr, now);
  if (now < old) {
    // If now is earlier than old, there may have been a race between
    // threads.
    // 0x0fffffff ~3.1 days, the code will not take that long to execute
    // so it must have been a wrap around.
    if (old > 0xf0000000 && now < 0x0fffffff) {
      num_wrap_time_get_time++;
    }
  }
  result.ticks_ = now + (num_wrap_time_get_time << 32);
#endif
#elif defined(WEBRTC_LINUX)
  struct timespec ts;
  // TODO(wu): Remove CLOCK_REALTIME implementation.
#ifdef WEBRTC_CLOCK_TYPE_REALTIME
  clock_gettime(CLOCK_REALTIME, &ts);
#else
  clock_gettime(CLOCK_MONOTONIC, &ts);
#endif
  result.ticks_ = 1000000000LL * static_cast<int64_t>(ts.tv_sec) +
      static_cast<int64_t>(ts.tv_nsec);
#elif defined(WEBRTC_MAC)
  static mach_timebase_info_data_t timebase;
  if (timebase.denom == 0) {
    // Get the timebase if this is the first time we run.
    // Recommended by Apple's QA1398.
    kern_return_t retval = mach_timebase_info(&timebase);
    if (retval != KERN_SUCCESS) {
      // TODO(wu): Implement CHECK similar to chrome for all the platforms.
      // Then replace this with a CHECK(retval == KERN_SUCCESS);
#ifndef WEBRTC_IOS
      asm("int3");
#else
      __builtin_trap();
#endif  // WEBRTC_IOS
    }
  }
  // Use timebase to convert absolute time tick units into nanoseconds.
  result.ticks_ = mach_absolute_time() * timebase.numer / timebase.denom;
#else
  struct timeval tv;
  gettimeofday(&tv, NULL);
  result.ticks_ = 1000000LL * static_cast<int64_t>(tv.tv_sec) +
      static_cast<int64_t>(tv.tv_usec);
#endif
  return result.ticks_;
}

}  // namespace webrtc
