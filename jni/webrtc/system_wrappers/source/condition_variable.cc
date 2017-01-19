/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/system_wrappers/interface/condition_variable_wrapper.h"

#if defined(_WIN32)
#include <windows.h>
#include "webrtc/system_wrappers/source/condition_variable_event_win.h"
#include "webrtc/system_wrappers/source/condition_variable_native_win.h"
#elif defined(WEBRTC_LINUX) || defined(WEBRTC_MAC)
#include <pthread.h>
#include "webrtc/system_wrappers/source/condition_variable_posix.h"
#endif

namespace webrtc {

ConditionVariableWrapper* ConditionVariableWrapper::CreateConditionVariable() {
#if defined(_WIN32)
  // Try to create native condition variable implementation.
  ConditionVariableWrapper* ret_val = ConditionVariableNativeWin::Create();
  if (!ret_val) {
    // Native condition variable implementation does not exist. Create generic
    // condition variable based on events.
    ret_val = new ConditionVariableEventWin();
  }
  return ret_val;
#elif defined(WEBRTC_LINUX) || defined(WEBRTC_MAC)
  return ConditionVariablePosix::Create();
#else
  return NULL;
#endif
}

}  // namespace webrtc
