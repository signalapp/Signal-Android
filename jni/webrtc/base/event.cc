/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/base/event.h"

#if defined(WEBRTC_WIN)
#include <windows.h>
#elif defined(WEBRTC_POSIX)
#include <pthread.h>
#include <sys/time.h>
#include <time.h>
#else
#error "Must define either WEBRTC_WIN or WEBRTC_POSIX."
#endif

#include "webrtc/base/checks.h"

namespace rtc {

#if defined(WEBRTC_WIN)

Event::Event(bool manual_reset, bool initially_signaled) {
  event_handle_ = ::CreateEvent(NULL,                 // Security attributes.
                                manual_reset,
                                initially_signaled,
                                NULL);                // Name.
  RTC_CHECK(event_handle_);
}

Event::~Event() {
  CloseHandle(event_handle_);
}

void Event::Set() {
  SetEvent(event_handle_);
}

void Event::Reset() {
  ResetEvent(event_handle_);
}

bool Event::Wait(int milliseconds) {
  DWORD ms = (milliseconds == kForever) ? INFINITE : milliseconds;
  return (WaitForSingleObject(event_handle_, ms) == WAIT_OBJECT_0);
}

#elif defined(WEBRTC_POSIX)

Event::Event(bool manual_reset, bool initially_signaled)
    : is_manual_reset_(manual_reset),
      event_status_(initially_signaled) {
  RTC_CHECK(pthread_mutex_init(&event_mutex_, NULL) == 0);
  RTC_CHECK(pthread_cond_init(&event_cond_, NULL) == 0);
}

Event::~Event() {
  pthread_mutex_destroy(&event_mutex_);
  pthread_cond_destroy(&event_cond_);
}

void Event::Set() {
  pthread_mutex_lock(&event_mutex_);
  event_status_ = true;
  pthread_cond_broadcast(&event_cond_);
  pthread_mutex_unlock(&event_mutex_);
}

void Event::Reset() {
  pthread_mutex_lock(&event_mutex_);
  event_status_ = false;
  pthread_mutex_unlock(&event_mutex_);
}

bool Event::Wait(int milliseconds) {
  int error = 0;

  struct timespec ts;
  if (milliseconds != kForever) {
    // Converting from seconds and microseconds (1e-6) plus
    // milliseconds (1e-3) to seconds and nanoseconds (1e-9).

#if HAVE_PTHREAD_COND_TIMEDWAIT_RELATIVE
    // Use relative time version, which tends to be more efficient for
    // pthread implementations where provided (like on Android).
    ts.tv_sec = milliseconds / 1000;
    ts.tv_nsec = (milliseconds % 1000) * 1000000;
#else
    struct timeval tv;
    gettimeofday(&tv, NULL);

    ts.tv_sec = tv.tv_sec + (milliseconds / 1000);
    ts.tv_nsec = tv.tv_usec * 1000 + (milliseconds % 1000) * 1000000;

    // Handle overflow.
    if (ts.tv_nsec >= 1000000000) {
      ts.tv_sec++;
      ts.tv_nsec -= 1000000000;
    }
#endif
  }

  pthread_mutex_lock(&event_mutex_);
  if (milliseconds != kForever) {
    while (!event_status_ && error == 0) {
#if HAVE_PTHREAD_COND_TIMEDWAIT_RELATIVE
      error = pthread_cond_timedwait_relative_np(
          &event_cond_, &event_mutex_, &ts);
#else
      error = pthread_cond_timedwait(&event_cond_, &event_mutex_, &ts);
#endif
    }
  } else {
    while (!event_status_ && error == 0)
      error = pthread_cond_wait(&event_cond_, &event_mutex_);
  }

  // NOTE(liulk): Exactly one thread will auto-reset this event. All
  // the other threads will think it's unsignaled.  This seems to be
  // consistent with auto-reset events in WEBRTC_WIN
  if (error == 0 && !is_manual_reset_)
    event_status_ = false;

  pthread_mutex_unlock(&event_mutex_);

  return (error == 0);
}

#endif

}  // namespace rtc
