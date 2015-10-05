/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/system_wrappers/source/critical_section_win.h"

namespace webrtc {

CriticalSectionWindows::CriticalSectionWindows() {
  InitializeCriticalSection(&crit);
}

CriticalSectionWindows::~CriticalSectionWindows() {
  DeleteCriticalSection(&crit);
}

void
CriticalSectionWindows::Enter() {
  EnterCriticalSection(&crit);
}

void
CriticalSectionWindows::Leave() {
  LeaveCriticalSection(&crit);
}

}  // namespace webrtc
