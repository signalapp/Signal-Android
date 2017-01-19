/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 *
 *  System independent wrapper for logging runtime information to file.
 *  Note: All log messages will be written to the same trace file.
 *  Note: If too many messages are written to file there will be a build up of
 *  messages. Apply filtering to avoid that.
 */

#ifndef WEBRTC_SYSTEM_WRAPPERS_INTERFACE_TRACE_H_
#define WEBRTC_SYSTEM_WRAPPERS_INTERFACE_TRACE_H_

#include "webrtc/common_types.h"
#include "webrtc/typedefs.h"

namespace webrtc {

#if defined(WEBRTC_RESTRICT_LOGGING)
// Disable all TRACE macros. The LOG macro is still functional.
#define WEBRTC_TRACE true ? (void) 0 : Trace::Add
#else
#define WEBRTC_TRACE Trace::Add
#endif

class Trace {
 public:
  // The length of the trace text preceeding the log message.
  static const int kBoilerplateLength;
  // The position of the timestamp text within a trace.
  static const int kTimestampPosition;
  // The length of the timestamp (without "delta" field).
  static const int kTimestampLength;

  // Increments the reference count to the trace.
  static void CreateTrace();
  // Decrements the reference count to the trace.
  static void ReturnTrace();
  // Note: any instance that writes to the trace file should increment and
  // decrement the reference count on construction and destruction,
  // respectively.

  // Specifies what type of messages should be written to the trace file. The
  // filter parameter is a bitmask where each message type is enumerated by the
  // TraceLevel enumerator. TODO(hellner): why is the TraceLevel enumerator not
  // defined in this file?
  static void set_level_filter(uint32_t filter) { level_filter_ = filter; }

  // Returns what type of messages are written to the trace file.
  static uint32_t level_filter() { return level_filter_; }

  // Sets the file name. If add_file_counter is false the same file will be
  // reused when it fills up. If it's true a new file with incremented name
  // will be used.
  static int32_t SetTraceFile(const char* file_name,
                              const bool add_file_counter = false);

  // Returns the name of the file that the trace is currently writing to.
  static int32_t TraceFile(char file_name[1024]);

  // Registers callback to receive trace messages.
  // TODO(hellner): Why not use OutStream instead? Why is TraceCallback not
  // defined in this file?
  static int32_t SetTraceCallback(TraceCallback* callback);

  // Adds a trace message for writing to file. The message is put in a queue
  // for writing to file whenever possible for performance reasons. I.e. there
  // is a crash it is possible that the last, vital logs are not logged yet.
  // level is the type of message to log. If that type of messages is
  // filtered it will not be written to file. module is an identifier for what
  // part of the code the message is coming.
  // id is an identifier that should be unique for that set of classes that
  // are associated (e.g. all instances owned by an engine).
  // msg and the ellipsis are the same as e.g. sprintf.
  // TODO(hellner) Why is TraceModule not defined in this file?
  static void Add(const TraceLevel level,
                  const TraceModule module,
                  const int32_t id,
                  const char* msg, ...);

 private:
  static uint32_t level_filter_;
};

}  // namespace webrtc

#endif  // WEBRTC_SYSTEM_WRAPPERS_INTERFACE_TRACE_H_
