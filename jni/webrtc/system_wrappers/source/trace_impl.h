/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_SYSTEM_WRAPPERS_SOURCE_TRACE_IMPL_H_
#define WEBRTC_SYSTEM_WRAPPERS_SOURCE_TRACE_IMPL_H_

#include <memory>

#include "webrtc/base/criticalsection.h"
#include "webrtc/system_wrappers/include/event_wrapper.h"
#include "webrtc/system_wrappers/include/file_wrapper.h"
#include "webrtc/system_wrappers/include/static_instance.h"
#include "webrtc/base/platform_thread.h"
#include "webrtc/system_wrappers/include/trace.h"

namespace webrtc {

#define WEBRTC_TRACE_MAX_MESSAGE_SIZE 1024
// Total buffer size is WEBRTC_TRACE_NUM_ARRAY (number of buffer partitions) *
// WEBRTC_TRACE_MAX_QUEUE (number of lines per buffer partition) *
// WEBRTC_TRACE_MAX_MESSAGE_SIZE (number of 1 byte charachters per line) =
// 1 or 4 Mbyte.

#define WEBRTC_TRACE_MAX_FILE_SIZE 100*1000
// Number of rows that may be written to file. On average 110 bytes per row (max
// 256 bytes per row). So on average 110*100*1000 = 11 Mbyte, max 256*100*1000 =
// 25.6 Mbyte

class TraceImpl : public Trace {
 public:
  virtual ~TraceImpl();

  static TraceImpl* CreateInstance();
  static TraceImpl* GetTrace(const TraceLevel level = kTraceAll);

  int32_t SetTraceFileImpl(const char* file_name, const bool add_file_counter);
  int32_t SetTraceCallbackImpl(TraceCallback* callback);

  void AddImpl(const TraceLevel level, const TraceModule module,
               const int32_t id, const char* msg);

  bool TraceCheck(const TraceLevel level) const;

 protected:
  TraceImpl();

  static TraceImpl* StaticInstance(CountOperation count_operation,
                                   const TraceLevel level = kTraceAll);

  int32_t AddThreadId(char* trace_message) const;

  // OS specific implementations.
  virtual int32_t AddTime(char* trace_message,
                          const TraceLevel level) const = 0;

  virtual int32_t AddDateTimeInfo(char* trace_message) const = 0;

 private:
  friend class Trace;

  int32_t AddLevel(char* sz_message, const TraceLevel level) const;

  int32_t AddModuleAndId(char* trace_message, const TraceModule module,
                         const int32_t id) const;

  int32_t AddMessage(char* trace_message,
                     const char msg[WEBRTC_TRACE_MAX_MESSAGE_SIZE],
                     const uint16_t written_so_far) const;

  void AddMessageToList(
    const char trace_message[WEBRTC_TRACE_MAX_MESSAGE_SIZE],
    const uint16_t length,
    const TraceLevel level);

  bool UpdateFileName(
      char file_name_with_counter_utf8[FileWrapper::kMaxFileNameSize],
      const uint32_t new_count) const EXCLUSIVE_LOCKS_REQUIRED(crit_);

  bool CreateFileName(
    const char file_name_utf8[FileWrapper::kMaxFileNameSize],
    char file_name_with_counter_utf8[FileWrapper::kMaxFileNameSize],
    const uint32_t new_count) const;

  void WriteToFile(const char* msg, uint16_t length)
      EXCLUSIVE_LOCKS_REQUIRED(crit_);

  TraceCallback* callback_ GUARDED_BY(crit_);
  uint32_t row_count_text_ GUARDED_BY(crit_);
  uint32_t file_count_text_ GUARDED_BY(crit_);

  const std::unique_ptr<FileWrapper> trace_file_ GUARDED_BY(crit_);
  std::string trace_file_path_ GUARDED_BY(crit_);
  rtc::CriticalSection crit_;
};

}  // namespace webrtc

#endif  // WEBRTC_SYSTEM_WRAPPERS_SOURCE_TRACE_IMPL_H_
