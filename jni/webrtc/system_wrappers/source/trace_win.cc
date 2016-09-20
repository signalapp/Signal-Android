/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/system_wrappers/source/trace_win.h"

#include <assert.h>
#include <stdarg.h>

#include "Mmsystem.h"

namespace webrtc {
TraceWindows::TraceWindows()
    : prev_api_tick_count_(0),
      prev_tick_count_(0) {
}

TraceWindows::~TraceWindows() {
}

int32_t TraceWindows::AddTime(char* trace_message,
                              const TraceLevel level) const {
  uint32_t dw_current_time = timeGetTime();
  SYSTEMTIME system_time;
  GetSystemTime(&system_time);

  if (level == kTraceApiCall) {
    uint32_t dw_delta_time = dw_current_time - prev_tick_count_;
    prev_tick_count_ = dw_current_time;

    if (prev_tick_count_ == 0) {
      dw_delta_time = 0;
    }
    if (dw_delta_time > 0x0fffffff) {
      // Either wrap-around or data race.
      dw_delta_time = 0;
    }
    if (dw_delta_time > 99999) {
      dw_delta_time = 99999;
    }

    sprintf(trace_message, "(%2u:%2u:%2u:%3u |%5u) ", system_time.wHour,
            system_time.wMinute, system_time.wSecond,
            system_time.wMilliseconds, dw_delta_time);
  } else {
    uint32_t dw_delta_time = dw_current_time - prev_api_tick_count_;
    prev_api_tick_count_ = dw_current_time;

    if (prev_api_tick_count_ == 0) {
      dw_delta_time = 0;
    }
    if (dw_delta_time > 0x0fffffff) {
      // Either wraparound or data race.
      dw_delta_time = 0;
    }
    if (dw_delta_time > 99999) {
      dw_delta_time = 99999;
    }
    sprintf(trace_message, "(%2u:%2u:%2u:%3u |%5u) ", system_time.wHour,
            system_time.wMinute, system_time.wSecond,
            system_time.wMilliseconds, dw_delta_time);
  }
  return 22;
}

int32_t TraceWindows::AddDateTimeInfo(char* trace_message) const {
  prev_api_tick_count_ = timeGetTime();
  prev_tick_count_ = prev_api_tick_count_;

  SYSTEMTIME sys_time;
  GetLocalTime(&sys_time);

  TCHAR sz_date_str[20];
  TCHAR sz_time_str[20];

  // Create date string (e.g. Apr 04 2002)
  GetDateFormat(LOCALE_SYSTEM_DEFAULT, 0, &sys_time, TEXT("MMM dd yyyy"),
                sz_date_str, 20);

  // Create time string (e.g. 15:32:08)
  GetTimeFormat(LOCALE_SYSTEM_DEFAULT, 0, &sys_time, TEXT("HH':'mm':'ss"),
                sz_time_str, 20);

  sprintf(trace_message, "Local Date: %ls Local Time: %ls", sz_date_str,
          sz_time_str);

  // Include NULL termination (hence + 1).
  return static_cast<int32_t>(strlen(trace_message) + 1);
}

}  // namespace webrtc
