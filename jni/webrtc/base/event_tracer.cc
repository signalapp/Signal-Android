/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "webrtc/base/event_tracer.h"

#include <inttypes.h>

#include <vector>

#include "webrtc/base/checks.h"
#include "webrtc/base/criticalsection.h"
#include "webrtc/base/event.h"
#include "webrtc/base/logging.h"
#include "webrtc/base/platform_thread.h"
#include "webrtc/base/timeutils.h"
#include "webrtc/base/trace_event.h"

namespace webrtc {

namespace {

GetCategoryEnabledPtr g_get_category_enabled_ptr = nullptr;
AddTraceEventPtr g_add_trace_event_ptr = nullptr;

}  // namespace

void SetupEventTracer(GetCategoryEnabledPtr get_category_enabled_ptr,
                      AddTraceEventPtr add_trace_event_ptr) {
  g_get_category_enabled_ptr = get_category_enabled_ptr;
  g_add_trace_event_ptr = add_trace_event_ptr;
}

const unsigned char* EventTracer::GetCategoryEnabled(const char* name) {
  if (g_get_category_enabled_ptr)
    return g_get_category_enabled_ptr(name);

  // A string with null terminator means category is disabled.
  return reinterpret_cast<const unsigned char*>("\0");
}

// Arguments to this function (phase, etc.) are as defined in
// webrtc/base/trace_event.h.
void EventTracer::AddTraceEvent(char phase,
                                const unsigned char* category_enabled,
                                const char* name,
                                unsigned long long id,
                                int num_args,
                                const char** arg_names,
                                const unsigned char* arg_types,
                                const unsigned long long* arg_values,
                                unsigned char flags) {
  if (g_add_trace_event_ptr) {
    g_add_trace_event_ptr(phase,
                          category_enabled,
                          name,
                          id,
                          num_args,
                          arg_names,
                          arg_types,
                          arg_values,
                          flags);
  }
}

}  // namespace webrtc

namespace rtc {
namespace tracing {
namespace {

static bool EventTracingThreadFunc(void* params);

// Atomic-int fast path for avoiding logging when disabled.
static volatile int g_event_logging_active = 0;

// TODO(pbos): Log metadata for all threads, etc.
class EventLogger final {
 public:
  EventLogger()
      : logging_thread_(EventTracingThreadFunc, this, "EventTracingThread"),
        shutdown_event_(false, false) {}
  ~EventLogger() { RTC_DCHECK(thread_checker_.CalledOnValidThread()); }

  void AddTraceEvent(const char* name,
                     const unsigned char* category_enabled,
                     char phase,
                     uint64_t timestamp,
                     int pid,
                     rtc::PlatformThreadId thread_id) {
    rtc::CritScope lock(&crit_);
    trace_events_.push_back(
        {name, category_enabled, phase, timestamp, 1, thread_id});
  }

// The TraceEvent format is documented here:
// https://docs.google.com/document/d/1CvAClvFfyA5R-PhYUmn5OOQtYMH4h6I0nSsKchNAySU/preview
  void Log() {
    RTC_DCHECK(output_file_);
    static const int kLoggingIntervalMs = 100;
    fprintf(output_file_, "{ \"traceEvents\": [\n");
    bool has_logged_event = false;
    while (true) {
      bool shutting_down = shutdown_event_.Wait(kLoggingIntervalMs);
      std::vector<TraceEvent> events;
      {
        rtc::CritScope lock(&crit_);
        trace_events_.swap(events);
      }
      for (const TraceEvent& e : events) {
        fprintf(output_file_,
                "%s{ \"name\": \"%s\""
                ", \"cat\": \"%s\""
                ", \"ph\": \"%c\""
                ", \"ts\": %" PRIu64
                ", \"pid\": %d"
#if defined(WEBRTC_WIN)
                ", \"tid\": %lu"
#else
                ", \"tid\": %d"
#endif  // defined(WEBRTC_WIN)
                "}\n",
                has_logged_event ? "," : " ", e.name, e.category_enabled,
                e.phase, e.timestamp, e.pid, e.tid);
        has_logged_event = true;
      }
      if (shutting_down)
        break;
    }
    fprintf(output_file_, "]}\n");
    if (output_file_owned_)
      fclose(output_file_);
    output_file_ = nullptr;
  }

  void Start(FILE* file, bool owned) {
    RTC_DCHECK(thread_checker_.CalledOnValidThread());
    RTC_DCHECK(file);
    RTC_DCHECK(!output_file_);
    output_file_ = file;
    output_file_owned_ = owned;
    {
      rtc::CritScope lock(&crit_);
      // Since the atomic fast-path for adding events to the queue can be
      // bypassed while the logging thread is shutting down there may be some
      // stale events in the queue, hence the vector needs to be cleared to not
      // log events from a previous logging session (which may be days old).
      trace_events_.clear();
    }
    // Enable event logging (fast-path). This should be disabled since starting
    // shouldn't be done twice.
    RTC_CHECK_EQ(0,
                 rtc::AtomicOps::CompareAndSwap(&g_event_logging_active, 0, 1));

    // Finally start, everything should be set up now.
    logging_thread_.Start();
    TRACE_EVENT_INSTANT0("webrtc", "EventLogger::Start");
    logging_thread_.SetPriority(kLowPriority);
  }

  void Stop() {
    RTC_DCHECK(thread_checker_.CalledOnValidThread());
    TRACE_EVENT_INSTANT0("webrtc", "EventLogger::Stop");
    // Try to stop. Abort if we're not currently logging.
    if (rtc::AtomicOps::CompareAndSwap(&g_event_logging_active, 1, 0) == 0)
      return;

    // Wake up logging thread to finish writing.
    shutdown_event_.Set();
    // Join the logging thread.
    logging_thread_.Stop();
  }

 private:
  struct TraceEvent {
    const char* name;
    const unsigned char* category_enabled;
    char phase;
    uint64_t timestamp;
    int pid;
    rtc::PlatformThreadId tid;
  };

  rtc::CriticalSection crit_;
  std::vector<TraceEvent> trace_events_ GUARDED_BY(crit_);
  rtc::PlatformThread logging_thread_;
  rtc::Event shutdown_event_;
  rtc::ThreadChecker thread_checker_;
  FILE* output_file_ = nullptr;
  bool output_file_owned_ = false;
};

static bool EventTracingThreadFunc(void* params) {
  static_cast<EventLogger*>(params)->Log();
  return true;
}

static EventLogger* volatile g_event_logger = nullptr;
static const char* const kDisabledTracePrefix = TRACE_DISABLED_BY_DEFAULT("");
const unsigned char* InternalGetCategoryEnabled(const char* name) {
  const char* prefix_ptr = &kDisabledTracePrefix[0];
  const char* name_ptr = name;
  // Check whether name contains the default-disabled prefix.
  while (*prefix_ptr == *name_ptr && *prefix_ptr != '\0') {
    ++prefix_ptr;
    ++name_ptr;
  }
  return reinterpret_cast<const unsigned char*>(*prefix_ptr == '\0' ? ""
                                                                    : name);
}

void InternalAddTraceEvent(char phase,
                           const unsigned char* category_enabled,
                           const char* name,
                           unsigned long long id,
                           int num_args,
                           const char** arg_names,
                           const unsigned char* arg_types,
                           const unsigned long long* arg_values,
                           unsigned char flags) {
  // Fast path for when event tracing is inactive.
  if (rtc::AtomicOps::AcquireLoad(&g_event_logging_active) == 0)
    return;

  g_event_logger->AddTraceEvent(name, category_enabled, phase,
                                rtc::TimeMicros(), 1, rtc::CurrentThreadId());
}

}  // namespace

void SetupInternalTracer() {
  RTC_CHECK(rtc::AtomicOps::CompareAndSwapPtr(
                &g_event_logger, static_cast<EventLogger*>(nullptr),
                new EventLogger()) == nullptr);
  g_event_logger = new EventLogger();
  webrtc::SetupEventTracer(InternalGetCategoryEnabled, InternalAddTraceEvent);
}

void StartInternalCaptureToFile(FILE* file) {
  g_event_logger->Start(file, false);
}

bool StartInternalCapture(const char* filename) {
  FILE* file = fopen(filename, "w");
  if (!file) {
    LOG(LS_ERROR) << "Failed to open trace file '" << filename
                  << "' for writing.";
    return false;
  }
  g_event_logger->Start(file, true);
  return true;
}

void StopInternalCapture() {
  g_event_logger->Stop();
}

void ShutdownInternalTracer() {
  StopInternalCapture();
  EventLogger* old_logger = rtc::AtomicOps::AcquireLoadPtr(&g_event_logger);
  RTC_DCHECK(old_logger);
  RTC_CHECK(rtc::AtomicOps::CompareAndSwapPtr(
                &g_event_logger, old_logger,
                static_cast<EventLogger*>(nullptr)) == old_logger);
  delete old_logger;
  webrtc::SetupEventTracer(nullptr, nullptr);
}

}  // namespace tracing
}  // namespace rtc
