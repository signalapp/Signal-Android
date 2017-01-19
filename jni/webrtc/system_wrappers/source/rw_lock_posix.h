/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_SYSTEM_WRAPPERS_SOURCE_RW_LOCK_POSIX_H_
#define WEBRTC_SYSTEM_WRAPPERS_SOURCE_RW_LOCK_POSIX_H_

#include "webrtc/system_wrappers/interface/rw_lock_wrapper.h"
#include "webrtc/typedefs.h"

#include <pthread.h>

namespace webrtc {

class RWLockPosix : public RWLockWrapper {
 public:
  static RWLockPosix* Create();
  virtual ~RWLockPosix();

  virtual void AcquireLockExclusive() OVERRIDE;
  virtual void ReleaseLockExclusive() OVERRIDE;

  virtual void AcquireLockShared() OVERRIDE;
  virtual void ReleaseLockShared() OVERRIDE;

 private:
  RWLockPosix();
  bool Init();

  pthread_rwlock_t lock_;
};

}  // namespace webrtc

#endif  // WEBRTC_SYSTEM_WRAPPERS_SOURCE_RW_LOCK_POSIX_H_
