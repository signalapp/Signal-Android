/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// General note: return values for the various pthread synchronization APIs
// are explicitly ignored here. In Chromium, the same thing is done for release.
// However, in debugging, failure in these APIs are logged. There is currently
// no equivalent to DCHECK_EQ in WebRTC code so this is the best we can do here.
// TODO(henrike): add logging when pthread synchronization APIs are failing.

#include "webrtc/system_wrappers/source/critical_section_posix.h"

namespace webrtc {

CriticalSectionPosix::CriticalSectionPosix() {
  pthread_mutexattr_t attr;
  (void) pthread_mutexattr_init(&attr);
  (void) pthread_mutexattr_settype(&attr, PTHREAD_MUTEX_RECURSIVE);
  (void) pthread_mutex_init(&mutex_, &attr);
}

CriticalSectionPosix::~CriticalSectionPosix() {
  (void) pthread_mutex_destroy(&mutex_);
}

void
CriticalSectionPosix::Enter() {
  (void) pthread_mutex_lock(&mutex_);
}

void
CriticalSectionPosix::Leave() {
  (void) pthread_mutex_unlock(&mutex_);
}

}  // namespace webrtc
