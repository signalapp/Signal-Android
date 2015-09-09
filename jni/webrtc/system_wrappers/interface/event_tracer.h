/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// This file defines the interface for event tracing in WebRTC.
//
// Event log handlers are set through SetupEventTracer(). User of this API will
// provide two function pointers to handle event tracing calls.
//
// * GetCategoryEnabledPtr
//   Event tracing system calls this function to determine if a particular
//   event category is enabled.
//
// * AddTraceEventPtr
//   Adds a tracing event. It is the user's responsibility to log the data
//   provided.
//
// Parameters for the above two functions are described in trace_event.h.

#ifndef WEBRTC_SYSTEM_WRAPPERS_INTERFACE_EVENT_TRACER_H_
#define WEBRTC_SYSTEM_WRAPPERS_INTERFACE_EVENT_TRACER_H_

#include "webrtc/common_types.h"

namespace webrtc {

typedef const unsigned char* (*GetCategoryEnabledPtr)(const char* name);
typedef void (*AddTraceEventPtr)(char phase,
                                 const unsigned char* category_enabled,
                                 const char* name,
                                 unsigned long long id,
                                 int num_args,
                                 const char** arg_names,
                                 const unsigned char* arg_types,
                                 const unsigned long long* arg_values,
                                 unsigned char flags);

// User of WebRTC can call this method to setup event tracing.
//
// This method must be called before any WebRTC methods. Functions
// provided should be thread-safe.
WEBRTC_DLLEXPORT void SetupEventTracer(
    GetCategoryEnabledPtr get_category_enabled_ptr,
    AddTraceEventPtr add_trace_event_ptr);

// This class defines interface for the event tracing system to call
// internally. Do not call these methods directly.
class EventTracer {
 public:
  static const unsigned char* GetCategoryEnabled(
      const char* name);

  static void AddTraceEvent(
      char phase,
      const unsigned char* category_enabled,
      const char* name,
      unsigned long long id,
      int num_args,
      const char** arg_names,
      const unsigned char* arg_types,
      const unsigned long long* arg_values,
      unsigned char flags);
};

}  // namespace webrtc

#endif  // WEBRTC_SYSTEM_WRAPPERS_INTERFACE_EVENT_TRACER_H_
