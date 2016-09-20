/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/system_wrappers/include/clock.h"

#if defined(_WIN32)
// Windows needs to be included before mmsystem.h
#include "webrtc/base/win32.h"
#include <MMSystem.h>
#elif ((defined WEBRTC_LINUX) || (defined WEBRTC_MAC))
#include <sys/time.h>
#include <time.h>
#endif

#include "webrtc/base/criticalsection.h"
#include "webrtc/base/timeutils.h"
#include "webrtc/system_wrappers/include/rw_lock_wrapper.h"

namespace webrtc {

const double kNtpFracPerMs = 4.294967296E6;

int64_t Clock::NtpToMs(uint32_t ntp_secs, uint32_t ntp_frac) {
  const double ntp_frac_ms = static_cast<double>(ntp_frac) / kNtpFracPerMs;
  return 1000 * static_cast<int64_t>(ntp_secs) +
      static_cast<int64_t>(ntp_frac_ms + 0.5);
}

class RealTimeClock : public Clock {
  // Return a timestamp in milliseconds relative to some arbitrary source; the
  // source is fixed for this clock.
  int64_t TimeInMilliseconds() const override {
    return rtc::TimeMillis();
  }

  // Return a timestamp in microseconds relative to some arbitrary source; the
  // source is fixed for this clock.
  int64_t TimeInMicroseconds() const override {
    return rtc::TimeMicros();
  }

  // Retrieve an NTP absolute timestamp in seconds and fractions of a second.
  void CurrentNtp(uint32_t& seconds, uint32_t& fractions) const override {
    timeval tv = CurrentTimeVal();
    double microseconds_in_seconds;
    Adjust(tv, &seconds, &microseconds_in_seconds);
    fractions = static_cast<uint32_t>(
        microseconds_in_seconds * kMagicNtpFractionalUnit + 0.5);
  }

  // Retrieve an NTP absolute timestamp in milliseconds.
  int64_t CurrentNtpInMilliseconds() const override {
    timeval tv = CurrentTimeVal();
    uint32_t seconds;
    double microseconds_in_seconds;
    Adjust(tv, &seconds, &microseconds_in_seconds);
    return 1000 * static_cast<int64_t>(seconds) +
        static_cast<int64_t>(1000.0 * microseconds_in_seconds + 0.5);
  }

 protected:
  virtual timeval CurrentTimeVal() const = 0;

  static void Adjust(const timeval& tv, uint32_t* adjusted_s,
                     double* adjusted_us_in_s) {
    *adjusted_s = tv.tv_sec + kNtpJan1970;
    *adjusted_us_in_s = tv.tv_usec / 1e6;

    if (*adjusted_us_in_s >= 1) {
      *adjusted_us_in_s -= 1;
      ++*adjusted_s;
    } else if (*adjusted_us_in_s < -1) {
      *adjusted_us_in_s += 1;
      --*adjusted_s;
    }
  }
};

#if defined(_WIN32)
// TODO(pbos): Consider modifying the implementation to synchronize itself
// against system time (update ref_point_, make it non-const) periodically to
// prevent clock drift.
class WindowsRealTimeClock : public RealTimeClock {
 public:
  WindowsRealTimeClock()
      : last_time_ms_(0),
        num_timer_wraps_(0),
        ref_point_(GetSystemReferencePoint()) {}

  virtual ~WindowsRealTimeClock() {}

 protected:
  struct ReferencePoint {
    FILETIME file_time;
    LARGE_INTEGER counter_ms;
  };

  timeval CurrentTimeVal() const override {
    const uint64_t FILETIME_1970 = 0x019db1ded53e8000;

    FILETIME StartTime;
    uint64_t Time;
    struct timeval tv;

    // We can't use query performance counter since they can change depending on
    // speed stepping.
    GetTime(&StartTime);

    Time = (((uint64_t) StartTime.dwHighDateTime) << 32) +
           (uint64_t) StartTime.dwLowDateTime;

    // Convert the hecto-nano second time to tv format.
    Time -= FILETIME_1970;

    tv.tv_sec = (uint32_t)(Time / (uint64_t)10000000);
    tv.tv_usec = (uint32_t)((Time % (uint64_t)10000000) / 10);
    return tv;
  }

  void GetTime(FILETIME* current_time) const {
    DWORD t;
    LARGE_INTEGER elapsed_ms;
    {
      rtc::CritScope lock(&crit_);
      // time MUST be fetched inside the critical section to avoid non-monotonic
      // last_time_ms_ values that'll register as incorrect wraparounds due to
      // concurrent calls to GetTime.
      t = timeGetTime();
      if (t < last_time_ms_)
        num_timer_wraps_++;
      last_time_ms_ = t;
      elapsed_ms.HighPart = num_timer_wraps_;
    }
    elapsed_ms.LowPart = t;
    elapsed_ms.QuadPart = elapsed_ms.QuadPart - ref_point_.counter_ms.QuadPart;

    // Translate to 100-nanoseconds intervals (FILETIME resolution)
    // and add to reference FILETIME to get current FILETIME.
    ULARGE_INTEGER filetime_ref_as_ul;
    filetime_ref_as_ul.HighPart = ref_point_.file_time.dwHighDateTime;
    filetime_ref_as_ul.LowPart = ref_point_.file_time.dwLowDateTime;
    filetime_ref_as_ul.QuadPart +=
        static_cast<ULONGLONG>((elapsed_ms.QuadPart) * 1000 * 10);

    // Copy to result
    current_time->dwHighDateTime = filetime_ref_as_ul.HighPart;
    current_time->dwLowDateTime = filetime_ref_as_ul.LowPart;
  }

  static ReferencePoint GetSystemReferencePoint() {
    ReferencePoint ref = {};
    FILETIME ft0 = {};
    FILETIME ft1 = {};
    // Spin waiting for a change in system time. As soon as this change happens,
    // get the matching call for timeGetTime() as soon as possible. This is
    // assumed to be the most accurate offset that we can get between
    // timeGetTime() and system time.

    // Set timer accuracy to 1 ms.
    timeBeginPeriod(1);
    GetSystemTimeAsFileTime(&ft0);
    do {
      GetSystemTimeAsFileTime(&ft1);

      ref.counter_ms.QuadPart = timeGetTime();
      Sleep(0);
    } while ((ft0.dwHighDateTime == ft1.dwHighDateTime) &&
             (ft0.dwLowDateTime == ft1.dwLowDateTime));
    ref.file_time = ft1;
    timeEndPeriod(1);
    return ref;
  }

  // mutable as time-accessing functions are const.
  rtc::CriticalSection crit_;
  mutable DWORD last_time_ms_;
  mutable LONG num_timer_wraps_;
  const ReferencePoint ref_point_;
};

#elif ((defined WEBRTC_LINUX) || (defined WEBRTC_MAC))
class UnixRealTimeClock : public RealTimeClock {
 public:
  UnixRealTimeClock() {}

  ~UnixRealTimeClock() override {}

 protected:
  timeval CurrentTimeVal() const override {
    struct timeval tv;
    struct timezone tz;
    tz.tz_minuteswest = 0;
    tz.tz_dsttime = 0;
    gettimeofday(&tv, &tz);
    return tv;
  }
};
#endif

#if defined(_WIN32)
static WindowsRealTimeClock* volatile g_shared_clock = nullptr;
#endif
Clock* Clock::GetRealTimeClock() {
#if defined(_WIN32)
  // This read relies on volatile read being atomic-load-acquire. This is
  // true in MSVC since at least 2005:
  // "A read of a volatile object (volatile read) has Acquire semantics"
  if (g_shared_clock != nullptr)
    return g_shared_clock;
  WindowsRealTimeClock* clock = new WindowsRealTimeClock;
  if (InterlockedCompareExchangePointer(
          reinterpret_cast<void* volatile*>(&g_shared_clock), clock, nullptr) !=
      nullptr) {
    // g_shared_clock was assigned while we constructed/tried to assign our
    // instance, delete our instance and use the existing one.
    delete clock;
  }
  return g_shared_clock;
#elif defined(WEBRTC_LINUX) || defined(WEBRTC_MAC)
  static UnixRealTimeClock clock;
  return &clock;
#else
  return NULL;
#endif
}

SimulatedClock::SimulatedClock(int64_t initial_time_us)
    : time_us_(initial_time_us), lock_(RWLockWrapper::CreateRWLock()) {
}

SimulatedClock::~SimulatedClock() {
}

int64_t SimulatedClock::TimeInMilliseconds() const {
  ReadLockScoped synchronize(*lock_);
  return (time_us_ + 500) / 1000;
}

int64_t SimulatedClock::TimeInMicroseconds() const {
  ReadLockScoped synchronize(*lock_);
  return time_us_;
}

void SimulatedClock::CurrentNtp(uint32_t& seconds, uint32_t& fractions) const {
  int64_t now_ms = TimeInMilliseconds();
  seconds = (now_ms / 1000) + kNtpJan1970;
  fractions =
      static_cast<uint32_t>((now_ms % 1000) * kMagicNtpFractionalUnit / 1000);
}

int64_t SimulatedClock::CurrentNtpInMilliseconds() const {
  return TimeInMilliseconds() + 1000 * static_cast<int64_t>(kNtpJan1970);
}

void SimulatedClock::AdvanceTimeMilliseconds(int64_t milliseconds) {
  AdvanceTimeMicroseconds(1000 * milliseconds);
}

void SimulatedClock::AdvanceTimeMicroseconds(int64_t microseconds) {
  WriteLockScoped synchronize(*lock_);
  time_us_ += microseconds;
}

};  // namespace webrtc
