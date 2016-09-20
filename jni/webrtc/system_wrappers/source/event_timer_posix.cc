/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/system_wrappers/source/event_timer_posix.h"

#include <errno.h>
#include <pthread.h>
#include <signal.h>
#include <stdio.h>
#include <string.h>
#include <sys/time.h>
#include <unistd.h>

#include "webrtc/base/checks.h"

namespace webrtc {

// static
EventTimerWrapper* EventTimerWrapper::Create() {
  return new EventTimerPosix();
}

const int64_t kNanosecondsPerMillisecond = 1000000;
const int64_t kNanosecondsPerSecond = 1000000000;

EventTimerPosix::EventTimerPosix()
    : event_set_(false),
      timer_thread_(nullptr),
      created_at_(),
      periodic_(false),
      time_ms_(0),
      count_(0),
      is_stopping_(false) {
  pthread_mutexattr_t attr;
  pthread_mutexattr_init(&attr);
  pthread_mutexattr_settype(&attr, PTHREAD_MUTEX_RECURSIVE);
  pthread_mutex_init(&mutex_, &attr);
  pthread_condattr_t cond_attr;
  pthread_condattr_init(&cond_attr);
// TODO(sprang): Remove HAVE_PTHREAD_COND_TIMEDWAIT_MONOTONIC special case once
// all supported Android platforms support pthread_condattr_setclock.
// TODO(sprang): Add support for monotonic clock on Apple platforms.
#if !(defined(WEBRTC_MAC) || defined(WEBRTC_IOS)) && \
    !(defined(WEBRTC_ANDROID) &&                     \
      defined(HAVE_PTHREAD_COND_TIMEDWAIT_MONOTONIC))
  pthread_condattr_setclock(&cond_attr, CLOCK_MONOTONIC);
#endif
  pthread_cond_init(&cond_, &cond_attr);
  pthread_condattr_destroy(&cond_attr);
}

EventTimerPosix::~EventTimerPosix() {
  StopTimer();
  pthread_cond_destroy(&cond_);
  pthread_mutex_destroy(&mutex_);
}

// TODO(pbos): Make this void.
bool EventTimerPosix::Set() {
  RTC_CHECK_EQ(0, pthread_mutex_lock(&mutex_));
  event_set_ = true;
  pthread_cond_signal(&cond_);
  pthread_mutex_unlock(&mutex_);
  return true;
}

EventTypeWrapper EventTimerPosix::Wait(unsigned long timeout_ms) {
  int ret_val = 0;
  RTC_CHECK_EQ(0, pthread_mutex_lock(&mutex_));

  if (!event_set_) {
    if (WEBRTC_EVENT_INFINITE != timeout_ms) {
      timespec end_at;
#ifndef WEBRTC_MAC
      clock_gettime(CLOCK_MONOTONIC, &end_at);
#else
      timeval value;
      struct timezone time_zone;
      time_zone.tz_minuteswest = 0;
      time_zone.tz_dsttime = 0;
      gettimeofday(&value, &time_zone);
      TIMEVAL_TO_TIMESPEC(&value, &end_at);
#endif
      end_at.tv_sec += timeout_ms / 1000;
      end_at.tv_nsec += (timeout_ms % 1000) * kNanosecondsPerMillisecond;

      if (end_at.tv_nsec >= kNanosecondsPerSecond) {
        end_at.tv_sec++;
        end_at.tv_nsec -= kNanosecondsPerSecond;
      }
      while (ret_val == 0 && !event_set_) {
#if defined(WEBRTC_ANDROID) && defined(HAVE_PTHREAD_COND_TIMEDWAIT_MONOTONIC)
        ret_val = pthread_cond_timedwait_monotonic_np(&cond_, &mutex_, &end_at);
#else
        ret_val = pthread_cond_timedwait(&cond_, &mutex_, &end_at);
#endif  // WEBRTC_ANDROID && HAVE_PTHREAD_COND_TIMEDWAIT_MONOTONIC
      }
    } else {
      while (ret_val == 0 && !event_set_)
        ret_val = pthread_cond_wait(&cond_, &mutex_);
    }
  }

  RTC_DCHECK(ret_val == 0 || ret_val == ETIMEDOUT);

  // Reset and signal if set, regardless of why the thread woke up.
  if (event_set_) {
    ret_val = 0;
    event_set_ = false;
  }
  pthread_mutex_unlock(&mutex_);

  return ret_val == 0 ? kEventSignaled : kEventTimeout;
}

EventTypeWrapper EventTimerPosix::Wait(timespec* end_at, bool reset_event) {
  int ret_val = 0;
  RTC_CHECK_EQ(0, pthread_mutex_lock(&mutex_));
  if (reset_event) {
    // Only wake for new events or timeouts.
    event_set_ = false;
  }

  while (ret_val == 0 && !event_set_) {
#if defined(WEBRTC_ANDROID) && defined(HAVE_PTHREAD_COND_TIMEDWAIT_MONOTONIC)
    ret_val = pthread_cond_timedwait_monotonic_np(&cond_, &mutex_, end_at);
#else
    ret_val = pthread_cond_timedwait(&cond_, &mutex_, end_at);
#endif  // WEBRTC_ANDROID && HAVE_PTHREAD_COND_TIMEDWAIT_MONOTONIC
  }

  RTC_DCHECK(ret_val == 0 || ret_val == ETIMEDOUT);

  // Reset and signal if set, regardless of why the thread woke up.
  if (event_set_) {
    ret_val = 0;
    event_set_ = false;
  }
  pthread_mutex_unlock(&mutex_);

  return ret_val == 0 ? kEventSignaled : kEventTimeout;
}

rtc::PlatformThread* EventTimerPosix::CreateThread() {
  const char* kThreadName = "WebRtc_event_timer_thread";
  return new rtc::PlatformThread(Run, this, kThreadName);
}

bool EventTimerPosix::StartTimer(bool periodic, unsigned long time_ms) {
  pthread_mutex_lock(&mutex_);
  if (timer_thread_) {
    if (periodic_) {
      // Timer already started.
      pthread_mutex_unlock(&mutex_);
      return false;
    } else  {
      // New one shot timer.
      time_ms_ = time_ms;
      created_at_.tv_sec = 0;
      timer_event_->Set();
      pthread_mutex_unlock(&mutex_);
      return true;
    }
  }

  // Start the timer thread.
  timer_event_.reset(new EventTimerPosix());
  timer_thread_.reset(CreateThread());
  periodic_ = periodic;
  time_ms_ = time_ms;
  timer_thread_->Start();
  timer_thread_->SetPriority(rtc::kRealtimePriority);
  pthread_mutex_unlock(&mutex_);

  return true;
}

bool EventTimerPosix::Run(void* obj) {
  return static_cast<EventTimerPosix*>(obj)->Process();
}

bool EventTimerPosix::Process() {
  pthread_mutex_lock(&mutex_);
  if (is_stopping_) {
    pthread_mutex_unlock(&mutex_);
    return false;
  }
  if (created_at_.tv_sec == 0) {
#ifndef WEBRTC_MAC
    RTC_CHECK_EQ(0, clock_gettime(CLOCK_MONOTONIC, &created_at_));
#else
    timeval value;
    struct timezone time_zone;
    time_zone.tz_minuteswest = 0;
    time_zone.tz_dsttime = 0;
    gettimeofday(&value, &time_zone);
    TIMEVAL_TO_TIMESPEC(&value, &created_at_);
#endif
    count_ = 0;
  }

  timespec end_at;
  unsigned long long total_delta_ms = time_ms_ * ++count_;
  if (!periodic_ && count_ >= 1) {
    // No need to wake up often if we're not going to signal waiting threads.
    total_delta_ms =
        std::min<uint64_t>(total_delta_ms, 60 * kNanosecondsPerSecond);
  }

  end_at.tv_sec = created_at_.tv_sec + total_delta_ms / 1000;
  end_at.tv_nsec = created_at_.tv_nsec +
                   (total_delta_ms % 1000) * kNanosecondsPerMillisecond;

  if (end_at.tv_nsec >= kNanosecondsPerSecond) {
    end_at.tv_sec++;
    end_at.tv_nsec -= kNanosecondsPerSecond;
  }

  pthread_mutex_unlock(&mutex_);
  // Reset event on first call so that we don't immediately return here if this
  // thread was not blocked on timer_event_->Wait when the StartTimer() call
  // was made.
  if (timer_event_->Wait(&end_at, count_ == 1) == kEventSignaled)
    return true;

  pthread_mutex_lock(&mutex_);
  if (periodic_ || count_ == 1)
    Set();
  pthread_mutex_unlock(&mutex_);

  return true;
}

bool EventTimerPosix::StopTimer() {
  pthread_mutex_lock(&mutex_);
  is_stopping_ = true;
  pthread_mutex_unlock(&mutex_);

  if (timer_event_)
    timer_event_->Set();

  if (timer_thread_) {
    timer_thread_->Stop();
    timer_thread_.reset();
  }
  timer_event_.reset();

  // Set time to zero to force new reference time for the timer.
  memset(&created_at_, 0, sizeof(created_at_));
  count_ = 0;
  return true;
}

}  // namespace webrtc
