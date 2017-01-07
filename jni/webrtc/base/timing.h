/*
 *  Copyright 2008 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_TIMING_H_
#define WEBRTC_BASE_TIMING_H_

namespace rtc {

// TODO(deadbeef): Remove this and use ClockInterface instead.
class Timing {
 public:
  Timing();
  virtual ~Timing();

  // WallTimeNow() returns the current wall-clock time in seconds,
  // within 10 milliseconds resolution.
  // WallTimeNow is static and does not require a timer_handle_ on Windows.
  static double WallTimeNow();

  // TimerNow() is like WallTimeNow(), but is monotonically
  // increasing.  It returns seconds in resolution of 10 microseconds
  // or better.  Although timer and wall-clock time have the same
  // timing unit, they do not necessarily correlate because wall-clock
  // time may be adjusted backwards, hence not monotonic.
  // Made virtual so we can make a fake one.
  // TODO(tommi): The only place we use this (virtual) is in
  // rtpdata_engine_unittest.cc.  See if it doesn't make more sense to change
  // that contract or test than to modify this generic class.
  virtual double TimerNow();
};

}  // namespace rtc

#endif // WEBRTC_BASE_TIMING_H_
