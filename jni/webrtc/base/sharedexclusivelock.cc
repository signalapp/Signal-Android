/*
 *  Copyright 2011 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/base/sharedexclusivelock.h"

namespace rtc {

SharedExclusiveLock::SharedExclusiveLock()
    : shared_count_is_zero_(true, true),
      shared_count_(0) {
}

void SharedExclusiveLock::LockExclusive() {
  cs_exclusive_.Enter();
  shared_count_is_zero_.Wait(Event::kForever);
}

void SharedExclusiveLock::UnlockExclusive() {
  cs_exclusive_.Leave();
}

void SharedExclusiveLock::LockShared() {
  CritScope exclusive_scope(&cs_exclusive_);
  CritScope shared_scope(&cs_shared_);
  if (++shared_count_ == 1) {
    shared_count_is_zero_.Reset();
  }
}

void SharedExclusiveLock::UnlockShared() {
  CritScope shared_scope(&cs_shared_);
  if (--shared_count_ == 0) {
    shared_count_is_zero_.Set();
  }
}

}  // namespace rtc
