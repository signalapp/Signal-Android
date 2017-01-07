/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_SYSTEM_WRAPPERS_SOURCE_RW_LOCK_WIN_H_
#define WEBRTC_SYSTEM_WRAPPERS_SOURCE_RW_LOCK_WIN_H_

#include "webrtc/system_wrappers/include/rw_lock_wrapper.h"

#include <Windows.h>

namespace webrtc {

class RWLockWin : public RWLockWrapper {
 public:
  static RWLockWin* Create();
  ~RWLockWin() {}

  virtual void AcquireLockExclusive();
  virtual void ReleaseLockExclusive();

  virtual void AcquireLockShared();
  virtual void ReleaseLockShared();

 private:
  RWLockWin();
  static bool LoadModule();

  SRWLOCK lock_;
};

}  // namespace webrtc

#endif  // WEBRTC_SYSTEM_WRAPPERS_SOURCE_RW_LOCK_WIN_H_
