/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_SYSTEM_WRAPPERS_SOURCE_TRACE_WIN_H_
#define WEBRTC_SYSTEM_WRAPPERS_SOURCE_TRACE_WIN_H_

#include <stdio.h>
#include <windows.h>

#include "webrtc/system_wrappers/source/trace_impl.h"

namespace webrtc {

class TraceWindows : public TraceImpl {
 public:
  TraceWindows();
  virtual ~TraceWindows();

  virtual int32_t AddTime(char* trace_message, const TraceLevel level) const;

  virtual int32_t AddDateTimeInfo(char* trace_message) const;
 private:
  volatile mutable uint32_t prev_api_tick_count_;
  volatile mutable uint32_t prev_tick_count_;
};

}  // namespace webrtc

#endif  // WEBRTC_SYSTEM_WRAPPERS_SOURCE_TRACE_WIN_H_
