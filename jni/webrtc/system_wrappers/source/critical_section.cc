/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#if defined(_WIN32)
#include <windows.h>
#include "webrtc/system_wrappers/source/critical_section_win.h"
#else
#include "webrtc/system_wrappers/source/critical_section_posix.h"
#endif

namespace webrtc {

CriticalSectionWrapper* CriticalSectionWrapper::CreateCriticalSection() {
#ifdef _WIN32
  return new CriticalSectionWindows();
#else
  return new CriticalSectionPosix();
#endif
}

}  // namespace webrtc
