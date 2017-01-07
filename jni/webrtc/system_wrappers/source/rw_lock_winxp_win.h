/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_SYSTEM_WRAPPERS_SOURCE_RW_LOCK_WINXP_WIN_H_
#define WEBRTC_SYSTEM_WRAPPERS_SOURCE_RW_LOCK_WINXP_WIN_H_

#include "webrtc/system_wrappers/include/rw_lock_wrapper.h"
#include "webrtc/system_wrappers/source/condition_variable_event_win.h"
#include "webrtc/typedefs.h"

namespace webrtc {

class RWLockWinXP : public RWLockWrapper {
 public:
  RWLockWinXP();
  ~RWLockWinXP() override;

  void AcquireLockExclusive() override;
  void ReleaseLockExclusive() override;

  void AcquireLockShared() override;
  void ReleaseLockShared() override;

 private:
  CRITICAL_SECTION critical_section_;
  ConditionVariableEventWin read_condition_;
  ConditionVariableEventWin write_condition_;

  int readers_active_ = 0;
  bool writer_active_ = false;
  int readers_waiting_ = 0;
  int writers_waiting_ = 0;
};

}  // namespace webrtc

#endif  // WEBRTC_SYSTEM_WRAPPERS_SOURCE_RW_LOCK_WINXP_WIN_H_
