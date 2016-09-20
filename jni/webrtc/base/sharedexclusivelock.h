/*
 *  Copyright 2011 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_SHAREDEXCLUSIVELOCK_H_
#define WEBRTC_BASE_SHAREDEXCLUSIVELOCK_H_

#include "webrtc/base/constructormagic.h"
#include "webrtc/base/criticalsection.h"
#include "webrtc/base/event.h"

namespace rtc {

// This class provides shared-exclusive lock. It can be used in cases like
// multiple-readers/single-writer model.
class LOCKABLE SharedExclusiveLock {
 public:
  SharedExclusiveLock();

  // Locking/unlocking methods. It is encouraged to use SharedScope or
  // ExclusiveScope for protection.
  void LockExclusive() EXCLUSIVE_LOCK_FUNCTION();
  void UnlockExclusive() UNLOCK_FUNCTION();
  void LockShared();
  void UnlockShared();

 private:
  rtc::CriticalSection cs_exclusive_;
  rtc::CriticalSection cs_shared_;
  rtc::Event shared_count_is_zero_;
  int shared_count_;

  RTC_DISALLOW_COPY_AND_ASSIGN(SharedExclusiveLock);
};

class SCOPED_LOCKABLE SharedScope {
 public:
  explicit SharedScope(SharedExclusiveLock* lock) SHARED_LOCK_FUNCTION(lock)
      : lock_(lock) {
    lock_->LockShared();
  }

  ~SharedScope() UNLOCK_FUNCTION() { lock_->UnlockShared(); }

 private:
  SharedExclusiveLock* lock_;

  RTC_DISALLOW_COPY_AND_ASSIGN(SharedScope);
};

class SCOPED_LOCKABLE ExclusiveScope {
 public:
  explicit ExclusiveScope(SharedExclusiveLock* lock)
      EXCLUSIVE_LOCK_FUNCTION(lock)
      : lock_(lock) {
    lock_->LockExclusive();
  }

  ~ExclusiveScope() UNLOCK_FUNCTION() { lock_->UnlockExclusive(); }

 private:
  SharedExclusiveLock* lock_;

  RTC_DISALLOW_COPY_AND_ASSIGN(ExclusiveScope);
};

}  // namespace rtc

#endif  // WEBRTC_BASE_SHAREDEXCLUSIVELOCK_H_
