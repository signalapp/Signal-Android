/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <signal.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>

#if WEBRTC_WIN
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#endif  // WEBRTC_WIN 

#if defined(WEBRTC_MAC) && !defined(WEBRTC_IOS)
#include <CoreServices/CoreServices.h>
#endif  // WEBRTC_MAC && !defined(WEBRTC_IOS)

#include <algorithm>
#include "webrtc/base/common.h"
#include "webrtc/base/logging.h"

//////////////////////////////////////////////////////////////////////
// Assertions
//////////////////////////////////////////////////////////////////////

namespace rtc {

void Break() {
#if WEBRTC_WIN
  ::DebugBreak();
#else  // !WEBRTC_WIN 
  // On POSIX systems, SIGTRAP signals debuggers to break without killing the
  // process. If a debugger isn't attached, the uncaught SIGTRAP will crash the
  // app.
  raise(SIGTRAP);
#endif
  // If a debugger wasn't attached, we will have crashed by this point. If a
  // debugger is attached, we'll continue from here.
}

static AssertLogger custom_assert_logger_ = NULL;

void SetCustomAssertLogger(AssertLogger logger) {
  custom_assert_logger_ = logger;
}

void LogAssert(const char* function, const char* file, int line,
               const char* expression) {
  if (custom_assert_logger_) {
    custom_assert_logger_(function, file, line, expression);
  } else {
    LOG(LS_ERROR) << file << "(" << line << ")" << ": ASSERT FAILED: "
                  << expression << " @ " << function;
  }
}

bool IsOdd(int n) {
  return (n & 0x1);
}

bool IsEven(int n) {
  return !IsOdd(n);
}

} // namespace rtc
