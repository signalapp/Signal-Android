/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/system_wrappers/source/condition_variable_posix.h"

#include <errno.h>
#if defined(WEBRTC_LINUX)
#include <time.h>
#else
#include <sys/time.h>
#endif

#include "webrtc/system_wrappers/source/critical_section_posix.h"

namespace webrtc {

ConditionVariableWrapper* ConditionVariablePosix::Create() {
  ConditionVariablePosix* ptr = new ConditionVariablePosix;
  if (!ptr) {
    return NULL;
  }

  const int error = ptr->Construct();
  if (error) {
    delete ptr;
    return NULL;
  }

  return ptr;
}

ConditionVariablePosix::ConditionVariablePosix() {
}

int ConditionVariablePosix::Construct() {
#ifdef WEBRTC_CLOCK_TYPE_REALTIME
  pthread_cond_init(&cond_, NULL);
#else
  int result = 0;
  pthread_condattr_t cond_attr;
  result = pthread_condattr_init(&cond_attr);
  if (result != 0) {
    return -1;
  }
  result = pthread_condattr_setclock(&cond_attr, CLOCK_MONOTONIC);
  if (result != 0) {
    return -1;
  }
  result = pthread_cond_init(&cond_, &cond_attr);
  if (result != 0) {
    return -1;
  }
  result = pthread_condattr_destroy(&cond_attr);
  if (result != 0) {
    return -1;
  }
#endif
  return 0;
}

ConditionVariablePosix::~ConditionVariablePosix() {
  pthread_cond_destroy(&cond_);
}

void ConditionVariablePosix::SleepCS(CriticalSectionWrapper& crit_sect) {
  CriticalSectionPosix* cs = reinterpret_cast<CriticalSectionPosix*>(
      &crit_sect);
  pthread_cond_wait(&cond_, &cs->mutex_);
}

bool ConditionVariablePosix::SleepCS(CriticalSectionWrapper& crit_sect,
                                     unsigned long max_time_inMS) {
  const unsigned long INFINITE =  0xFFFFFFFF;
  const int MILLISECONDS_PER_SECOND = 1000;
#ifndef WEBRTC_LINUX
  const int MICROSECONDS_PER_MILLISECOND = 1000;
#endif
  const int NANOSECONDS_PER_SECOND = 1000000000;
  const int NANOSECONDS_PER_MILLISECOND  = 1000000;

  CriticalSectionPosix* cs = reinterpret_cast<CriticalSectionPosix*>(
      &crit_sect);

  if (max_time_inMS != INFINITE) {
    timespec ts;
#ifndef WEBRTC_MAC
#ifdef WEBRTC_CLOCK_TYPE_REALTIME
    clock_gettime(CLOCK_REALTIME, &ts);
#else
    clock_gettime(CLOCK_MONOTONIC, &ts);
#endif
#else  // WEBRTC_MAC
    struct timeval tv;
    gettimeofday(&tv, 0);
    ts.tv_sec  = tv.tv_sec;
    ts.tv_nsec = tv.tv_usec * MICROSECONDS_PER_MILLISECOND;
#endif

    ts.tv_sec += max_time_inMS / MILLISECONDS_PER_SECOND;
    ts.tv_nsec +=
        (max_time_inMS
        - ((max_time_inMS / MILLISECONDS_PER_SECOND) * MILLISECONDS_PER_SECOND))
        * NANOSECONDS_PER_MILLISECOND;

    if (ts.tv_nsec >= NANOSECONDS_PER_SECOND) {
      ts.tv_sec += ts.tv_nsec / NANOSECONDS_PER_SECOND;
      ts.tv_nsec %= NANOSECONDS_PER_SECOND;
    }
    const int res = pthread_cond_timedwait(&cond_, &cs->mutex_, &ts);
    return (res == ETIMEDOUT) ? false : true;
  } else {
    pthread_cond_wait(&cond_, &cs->mutex_);
    return true;
  }
}

void ConditionVariablePosix::Wake() {
  pthread_cond_signal(&cond_);
}

void ConditionVariablePosix::WakeAll() {
  pthread_cond_broadcast(&cond_);
}

}  // namespace webrtc
