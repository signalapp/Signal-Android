/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/system_wrappers/source/rw_lock_winxp_win.h"

namespace webrtc {
namespace {
class ScopedLock {
 public:
  ScopedLock(CRITICAL_SECTION* lock) : lock_(lock) {
    EnterCriticalSection(lock_);
  }
  ~ScopedLock() {
    LeaveCriticalSection(lock_);
  }
 private:
  CRITICAL_SECTION* const lock_;
};
}

RWLockWinXP::RWLockWinXP() {
  InitializeCriticalSection(&critical_section_);
}

RWLockWinXP::~RWLockWinXP() {
  DeleteCriticalSection(&critical_section_);
}

void RWLockWinXP::AcquireLockExclusive() {
  ScopedLock cs(&critical_section_);
  if (writer_active_ || readers_active_ > 0) {
    ++writers_waiting_;
    while (writer_active_ || readers_active_ > 0) {
      write_condition_.SleepCS(&critical_section_);
    }
    --writers_waiting_;
  }
  writer_active_ = true;
}

void RWLockWinXP::ReleaseLockExclusive() {
  ScopedLock cs(&critical_section_);
  writer_active_ = false;
  if (writers_waiting_ > 0) {
    write_condition_.Wake();
  } else if (readers_waiting_ > 0) {
    read_condition_.WakeAll();
  }
}

void RWLockWinXP::AcquireLockShared() {
  ScopedLock cs(&critical_section_);
  if (writer_active_ || writers_waiting_ > 0) {
    ++readers_waiting_;

    while (writer_active_ || writers_waiting_ > 0) {
      read_condition_.SleepCS(&critical_section_);
    }
    --readers_waiting_;
  }
  ++readers_active_;
}

void RWLockWinXP::ReleaseLockShared() {
  ScopedLock cs(&critical_section_);
  --readers_active_;
  if (readers_active_ == 0 && writers_waiting_ > 0) {
    write_condition_.Wake();
  }
}

}  // namespace webrtc
