/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_SYSTEM_WRAPPERS_SOURCE_CONDITION_VARIABLE_NATIVE_WIN_H_
#define WEBRTC_SYSTEM_WRAPPERS_SOURCE_CONDITION_VARIABLE_NATIVE_WIN_H_

#include <windows.h>

#include "webrtc/system_wrappers/interface/condition_variable_wrapper.h"

namespace webrtc {

#if !defined CONDITION_VARIABLE_INIT
typedef struct RTL_CONDITION_VARIABLE_ {
  void* Ptr;
} RTL_CONDITION_VARIABLE, *PRTL_CONDITION_VARIABLE;

typedef RTL_CONDITION_VARIABLE CONDITION_VARIABLE, *PCONDITION_VARIABLE;
#endif

typedef void (WINAPI* PInitializeConditionVariable)(PCONDITION_VARIABLE);
typedef BOOL (WINAPI* PSleepConditionVariableCS)(PCONDITION_VARIABLE,
                                                 PCRITICAL_SECTION, DWORD);
typedef void (WINAPI* PWakeConditionVariable)(PCONDITION_VARIABLE);
typedef void (WINAPI* PWakeAllConditionVariable)(PCONDITION_VARIABLE);

class ConditionVariableNativeWin : public ConditionVariableWrapper {
 public:
  static ConditionVariableWrapper* Create();
  virtual ~ConditionVariableNativeWin();

  void SleepCS(CriticalSectionWrapper& crit_sect);
  bool SleepCS(CriticalSectionWrapper& crit_sect, unsigned long max_time_inMS);
  void Wake();
  void WakeAll();

 private:
  ConditionVariableNativeWin();

  bool Init();

  CONDITION_VARIABLE condition_variable_;
};

}  // namespace webrtc

#endif  // WEBRTC_SYSTEM_WRAPPERS_SOURCE_CONDITION_VARIABLE_NATIVE_WIN_H_
