/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/system_wrappers/interface/logcat_trace_context.h"

#include <android/log.h>
#include <assert.h>

#include "webrtc/system_wrappers/interface/logging.h"

namespace webrtc {

static android_LogPriority AndroidLogPriorityFromWebRtcLogLevel(
    TraceLevel webrtc_level) {
  // NOTE: this mapping is somewhat arbitrary.  StateInfo and Info are mapped
  // to DEBUG because they are highly verbose in webrtc code (which is
  // unfortunate).
  switch (webrtc_level) {
    case webrtc::kTraceStateInfo: return ANDROID_LOG_DEBUG;
    case webrtc::kTraceWarning: return ANDROID_LOG_WARN;
    case webrtc::kTraceError: return ANDROID_LOG_ERROR;
    case webrtc::kTraceCritical: return ANDROID_LOG_FATAL;
    case webrtc::kTraceApiCall: return ANDROID_LOG_VERBOSE;
    case webrtc::kTraceModuleCall: return ANDROID_LOG_VERBOSE;
    case webrtc::kTraceMemory: return ANDROID_LOG_VERBOSE;
    case webrtc::kTraceTimer: return ANDROID_LOG_VERBOSE;
    case webrtc::kTraceStream: return ANDROID_LOG_VERBOSE;
    case webrtc::kTraceDebug: return ANDROID_LOG_DEBUG;
    case webrtc::kTraceInfo: return ANDROID_LOG_DEBUG;
    case webrtc::kTraceTerseInfo: return ANDROID_LOG_INFO;
    default:
      LOG(LS_ERROR) << "Unexpected log level" << webrtc_level;
      return ANDROID_LOG_FATAL;
  }
}

LogcatTraceContext::LogcatTraceContext() {
  webrtc::Trace::CreateTrace();
  if (webrtc::Trace::SetTraceCallback(this) != 0)
    assert(false);
}

LogcatTraceContext::~LogcatTraceContext() {
  if (webrtc::Trace::SetTraceCallback(NULL) != 0)
    assert(false);
  webrtc::Trace::ReturnTrace();
}

void LogcatTraceContext::Print(TraceLevel level,
                               const char* message,
                               int length) {
  __android_log_print(AndroidLogPriorityFromWebRtcLogLevel(level),
                      "WEBRTC", "%.*s", length, message);
}

}  // namespace webrtc
