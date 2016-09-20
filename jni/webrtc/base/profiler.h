/*
 *  Copyright 2013 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// A simple wall-clock profiler for instrumented code.
// Example:
//   void MyLongFunction() {
//     PROFILE_F();  // Time the execution of this function.
//     // Do something
//     {  // Time just what is in this scope.
//       PROFILE("My event");
//       // Do something else
//     }
//   }
// Another example:
//   void StartAsyncProcess() {
//     PROFILE_START("My async event");
//     DoSomethingAsyncAndThenCall(&Callback);
//   }
//   void Callback() {
//     PROFILE_STOP("My async event");
//     // Handle callback.
//   }

#ifndef WEBRTC_BASE_PROFILER_H_
#define WEBRTC_BASE_PROFILER_H_

#include <map>
#include <string>

#include "webrtc/base/basictypes.h"
#include "webrtc/base/common.h"
#include "webrtc/base/constructormagic.h"
#include "webrtc/base/logging.h"
#include "webrtc/base/sharedexclusivelock.h"

// Profiling could be switched via a build flag, but for now, it's always on.
#ifndef ENABLE_PROFILING
#define ENABLE_PROFILING
#endif

#ifdef ENABLE_PROFILING

#define UV_HELPER2(x) _uv_ ## x
#define UV_HELPER(x) UV_HELPER2(x)
#define UNIQUE_VAR UV_HELPER(__LINE__)

// Profiles the current scope.
#define PROFILE(msg) rtc::ProfilerScope UNIQUE_VAR(msg)
// When placed at the start of a function, profiles the current function.
#define PROFILE_F() PROFILE(__FUNCTION__)
// Reports current timings to the log at severity |sev|.
#define PROFILE_DUMP_ALL(sev) \
  rtc::Profiler::Instance()->ReportAllToLog(__FILE__, __LINE__, sev)
// Reports current timings for all events whose names are prefixed by |prefix|
// to the log at severity |sev|. Using a unique event name as |prefix| will
// report only that event.
#define PROFILE_DUMP(sev, prefix) \
  rtc::Profiler::Instance()->ReportToLog(__FILE__, __LINE__, sev, prefix)
// Starts and stops a profile event. Useful when an event is not easily
// captured within a scope (eg, an async call with a callback when done).
#define PROFILE_START(msg) rtc::Profiler::Instance()->StartEvent(msg)
#define PROFILE_STOP(msg) rtc::Profiler::Instance()->StopEvent(msg)
// TODO(ryanpetrie): Consider adding PROFILE_DUMP_EVERY(sev, iterations)

#undef UV_HELPER2
#undef UV_HELPER
#undef UNIQUE_VAR

#else  // ENABLE_PROFILING

#define PROFILE(msg) (void)0
#define PROFILE_F() (void)0
#define PROFILE_DUMP_ALL(sev) (void)0
#define PROFILE_DUMP(sev, prefix) (void)0
#define PROFILE_START(msg) (void)0
#define PROFILE_STOP(msg) (void)0

#endif  // ENABLE_PROFILING

namespace rtc {

// Tracks information for one profiler event.
class ProfilerEvent {
 public:
  ProfilerEvent();
  void Start();
  void Stop();
  void Stop(uint64_t stop_time);
  double standard_deviation() const;
  double total_time() const { return total_time_; }
  double mean() const { return mean_; }
  double minimum() const { return minimum_; }
  double maximum() const { return maximum_; }
  int event_count() const { return event_count_; }
  bool is_started() const { return start_count_ > 0; }

 private:
  uint64_t current_start_time_;
  double total_time_;
  double mean_;
  double sum_of_squared_differences_;
  double minimum_;
  double maximum_;
  int start_count_;
  int event_count_;
};

// Singleton that owns ProfilerEvents and reports results. Prefer to use
// macros, defined above, rather than directly calling Profiler methods.
class Profiler {
 public:
  ~Profiler();
  void StartEvent(const std::string& event_name);
  void StopEvent(const std::string& event_name);
  void ReportToLog(const char* file, int line, LoggingSeverity severity_to_use,
                   const std::string& event_prefix);
  void ReportAllToLog(const char* file, int line,
                      LoggingSeverity severity_to_use);
  const ProfilerEvent* GetEvent(const std::string& event_name) const;
  // Clears all _stopped_ events. Returns true if _all_ events were cleared.
  bool Clear();

  static Profiler* Instance();
 private:
  Profiler();

  typedef std::map<std::string, ProfilerEvent> EventMap;
  EventMap events_;
  mutable SharedExclusiveLock lock_;

  RTC_DISALLOW_COPY_AND_ASSIGN(Profiler);
};

// Starts an event on construction and stops it on destruction.
// Used by PROFILE macro.
class ProfilerScope {
 public:
  explicit ProfilerScope(const std::string& event_name)
      : event_name_(event_name) {
    Profiler::Instance()->StartEvent(event_name_);
  }
  ~ProfilerScope() {
    Profiler::Instance()->StopEvent(event_name_);
  }
 private:
  std::string event_name_;

  RTC_DISALLOW_COPY_AND_ASSIGN(ProfilerScope);
};

std::ostream& operator<<(std::ostream& stream,
                         const ProfilerEvent& profiler_event);

}  // namespace rtc

#endif  // WEBRTC_BASE_PROFILER_H_
