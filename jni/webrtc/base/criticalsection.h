/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_CRITICALSECTION_H_
#define WEBRTC_BASE_CRITICALSECTION_H_

#include "webrtc/base/atomicops.h"
#include "webrtc/base/constructormagic.h"
#include "webrtc/base/thread_annotations.h"
#include "webrtc/base/platform_thread_types.h"

#if defined(WEBRTC_WIN)
// Include winsock2.h before including <windows.h> to maintain consistency with
// win32.h.  We can't include win32.h directly here since it pulls in
// headers such as basictypes.h which causes problems in Chromium where webrtc
// exists as two separate projects, webrtc and libjingle.
#include <winsock2.h>
#include <windows.h>
#include <sal.h>  // must come after windows headers.
#endif  // defined(WEBRTC_WIN)

#if defined(WEBRTC_POSIX)
#include <pthread.h>
#endif

// See notes in the 'Performance' unit test for the effects of this flag.
#define USE_NATIVE_MUTEX_ON_MAC 0

#if defined(WEBRTC_MAC) && !USE_NATIVE_MUTEX_ON_MAC
#include <dispatch/dispatch.h>
#endif

#if (!defined(NDEBUG) || defined(DCHECK_ALWAYS_ON))
#define CS_DEBUG_CHECKS 1
#endif

#undef CS_DEBUG_CHECKS

#if CS_DEBUG_CHECKS
#define CS_DEBUG_CODE(x) x
#else  // !CS_DEBUG_CHECKS
#define CS_DEBUG_CODE(x)
#endif  // !CS_DEBUG_CHECKS

namespace rtc {

// Locking methods (Enter, TryEnter, Leave)are const to permit protecting
// members inside a const context without requiring mutable CriticalSections
// everywhere.
class LOCKABLE CriticalSection {
 public:
  CriticalSection();
  ~CriticalSection();

  void Enter() const EXCLUSIVE_LOCK_FUNCTION();
  bool TryEnter() const EXCLUSIVE_TRYLOCK_FUNCTION(true);
  void Leave() const UNLOCK_FUNCTION();

  // Use only for RTC_DCHECKing.
  bool CurrentThreadIsOwner() const;
  // Use only for RTC_DCHECKing.
  bool IsLocked() const;

 private:
#if defined(WEBRTC_WIN)
  mutable CRITICAL_SECTION crit_;
#elif defined(WEBRTC_POSIX)
#if defined(WEBRTC_MAC) && !USE_NATIVE_MUTEX_ON_MAC
  // Number of times the lock has been locked + number of threads waiting.
  // TODO(tommi): We could use this number and subtract the recursion count
  // to find places where we have multiple threads contending on the same lock.
  mutable volatile int lock_queue_;
  // |recursion_| represents the recursion count + 1 for the thread that owns
  // the lock. Only modified by the thread that owns the lock.
  mutable int recursion_;
  // Used to signal a single waiting thread when the lock becomes available.
  mutable dispatch_semaphore_t semaphore_;
  // The thread that currently holds the lock. Required to handle recursion.
  mutable PlatformThreadRef owning_thread_;
#else
  mutable pthread_mutex_t mutex_;
#endif
  CS_DEBUG_CODE(mutable PlatformThreadRef thread_);
  CS_DEBUG_CODE(mutable int recursion_count_);
#endif
};

// CritScope, for serializing execution through a scope.
class SCOPED_LOCKABLE CritScope {
 public:
  explicit CritScope(const CriticalSection* cs) EXCLUSIVE_LOCK_FUNCTION(cs);
  ~CritScope() UNLOCK_FUNCTION();
 private:
  const CriticalSection* const cs_;
  RTC_DISALLOW_COPY_AND_ASSIGN(CritScope);
};

// Tries to lock a critical section on construction via
// CriticalSection::TryEnter, and unlocks on destruction if the
// lock was taken. Never blocks.
//
// IMPORTANT: Unlike CritScope, the lock may not be owned by this thread in
// subsequent code. Users *must* check locked() to determine if the
// lock was taken. If you're not calling locked(), you're doing it wrong!
class TryCritScope {
 public:
  explicit TryCritScope(const CriticalSection* cs);
  ~TryCritScope();
#if defined(WEBRTC_WIN)
  _Check_return_ bool locked() const;
#else
  bool locked() const __attribute__ ((__warn_unused_result__));
#endif
 private:
  const CriticalSection* const cs_;
  const bool locked_;
  CS_DEBUG_CODE(mutable bool lock_was_called_);
  RTC_DISALLOW_COPY_AND_ASSIGN(TryCritScope);
};

// A POD lock used to protect global variables. Do NOT use for other purposes.
// No custom constructor or private data member should be added.
class LOCKABLE GlobalLockPod {
 public:
  void Lock() EXCLUSIVE_LOCK_FUNCTION();

  void Unlock() UNLOCK_FUNCTION();

  volatile int lock_acquired;
};

class GlobalLock : public GlobalLockPod {
 public:
  GlobalLock();
};

// GlobalLockScope, for serializing execution through a scope.
class SCOPED_LOCKABLE GlobalLockScope {
 public:
  explicit GlobalLockScope(GlobalLockPod* lock) EXCLUSIVE_LOCK_FUNCTION(lock);
  ~GlobalLockScope() UNLOCK_FUNCTION();
 private:
  GlobalLockPod* const lock_;
  RTC_DISALLOW_COPY_AND_ASSIGN(GlobalLockScope);
};

} // namespace rtc

#endif // WEBRTC_BASE_CRITICALSECTION_H_
