/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/system_wrappers/source/rw_lock_win.h"

#include "webrtc/system_wrappers/interface/trace.h"

namespace webrtc {

static bool native_rw_locks_supported = false;
static bool module_load_attempted = false;
static HMODULE library = NULL;

typedef void (WINAPI* InitializeSRWLock)(PSRWLOCK);

typedef void (WINAPI* AcquireSRWLockExclusive)(PSRWLOCK);
typedef void (WINAPI* ReleaseSRWLockExclusive)(PSRWLOCK);

typedef void (WINAPI* AcquireSRWLockShared)(PSRWLOCK);
typedef void (WINAPI* ReleaseSRWLockShared)(PSRWLOCK);

InitializeSRWLock       initialize_srw_lock;
AcquireSRWLockExclusive acquire_srw_lock_exclusive;
AcquireSRWLockShared    acquire_srw_lock_shared;
ReleaseSRWLockShared    release_srw_lock_shared;
ReleaseSRWLockExclusive release_srw_lock_exclusive;

RWLockWin::RWLockWin() {
  initialize_srw_lock(&lock_);
}

RWLockWin* RWLockWin::Create() {
  if (!LoadModule()) {
    return NULL;
  }
  return new RWLockWin();
}

void RWLockWin::AcquireLockExclusive() {
  acquire_srw_lock_exclusive(&lock_);
}

void RWLockWin::ReleaseLockExclusive() {
  release_srw_lock_exclusive(&lock_);
}

void RWLockWin::AcquireLockShared() {
  acquire_srw_lock_shared(&lock_);
}

void RWLockWin::ReleaseLockShared() {
  release_srw_lock_shared(&lock_);
}

bool RWLockWin::LoadModule() {
  if (module_load_attempted) {
    return native_rw_locks_supported;
  }
  module_load_attempted = true;
  // Use native implementation if supported (i.e Vista+)
  library = LoadLibrary(TEXT("Kernel32.dll"));
  if (!library) {
    return false;
  }
  WEBRTC_TRACE(kTraceStateInfo, kTraceUtility, -1, "Loaded Kernel.dll");

  initialize_srw_lock =
    (InitializeSRWLock)GetProcAddress(library, "InitializeSRWLock");

  acquire_srw_lock_exclusive =
    (AcquireSRWLockExclusive)GetProcAddress(library,
                                            "AcquireSRWLockExclusive");
  release_srw_lock_exclusive =
    (ReleaseSRWLockExclusive)GetProcAddress(library,
                                            "ReleaseSRWLockExclusive");
  acquire_srw_lock_shared =
    (AcquireSRWLockShared)GetProcAddress(library, "AcquireSRWLockShared");
  release_srw_lock_shared =
    (ReleaseSRWLockShared)GetProcAddress(library, "ReleaseSRWLockShared");

  if (initialize_srw_lock && acquire_srw_lock_exclusive &&
      release_srw_lock_exclusive && acquire_srw_lock_shared &&
      release_srw_lock_shared) {
    WEBRTC_TRACE(kTraceStateInfo, kTraceUtility, -1, "Loaded Native RW Lock");
    native_rw_locks_supported = true;
  }
  return native_rw_locks_supported;
}

}  // namespace webrtc
