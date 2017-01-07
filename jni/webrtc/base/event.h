/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_EVENT_H__
#define WEBRTC_BASE_EVENT_H__

#if defined(WEBRTC_WIN)
#include "webrtc/base/win32.h"  // NOLINT: consider this a system header.
#elif defined(WEBRTC_POSIX)
#include <pthread.h>
#else
#error "Must define either WEBRTC_WIN or WEBRTC_POSIX."
#endif

#include "webrtc/base/basictypes.h"

namespace rtc {

class Event {
 public:
  static const int kForever = -1;

  Event(bool manual_reset, bool initially_signaled);
  ~Event();

  void Set();
  void Reset();

  // Wait for the event to become signaled, for the specified number of
  // |milliseconds|.  To wait indefinetly, pass kForever.
  bool Wait(int milliseconds);

 private:
#if defined(WEBRTC_WIN)
  HANDLE event_handle_;
#elif defined(WEBRTC_POSIX)
  pthread_mutex_t event_mutex_;
  pthread_cond_t event_cond_;
  const bool is_manual_reset_;
  bool event_status_;
#endif
};

}  // namespace rtc

#endif  // WEBRTC_BASE_EVENT_H__
