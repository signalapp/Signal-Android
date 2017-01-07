/*
 *  Copyright 2008 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/base/timing.h"
#include "webrtc/base/timeutils.h"

#if defined(WEBRTC_POSIX)
#include <errno.h>
#include <math.h>
#include <sys/time.h>
#if defined(WEBRTC_MAC) && !defined(WEBRTC_IOS)
#include <mach/mach.h>
#include <mach/clock.h>
#endif
#elif defined(WEBRTC_WIN)
#include <sys/timeb.h>
#endif

namespace rtc {

Timing::Timing() {}

Timing::~Timing() {}

// static
double Timing::WallTimeNow() {
#if defined(WEBRTC_POSIX)
  struct timeval time;
  gettimeofday(&time, NULL);
  // Convert from second (1.0) and microsecond (1e-6).
  return (static_cast<double>(time.tv_sec) +
          static_cast<double>(time.tv_usec) * 1.0e-6);

#elif defined(WEBRTC_WIN)
  struct _timeb time;
  _ftime(&time);
  // Convert from second (1.0) and milliseconds (1e-3).
  return (static_cast<double>(time.time) +
          static_cast<double>(time.millitm) * 1.0e-3);
#endif
}

double Timing::TimerNow() {
  return (static_cast<double>(TimeNanos()) / kNumNanosecsPerSec);
}

}  // namespace rtc
