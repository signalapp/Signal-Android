/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#if defined(WEBRTC_POSIX)
#include <sys/time.h>
#endif  // WEBRTC_POSIX

// TODO: Remove this once the cause of sporadic failures in these
// tests is tracked down.
#include <iostream>

#if defined(WEBRTC_WIN)
#include "webrtc/base/win32.h"
#endif  // WEBRTC_WIN

#include "webrtc/base/arraysize.h"
#include "webrtc/base/common.h"
#include "webrtc/base/constructormagic.h"
#include "webrtc/base/gunit.h"
#include "webrtc/base/logging.h"
#include "webrtc/base/task.h"
#include "webrtc/base/taskrunner.h"
#include "webrtc/base/thread.h"
#include "webrtc/base/timeutils.h"

namespace rtc {

static int64_t GetCurrentTime() {
  return TimeMillis() * 10000;
}

// feel free to change these numbers.  Note that '0' won't work, though
#define STUCK_TASK_COUNT 5
#define HAPPY_TASK_COUNT 20

// this is a generic timeout task which, when it signals timeout, will
// include the unique ID of the task in the signal (we don't use this
// in production code because we haven't yet had occasion to generate
// an array of the same types of task)

class IdTimeoutTask : public Task, public sigslot::has_slots<> {
 public:
  explicit IdTimeoutTask(TaskParent *parent) : Task(parent) {
    SignalTimeout.connect(this, &IdTimeoutTask::OnLocalTimeout);
  }

  sigslot::signal1<const int> SignalTimeoutId;
  sigslot::signal1<const int> SignalDoneId;

  virtual int ProcessStart() {
    return STATE_RESPONSE;
  }

  void OnLocalTimeout() {
    SignalTimeoutId(unique_id());
  }

 protected:
  virtual void Stop() {
    SignalDoneId(unique_id());
    Task::Stop();
  }
};

class StuckTask : public IdTimeoutTask {
 public:
  explicit StuckTask(TaskParent *parent) : IdTimeoutTask(parent) {}
  virtual int ProcessStart() {
    return STATE_BLOCKED;
  }
};

class HappyTask : public IdTimeoutTask {
 public:
  explicit HappyTask(TaskParent *parent) : IdTimeoutTask(parent) {
    time_to_perform_ = rand() % (STUCK_TASK_COUNT / 2);
  }
  virtual int ProcessStart() {
    if (ElapsedTime() > (time_to_perform_ * 1000 * 10000))
      return STATE_RESPONSE;
    else
      return STATE_BLOCKED;
  }

 private:
  int time_to_perform_;
};

// simple implementation of a task runner which uses Windows'
// GetSystemTimeAsFileTime() to get the current clock ticks

class MyTaskRunner : public TaskRunner {
 public:
  virtual void WakeTasks() { RunTasks(); }
  virtual int64_t CurrentTime() { return GetCurrentTime(); }

  bool timeout_change() const {
    return timeout_change_;
  }

  void clear_timeout_change() {
    timeout_change_ = false;
  }
 protected:
  virtual void OnTimeoutChange() {
    timeout_change_ = true;
  }
  bool timeout_change_;
};

//
// this unit test is primarily concerned (for now) with the timeout
// functionality in tasks.  It works as follows:
//
//   * Create a bunch of tasks, some "stuck" (ie., guaranteed to timeout)
//     and some "happy" (will immediately finish).
//   * Set the timeout on the "stuck" tasks to some number of seconds between
//     1 and the number of stuck tasks
//   * Start all the stuck & happy tasks in random order
//   * Wait "number of stuck tasks" seconds and make sure everything timed out

class TaskTest : public sigslot::has_slots<> {
 public:
  TaskTest() {}

  // no need to delete any tasks; the task runner owns them
  ~TaskTest() {}

  void Start() {
    // create and configure tasks
    for (int i = 0; i < STUCK_TASK_COUNT; ++i) {
      stuck_[i].task_ = new StuckTask(&task_runner_);
      stuck_[i].task_->SignalTimeoutId.connect(this,
                                               &TaskTest::OnTimeoutStuck);
      stuck_[i].timed_out_ = false;
      stuck_[i].xlat_ = stuck_[i].task_->unique_id();
      stuck_[i].task_->set_timeout_seconds(i + 1);
      LOG(LS_INFO) << "Task " << stuck_[i].xlat_ << " created with timeout "
                   << stuck_[i].task_->timeout_seconds();
    }

    for (int i = 0; i < HAPPY_TASK_COUNT; ++i) {
      happy_[i].task_ = new HappyTask(&task_runner_);
      happy_[i].task_->SignalTimeoutId.connect(this,
                                               &TaskTest::OnTimeoutHappy);
      happy_[i].task_->SignalDoneId.connect(this,
                                            &TaskTest::OnDoneHappy);
      happy_[i].timed_out_ = false;
      happy_[i].xlat_ = happy_[i].task_->unique_id();
    }

    // start all the tasks in random order
    int stuck_index = 0;
    int happy_index = 0;
    for (int i = 0; i < STUCK_TASK_COUNT + HAPPY_TASK_COUNT; ++i) {
      if ((stuck_index < STUCK_TASK_COUNT) &&
          (happy_index < HAPPY_TASK_COUNT)) {
        if (rand() % 2 == 1) {
          stuck_[stuck_index++].task_->Start();
        } else {
          happy_[happy_index++].task_->Start();
        }
      } else if (stuck_index < STUCK_TASK_COUNT) {
        stuck_[stuck_index++].task_->Start();
      } else {
        happy_[happy_index++].task_->Start();
      }
    }

    for (int i = 0; i < STUCK_TASK_COUNT; ++i) {
      std::cout << "Stuck task #" << i << " timeout is " <<
          stuck_[i].task_->timeout_seconds() << " at " <<
          stuck_[i].task_->timeout_time() << std::endl;
    }

    // just a little self-check to make sure we started all the tasks
    ASSERT_EQ(STUCK_TASK_COUNT, stuck_index);
    ASSERT_EQ(HAPPY_TASK_COUNT, happy_index);

    // run the unblocked tasks
    LOG(LS_INFO) << "Running tasks";
    task_runner_.RunTasks();

    std::cout << "Start time is " << GetCurrentTime() << std::endl;

    // give all the stuck tasks time to timeout
    for (int i = 0; !task_runner_.AllChildrenDone() && i < STUCK_TASK_COUNT;
         ++i) {
      Thread::Current()->ProcessMessages(1000);
      for (int j = 0; j < HAPPY_TASK_COUNT; ++j) {
        if (happy_[j].task_) {
          happy_[j].task_->Wake();
        }
      }
      LOG(LS_INFO) << "Polling tasks";
      task_runner_.PollTasks();
    }

    // We see occasional test failures here due to the stuck tasks not having
    // timed-out yet, which seems like it should be impossible. To help track
    // this down we have added logging of the timing information, which we send
    // directly to stdout so that we get it in opt builds too.
    std::cout << "End time is " << GetCurrentTime() << std::endl;
  }

  void OnTimeoutStuck(const int id) {
    LOG(LS_INFO) << "Timed out task " << id;

    int i;
    for (i = 0; i < STUCK_TASK_COUNT; ++i) {
      if (stuck_[i].xlat_ == id) {
        stuck_[i].timed_out_ = true;
        stuck_[i].task_ = NULL;
        break;
      }
    }

    // getting a bad ID here is a failure, but let's continue
    // running to see what else might go wrong
    EXPECT_LT(i, STUCK_TASK_COUNT);
  }

  void OnTimeoutHappy(const int id) {
    int i;
    for (i = 0; i < HAPPY_TASK_COUNT; ++i) {
      if (happy_[i].xlat_ == id) {
        happy_[i].timed_out_ = true;
        happy_[i].task_ = NULL;
        break;
      }
    }

    // getting a bad ID here is a failure, but let's continue
    // running to see what else might go wrong
    EXPECT_LT(i, HAPPY_TASK_COUNT);
  }

  void OnDoneHappy(const int id) {
    int i;
    for (i = 0; i < HAPPY_TASK_COUNT; ++i) {
      if (happy_[i].xlat_ == id) {
        happy_[i].task_ = NULL;
        break;
      }
    }

    // getting a bad ID here is a failure, but let's continue
    // running to see what else might go wrong
    EXPECT_LT(i, HAPPY_TASK_COUNT);
  }

  void check_passed() {
    EXPECT_TRUE(task_runner_.AllChildrenDone());

    // make sure none of our happy tasks timed out
    for (int i = 0; i < HAPPY_TASK_COUNT; ++i) {
      EXPECT_FALSE(happy_[i].timed_out_);
    }

    // make sure all of our stuck tasks timed out
    for (int i = 0; i < STUCK_TASK_COUNT; ++i) {
      EXPECT_TRUE(stuck_[i].timed_out_);
      if (!stuck_[i].timed_out_) {
        std::cout << "Stuck task #" << i << " timeout is at "
                  << stuck_[i].task_->timeout_time() << std::endl;
      }
    }

    std::cout.flush();
  }

 private:
  struct TaskInfo {
    IdTimeoutTask *task_;
    bool timed_out_;
    int xlat_;
  };

  MyTaskRunner task_runner_;
  TaskInfo stuck_[STUCK_TASK_COUNT];
  TaskInfo happy_[HAPPY_TASK_COUNT];
};

TEST(start_task_test, Timeout) {
  TaskTest task_test;
  task_test.Start();
  task_test.check_passed();
}

// Test for aborting the task while it is running

class AbortTask : public Task {
 public:
  explicit AbortTask(TaskParent *parent) : Task(parent) {
    set_timeout_seconds(1);
  }

  virtual int ProcessStart() {
    Abort();
    return STATE_NEXT;
  }
 private:
  RTC_DISALLOW_COPY_AND_ASSIGN(AbortTask);
};

class TaskAbortTest : public sigslot::has_slots<> {
 public:
  TaskAbortTest() {}

  // no need to delete any tasks; the task runner owns them
  ~TaskAbortTest() {}

  void Start() {
    Task *abort_task = new AbortTask(&task_runner_);
    abort_task->SignalTimeout.connect(this, &TaskAbortTest::OnTimeout);
    abort_task->Start();

    // run the task
    task_runner_.RunTasks();
  }

 private:
  void OnTimeout() {
    FAIL() << "Task timed out instead of aborting.";
  }

  MyTaskRunner task_runner_;
  RTC_DISALLOW_COPY_AND_ASSIGN(TaskAbortTest);
};

TEST(start_task_test, Abort) {
  TaskAbortTest abort_test;
  abort_test.Start();
}

// Test for aborting a task to verify that it does the Wake operation
// which gets it deleted.

class SetBoolOnDeleteTask : public Task {
 public:
  SetBoolOnDeleteTask(TaskParent *parent, bool *set_when_deleted)
    : Task(parent),
      set_when_deleted_(set_when_deleted) {
    EXPECT_TRUE(NULL != set_when_deleted);
    EXPECT_FALSE(*set_when_deleted);
  }

  virtual ~SetBoolOnDeleteTask() {
    *set_when_deleted_ = true;
  }

  virtual int ProcessStart() {
    return STATE_BLOCKED;
  }

 private:
  bool* set_when_deleted_;
  RTC_DISALLOW_COPY_AND_ASSIGN(SetBoolOnDeleteTask);
};

class AbortShouldWakeTest : public sigslot::has_slots<> {
 public:
  AbortShouldWakeTest() {}

  // no need to delete any tasks; the task runner owns them
  ~AbortShouldWakeTest() {}

  void Start() {
    bool task_deleted = false;
    Task *task_to_abort = new SetBoolOnDeleteTask(&task_runner_, &task_deleted);
    task_to_abort->Start();

    // Task::Abort() should call TaskRunner::WakeTasks(). WakeTasks calls
    // TaskRunner::RunTasks() immediately which should delete the task.
    task_to_abort->Abort();
    EXPECT_TRUE(task_deleted);

    if (!task_deleted) {
      // avoid a crash (due to referencing a local variable)
      // if the test fails.
      task_runner_.RunTasks();
    }
  }

 private:
  void OnTimeout() {
    FAIL() << "Task timed out instead of aborting.";
  }

  MyTaskRunner task_runner_;
  RTC_DISALLOW_COPY_AND_ASSIGN(AbortShouldWakeTest);
};

TEST(start_task_test, AbortShouldWake) {
  AbortShouldWakeTest abort_should_wake_test;
  abort_should_wake_test.Start();
}

// Validate that TaskRunner's OnTimeoutChange gets called appropriately
//  * When a task calls UpdateTaskTimeout
//  * When the next timeout task time, times out
class TimeoutChangeTest : public sigslot::has_slots<> {
 public:
  TimeoutChangeTest()
    : task_count_(arraysize(stuck_tasks_)) {}

  // no need to delete any tasks; the task runner owns them
  ~TimeoutChangeTest() {}

  void Start() {
    for (int i = 0; i < task_count_; ++i) {
      stuck_tasks_[i] = new StuckTask(&task_runner_);
      stuck_tasks_[i]->set_timeout_seconds(i + 2);
      stuck_tasks_[i]->SignalTimeoutId.connect(this,
                                               &TimeoutChangeTest::OnTimeoutId);
    }

    for (int i = task_count_ - 1; i >= 0; --i) {
      stuck_tasks_[i]->Start();
    }
    task_runner_.clear_timeout_change();

    // At this point, our timeouts are set as follows
    // task[0] is 2 seconds, task[1] at 3 seconds, etc.

    stuck_tasks_[0]->set_timeout_seconds(2);
    // Now, task[0] is 2 seconds, task[1] at 3 seconds...
    // so timeout change shouldn't be called.
    EXPECT_FALSE(task_runner_.timeout_change());
    task_runner_.clear_timeout_change();

    stuck_tasks_[0]->set_timeout_seconds(1);
    // task[0] is 1 seconds, task[1] at 3 seconds...
    // The smallest timeout got smaller so timeout change be called.
    EXPECT_TRUE(task_runner_.timeout_change());
    task_runner_.clear_timeout_change();

    stuck_tasks_[1]->set_timeout_seconds(2);
    // task[0] is 1 seconds, task[1] at 2 seconds...
    // The smallest timeout is still 1 second so no timeout change.
    EXPECT_FALSE(task_runner_.timeout_change());
    task_runner_.clear_timeout_change();

    while (task_count_ > 0) {
      int previous_count = task_count_;
      task_runner_.PollTasks();
      if (previous_count != task_count_) {
        // We only get here when a task times out.  When that
        // happens, the timeout change should get called because
        // the smallest timeout is now in the past.
        EXPECT_TRUE(task_runner_.timeout_change());
        task_runner_.clear_timeout_change();
      }
      Thread::Current()->socketserver()->Wait(500, false);
    }
  }

 private:
  void OnTimeoutId(const int id) {
    for (size_t i = 0; i < arraysize(stuck_tasks_); ++i) {
      if (stuck_tasks_[i] && stuck_tasks_[i]->unique_id() == id) {
        task_count_--;
        stuck_tasks_[i] = NULL;
        break;
      }
    }
  }

  MyTaskRunner task_runner_;
  StuckTask* (stuck_tasks_[3]);
  int task_count_;
  RTC_DISALLOW_COPY_AND_ASSIGN(TimeoutChangeTest);
};

TEST(start_task_test, TimeoutChange) {
  TimeoutChangeTest timeout_change_test;
  timeout_change_test.Start();
}

class DeleteTestTaskRunner : public TaskRunner {
 public:
  DeleteTestTaskRunner() {
  }
  virtual void WakeTasks() { }
  virtual int64_t CurrentTime() { return GetCurrentTime(); }
 private:
  RTC_DISALLOW_COPY_AND_ASSIGN(DeleteTestTaskRunner);
};

TEST(unstarted_task_test, DeleteTask) {
  // This test ensures that we don't
  // crash if a task is deleted without running it.
  DeleteTestTaskRunner task_runner;
  HappyTask* happy_task = new HappyTask(&task_runner);
  happy_task->Start();

  // try deleting the task directly
  HappyTask* child_happy_task = new HappyTask(happy_task);
  delete child_happy_task;

  // run the unblocked tasks
  task_runner.RunTasks();
}

TEST(unstarted_task_test, DoNotDeleteTask1) {
  // This test ensures that we don't
  // crash if a task runner is deleted without
  // running a certain task.
  DeleteTestTaskRunner task_runner;
  HappyTask* happy_task = new HappyTask(&task_runner);
  happy_task->Start();

  HappyTask* child_happy_task = new HappyTask(happy_task);
  child_happy_task->Start();

  // Never run the tasks
}

TEST(unstarted_task_test, DoNotDeleteTask2) {
  // This test ensures that we don't
  // crash if a taskrunner is delete with a
  // task that has never been started.
  DeleteTestTaskRunner task_runner;
  HappyTask* happy_task = new HappyTask(&task_runner);
  happy_task->Start();

  // Do not start the task.
  // Note: this leaks memory, so don't do this.
  // Instead, always run your tasks or delete them.
  new HappyTask(happy_task);

  // run the unblocked tasks
  task_runner.RunTasks();
}

}  // namespace rtc
