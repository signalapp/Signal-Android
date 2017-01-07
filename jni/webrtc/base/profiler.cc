/*
 *  Copyright 2013 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/base/profiler.h"

#include <math.h>
#include <algorithm>

#include "webrtc/base/timeutils.h"

namespace {

// When written to an ostream, FormattedTime chooses an appropriate scale and
// suffix for a time value given in seconds.
class FormattedTime {
 public:
  explicit FormattedTime(double t) : time_(t) {}
  double time() const { return time_; }
 private:
  double time_;
};

std::ostream& operator<<(std::ostream& stream, const FormattedTime& time) {
  if (time.time() < 1.0) {
    stream << (time.time() * 1000.0) << "ms";
  } else {
    stream << time.time() << 's';
  }
  return stream;
}

}  // namespace

namespace rtc {

ProfilerEvent::ProfilerEvent()
    : total_time_(0.0),
      mean_(0.0),
      sum_of_squared_differences_(0.0),
      start_count_(0),
      event_count_(0) {
}

void ProfilerEvent::Start() {
  if (start_count_ == 0) {
    current_start_time_ = TimeNanos();
  }
  ++start_count_;
}

void ProfilerEvent::Stop(uint64_t stop_time) {
  --start_count_;
  ASSERT(start_count_ >= 0);
  if (start_count_ == 0) {
    double elapsed = static_cast<double>(stop_time - current_start_time_) /
        kNumNanosecsPerSec;
    total_time_ += elapsed;
    if (event_count_ == 0) {
      minimum_ = maximum_ = elapsed;
    } else {
      minimum_ = std::min(minimum_, elapsed);
      maximum_ = std::max(maximum_, elapsed);
    }
    // Online variance and mean algorithm: http://en.wikipedia.org/wiki/
    // Algorithms_for_calculating_variance#Online_algorithm
    ++event_count_;
    double delta = elapsed - mean_;
    mean_ = mean_ + delta / event_count_;
    sum_of_squared_differences_ += delta * (elapsed - mean_);
  }
}

void ProfilerEvent::Stop() {
  Stop(TimeNanos());
}

double ProfilerEvent::standard_deviation() const {
    if (event_count_ <= 1) return 0.0;
    return sqrt(sum_of_squared_differences_ / (event_count_ - 1.0));
}

Profiler::~Profiler() = default;

Profiler* Profiler::Instance() {
  RTC_DEFINE_STATIC_LOCAL(Profiler, instance, ());
  return &instance;
}

Profiler::Profiler() {
}

void Profiler::StartEvent(const std::string& event_name) {
  lock_.LockShared();
  EventMap::iterator it = events_.find(event_name);
  bool needs_insert = (it == events_.end());
  lock_.UnlockShared();

  if (needs_insert) {
    // Need an exclusive lock to modify the map.
    ExclusiveScope scope(&lock_);
    it = events_.insert(
        EventMap::value_type(event_name, ProfilerEvent())).first;
  }

  it->second.Start();
}

void Profiler::StopEvent(const std::string& event_name) {
  // Get the time ASAP, then wait for the lock.
  uint64_t stop_time = TimeNanos();
  SharedScope scope(&lock_);
  EventMap::iterator it = events_.find(event_name);
  if (it != events_.end()) {
    it->second.Stop(stop_time);
  }
}

void Profiler::ReportToLog(const char* file, int line,
                           LoggingSeverity severity_to_use,
                           const std::string& event_prefix) {
  if (!LogMessage::Loggable(severity_to_use)) {
    return;
  }

  SharedScope scope(&lock_);

  { // Output first line.
    LogMessage msg(file, line, severity_to_use);
    msg.stream() << "=== Profile report ";
    if (event_prefix.empty()) {
      msg.stream() << "(prefix: '" << event_prefix << "') ";
    }
    msg.stream() << "===";
  }
  for (EventMap::const_iterator it = events_.begin();
       it != events_.end(); ++it) {
    if (event_prefix.empty() || it->first.find(event_prefix) == 0) {
      LogMessage(file, line, severity_to_use).stream()
          << it->first << " " << it->second;
    }
  }
  LogMessage(file, line, severity_to_use).stream()
      << "=== End profile report ===";
}

void Profiler::ReportAllToLog(const char* file, int line,
                           LoggingSeverity severity_to_use) {
  ReportToLog(file, line, severity_to_use, "");
}

const ProfilerEvent* Profiler::GetEvent(const std::string& event_name) const {
  SharedScope scope(&lock_);
  EventMap::const_iterator it =
      events_.find(event_name);
  return (it == events_.end()) ? NULL : &it->second;
}

bool Profiler::Clear() {
  ExclusiveScope scope(&lock_);
  bool result = true;
  // Clear all events that aren't started.
  EventMap::iterator it = events_.begin();
  while (it != events_.end()) {
    if (it->second.is_started()) {
      ++it;  // Can't clear started events.
      result = false;
    } else {
      events_.erase(it++);
    }
  }
  return result;
}

std::ostream& operator<<(std::ostream& stream,
                         const ProfilerEvent& profiler_event) {
  stream << "count=" << profiler_event.event_count()
         << " total=" << FormattedTime(profiler_event.total_time())
         << " mean=" << FormattedTime(profiler_event.mean())
         << " min=" << FormattedTime(profiler_event.minimum())
         << " max=" << FormattedTime(profiler_event.maximum())
         << " sd=" << profiler_event.standard_deviation();
  return stream;
}

}  // namespace rtc
