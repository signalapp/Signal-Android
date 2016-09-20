/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_TASKRUNNER_H__
#define WEBRTC_BASE_TASKRUNNER_H__

#include <vector>

#include "webrtc/base/basictypes.h"
#include "webrtc/base/sigslot.h"
#include "webrtc/base/taskparent.h"

namespace rtc {
class Task;

const int64_t kSecToMsec = 1000;
const int64_t kMsecTo100ns = 10000;
const int64_t kSecTo100ns = kSecToMsec * kMsecTo100ns;

class TaskRunner : public TaskParent, public sigslot::has_slots<> {
 public:
  TaskRunner();
  ~TaskRunner() override;

  virtual void WakeTasks() = 0;

  // Returns the current time in 100ns units.  It is used for
  // determining timeouts.  The origin is not important, only
  // the units and that rollover while the computer is running.
  //
  // On Windows, GetSystemTimeAsFileTime is the typical implementation.
  virtual int64_t CurrentTime() = 0;

  void StartTask(Task *task);
  void RunTasks();
  void PollTasks();

  void UpdateTaskTimeout(Task* task, int64_t previous_task_timeout_time);

#if !defined(NDEBUG)
  bool is_ok_to_delete(Task* task) {
    return task == deleting_task_;
  }

  void IncrementAbortCount() {
    ++abort_count_;
  }

  void DecrementAbortCount() {
    --abort_count_;
  }
#endif

  // Returns the next absolute time when a task times out
  // OR "0" if there is no next timeout.
  int64_t next_task_timeout() const;

 protected:
  // The primary usage of this method is to know if
  // a callback timer needs to be set-up or adjusted.
  // This method will be called
  //  * when the next_task_timeout() becomes a smaller value OR
  //  * when next_task_timeout() has changed values and the previous
  //    value is in the past.
  //
  // If the next_task_timeout moves to the future, this method will *not*
  // get called (because it subclass should check next_task_timeout()
  // when its timer goes off up to see if it needs to set-up a new timer).
  //
  // Note that this maybe called conservatively.  In that it may be
  // called when no time change has happened.
  virtual void OnTimeoutChange() {
    // by default, do nothing.
  }

 private:
  void InternalRunTasks(bool in_destructor);
  void CheckForTimeoutChange(int64_t previous_timeout_time);

  std::vector<Task *> tasks_;
  Task *next_timeout_task_;
  bool tasks_running_;
#if !defined(NDEBUG)
  int abort_count_;
  Task* deleting_task_;
#endif

  void RecalcNextTimeout(Task *exclude_task);
};

} // namespace rtc

#endif  // TASK_BASE_TASKRUNNER_H__
