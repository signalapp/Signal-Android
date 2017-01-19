/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_SYSTEM_WRAPPERS_SOURCE_CRITICAL_SECTION_WIN_H_
#define WEBRTC_SYSTEM_WRAPPERS_SOURCE_CRITICAL_SECTION_WIN_H_

#include <windows.h>
#include "webrtc/system_wrappers/interface/critical_section_wrapper.h"
#include "webrtc/typedefs.h"

namespace webrtc {

class CriticalSectionWindows : public CriticalSectionWrapper {
 public:
  CriticalSectionWindows();

  virtual ~CriticalSectionWindows();

  virtual void Enter();
  virtual void Leave();

 private:
  CRITICAL_SECTION crit;

  friend class ConditionVariableEventWin;
  friend class ConditionVariableNativeWin;
};

}  // namespace webrtc

#endif  // WEBRTC_SYSTEM_WRAPPERS_SOURCE_CRITICAL_SECTION_WIN_H_
